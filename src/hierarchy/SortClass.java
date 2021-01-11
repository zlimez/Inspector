package hierarchy;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
//import java.io.PrintWriter;
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

import hierarchy.loadersAdapters.*;
//import org.objectweb.asm.util.TraceClassVisitor;

public class SortClass {
	public static void main(String[] args) throws ClassNotFoundException, IOException {
		getSerialClasses();
	}
	
	public static Map<Class<?>, byte[]> getSerialClasses() throws IOException, ClassNotFoundException {
		Unpack up = new Unpack();
		ClassLoader classpaths = up.getLibLoader(Paths.get("/home/pcadmin/Deserialization/VulnServlet.war"));
		Map<Class<?>, byte[]> allSerialTypes = getSerialLibClasses(classpaths);
		
		String regexStr = "/";
		String replacementStr = "\\.";
		Pattern pattern = Pattern.compile(regexStr);
		Class<?> serial = Class.forName("java.io.Serializable");
		
		up.getClassesPath().forEach(b -> {
			ClassReader cr = new ClassReader(b);
			String inputClazz = cr.getClassName();
//			if (inputClazz.equals("JamesChiu/VulnServlet")) {
//				TraceClassVisitor tcv = new TraceClassVisitor(new PrintWriter(System.out));
//				cr.accept(tcv, 0);
//			}
			Matcher matcher = pattern.matcher(inputClazz);
			String outputStr = matcher.replaceAll(replacementStr);
			ClassLoader loader = new CustomLoader(b);
			
			try {
				Class<?> userClazz = loader.loadClass(outputStr);
				if (serial.isAssignableFrom(userClazz)) {
					allSerialTypes.put(userClazz, b);
				}
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});
//		allSerialTypes.forEach((k, v) -> System.out.println(k + ": " + v));
		System.out.println(allSerialTypes.size());
		return allSerialTypes;
	}
	
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
				Path libDir = Paths.get("/home/pcadmin/eclipse-workspace/Inspector/SampleWar/WEB-INF/lib");
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
}
