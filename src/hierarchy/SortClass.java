package hierarchy;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
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
	private CustomLoader genericLoader;
	private String serverDir;
	private String tempDir;
	
	public SortClass(String ... path) {
		this.pathToFile = path[0];
		if (path.length > 1) {
			this.serverDir = path[1];
			if (path.length > 2) {
				this.tempDir = path[2];
			}
		}
	}
	
	// assumption that only user classes will actively digest client input
	// for war file
	public List<Map<Class<?>, byte[]>> getSerialAndAllClasses() throws IOException, ClassNotFoundException {
		boolean canDeleteTempDir = true;
		if (tempDir != null) {
			Unpack.initialize(tempDir);
			canDeleteTempDir = false;
		} else 
			Unpack.initialize();
		
		if (serverDir != null) {
			genericLoader = Unpack.getLibLoader(Paths.get(pathToFile));
			List<Map<Class<?>, byte[]>> combined = getSerialLibClasses();
			Map<Class<?>, byte[]> allSerialTypes = combined.get(0);
			Map<Class<?>, byte[]> allClasses = combined.get(1); 
			
			String regexStr = "/";
			String replacementStr = "\\.";
			Pattern pattern = Pattern.compile(regexStr);
			Class<?> serial = Class.forName("java.io.Serializable");
			
			Object[] URLAndClass = Unpack.getClassesPath();
			genericLoader.addURL((URL) URLAndClass[0]);
			
			@SuppressWarnings("unchecked")
			List<byte[]> clazzes = (List<byte[]>) URLAndClass[1];
			clazzes.forEach(b -> {
				ClassReader cr = new ClassReader(b);
				String inputClazz = cr.getClassName();
				Matcher matcher = pattern.matcher(inputClazz);
				String outputStr = matcher.replaceAll(replacementStr);
				
				try {
					Class<?> userClazz = Class.forName(outputStr, false, genericLoader);
					allClasses.put(userClazz, b);
					if (serial.isAssignableFrom(userClazz)) {
						allSerialTypes.put(userClazz, b);
					}
				} catch (ClassNotFoundException e) {
					System.out.println("Please provide all relevant dependencies");
					e.printStackTrace();
				}
			});
			if (canDeleteTempDir)
				Unpack.deleteDirectory();
			combined.add(allSerialTypes);
			combined.add(allClasses); 
			return combined;
		} 
		genericLoader = Unpack.getJarClassLoader(Paths.get(pathToFile));
		List<Map<Class<?>, byte[]>> serialAndAllTypes = getJarClasses();
		Unpack.deleteDirectory();
		return serialAndAllTypes;
	}
	
	// for jar 
	private List<Map<Class<?>, byte[]>> getJarClasses() throws ClassNotFoundException, FileNotFoundException, IOException {
		List<Map<Class<?>, byte[]>> result = new ArrayList<>();
		List<Class<?>> temp = new ArrayList<Class<?>>();
		Map<Class<?>, byte[]> subtypes = new Hashtable<Class<?>, byte[]>();
		Map<Class<?>, byte[]> all = new Hashtable<Class<?>, byte[]>();
		ConfigurationBuilder config = new ConfigurationBuilder();
		Reflections reflections = new Reflections(config.setUrls(ClasspathHelper.forClassLoader(genericLoader)).setScanners(new SubTypesScanner(false)));
		Set<String> allTypes = reflections.getAllTypes();
		allTypes.forEach(t -> {
			try {
				temp.add(Class.forName(t, false, genericLoader));
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
	private List<Map<Class<?>, byte[]>> getSerialLibClasses() throws ClassNotFoundException, FileNotFoundException, IOException {
		List<Class<?>> temp = new ArrayList<Class<?>>();
		List<Map<Class<?>, byte[]>> result = new ArrayList<>();
		Map<Class<?>, byte[]> subtypes = new Hashtable<Class<?>, byte[]>();
		Map<Class<?>, byte[]> all = new Hashtable<Class<?>, byte[]>();
		ConfigurationBuilder config = new ConfigurationBuilder();
		Reflections reflections = new Reflections(config.setUrls(ClasspathHelper.forClassLoader(genericLoader)).setScanners(new SubTypesScanner(false)));
		Set<String> allTypes = reflections.getAllTypes();
		genericLoader = Unpack.genericResourceLoader(genericLoader, serverDir);

		allTypes.forEach(t -> {
			try {
				temp.add(Class.forName(t, false, genericLoader));
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
			Path libDir = Paths.get(tempDir);
			Path pathToClass = libDir.resolve("WEB-INF/lib/" + output + ".class");
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
	
	public URL[] getUrls() {
		return genericLoader.getURLs();
	}

	public static class CustomLoader extends URLClassLoader{
		public CustomLoader(URL[] urls) {
			super(urls);
		}

		@Override 
		public void addURL(URL url) {
			super.addURL(url);
		}
	}
}
