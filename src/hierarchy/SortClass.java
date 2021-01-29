package hierarchy;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassReader;

import org.reflections.*;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

public class SortClass {
	private String pathToFile;
	
	public SortClass(String path) {
		this.pathToFile = path;
	}
	
	// assumption that only user classes will actively digest client input
	// for war file
	public List<Map<Class<?>, byte[]>> getSerialAndAllClasses() throws IOException, ClassNotFoundException {
		Unpack up = new Unpack();
		if (pathToFile.endsWith(".war")) {
			ClassLoader classpaths = up.getLibLoader(Paths.get(pathToFile));
			Map<Class<?>, byte[]> allSerialTypes = getSerialLibClasses(classpaths);
			Map<Class<?>, byte[]> allUserClasses = new Hashtable<Class<?>, byte[]>();
			List<Map<Class<?>, byte[]>> combined = new ArrayList<>(); 
			
			String regexStr = "/";
			String replacementStr = "\\.";
			Pattern pattern = Pattern.compile(regexStr);
			Class<?> serial = Class.forName("java.io.Serializable");
			
			up.getClassesPath().forEach(b -> {
				ClassReader cr = new ClassReader(b);
				String inputClazz = cr.getClassName();
				Matcher matcher = pattern.matcher(inputClazz);
				String outputStr = matcher.replaceAll(replacementStr);
				ClassLoader loader = new CustomLoader(b);
				
				try {
					Class<?> userClazz = loader.loadClass(outputStr);
					allUserClasses.put(userClazz, b);
					if (serial.isAssignableFrom(userClazz)) {
						allSerialTypes.put(userClazz, b);
					}
				} catch (ClassNotFoundException e) {
					System.out.println("Please provide all relevant dependencies");
					e.printStackTrace();
				}
			});
			
			up.deleteDirectory();
			combined.add(allSerialTypes);
			combined.add(allUserClasses); 
			return combined;
		} 
		ClassLoader loader = up.getJarClassLoader(Paths.get(pathToFile));
		List<Map<Class<?>, byte[]>> serialAndAllTypes = getJarClasses(loader);
		up.deleteDirectory();
		return serialAndAllTypes;
	}
	
	// for jar 
	private static List<Map<Class<?>, byte[]>> getJarClasses(ClassLoader loader) throws ClassNotFoundException, FileNotFoundException, IOException {
		List<Map<Class<?>, byte[]>> result = new ArrayList<>();
		List<Class<?>> temp = new ArrayList<Class<?>>();
		Map<Class<?>, byte[]> subtypes = new Hashtable<Class<?>, byte[]>();
		Map<Class<?>, byte[]> all = new Hashtable<Class<?>, byte[]>();
		ConfigurationBuilder config = new ConfigurationBuilder();
		Reflections reflections = new Reflections(config.setUrls(ClasspathHelper.forClassLoader(loader)).setScanners(new SubTypesScanner(false)));
		Set<String> allTypes = reflections.getAllTypes();
		allTypes.forEach(t -> {
			try {
				temp.add(Class.forName(t, false, loader));
			} catch (ClassNotFoundException e) {
				System.out.println("Please provide all relevant dependencies");
				e.printStackTrace();
			}
		});
		Class<?> serial = Class.forName("java.io.Serializable");
		for (Class<?> type : temp) {
			String clazzName = type.getName();
			String regexStr = "\\.";
			String replacementStr = "/";
			Pattern pattern = Pattern.compile(regexStr);
			Matcher matcher = pattern.matcher(clazzName);
			String output = matcher.replaceAll(replacementStr);
			Path libDir = Paths.get("/home/pcadmin/Sample");
			Path pathToClass = libDir.resolve(output + ".class");
			
			try (
				FileInputStream fis = new FileInputStream(pathToClass.toFile());
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
			) {
				int byteread;
				byte[] buffer = new byte[4096];
				while ((byteread = fis.read(buffer)) != -1) {
					bos.write(buffer, 0, byteread);
				}
				byte[] libClazzBytes = bos.toByteArray();
				if (serial.isAssignableFrom(type)) {
					subtypes.put(type, libClazzBytes);
				}
				all.put(type, libClazzBytes);
			}
		}
		
		result.add(subtypes);
		result.add(all);
		return result;
	}
	
	//for war
	private static Map<Class<?>, byte[]> getSerialLibClasses(ClassLoader loader) throws ClassNotFoundException, FileNotFoundException, IOException {
		List<Class<?>> temp = new ArrayList<Class<?>>();
		Map<Class<?>, byte[]> subtypes = new Hashtable<Class<?>, byte[]>();
		ConfigurationBuilder config = new ConfigurationBuilder();
		Reflections reflections = new Reflections(config.setUrls(ClasspathHelper.forClassLoader(loader)).setScanners(new SubTypesScanner(false)));
		Set<String> allTypes = reflections.getAllTypes();
		allTypes.forEach(t -> {
			try {
				temp.add(Class.forName(t, false, loader));
			} catch (ClassNotFoundException e) {
				System.out.println("Please provide all relevant dependencies");
				e.printStackTrace();
			}
		});
		Class<?> serial = Class.forName("java.io.Serializable");
		for (Class<?> type : temp) {
			if (serial.isAssignableFrom(type)) {
				String clazzName = type.getName();
				String regexStr = "\\.";
				String replacementStr = "/";
				Pattern pattern = Pattern.compile(regexStr);
				Matcher matcher = pattern.matcher(clazzName);
				String output = matcher.replaceAll(replacementStr);
				Path libDir = Paths.get("/home/pcadmin/Sample/WEB-INF/lib");
				Path pathToClass = libDir.resolve(output + ".class");
				
				try (
					FileInputStream fis = new FileInputStream(pathToClass.toFile());
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
				) {
					int byteread;
					byte[] buffer = new byte[4096];
					while ((byteread = fis.read(buffer)) != -1) {
						bos.write(buffer, 0, byteread);
					}
					byte[] libClazzBytes = bos.toByteArray();
					subtypes.put(type, libClazzBytes);
				}
			}
		}
		return subtypes;
	}
	
	/*
	 * Side function get all root classes of target file
	 * Get all interfaces from jdk lib
	 */
	public static Map<String, Map<Class<?>, byte[]>> computeHierarchy(Map<Class<?>, byte[]> allTypes, Map<Class<?>, byte[]> serialTypes) {
		Iterator<Entry<Class<?>, byte[]>> it = allTypes.entrySet().iterator();
		Map<String, Map<Class<?>, byte[]>> hierarchy = new HashMap<>();
		while (it.hasNext()) {
			Map.Entry<Class<?>, byte[]> e = (Map.Entry<Class<?>, byte[]>) it.next();
			Class<?> parent = e.getKey();
			String className = parent.getCanonicalName();
			Map<Class<?>, byte[]> subtypes = new HashMap<Class<?>, byte[]>();
			serialTypes.forEach((k, v) -> {
				if (parent.isAssignableFrom(k)) {
					subtypes.put(k, v);
				}
			});
			hierarchy.put(className, subtypes);
		}
		return hierarchy;
	}
}
