package precompute;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Hashtable;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import chain.Entry;
import hierarchy.BuildOrder;
import hierarchy.SortClass.CustomLoader;
import hierarchy.Unpack;

// compute hierarchy for jdk classes when initiate program append user classes to this hierarchy the append the user class contained hierarchy 
public class ReadSystem {
	public static StoreHierarchy readAndCreate(String pathToJdkClasslist) throws IOException, ClassNotFoundException {
		Map<Class<?>, byte[]> serial = new Hashtable<Class<?>, byte[]>();
		Map<Class<?>, byte[]> all = new Hashtable<Class<?>, byte[]>();
		Class<?> serializable = Class.forName("java.io.Serializable");

		try (
			FileInputStream in = new FileInputStream(pathToJdkClasslist);
			Scanner scan = new Scanner(in);
		) {
			while (scan.hasNextLine()) {
				String className = scan.nextLine();
				ClassReader cr = new ClassReader(className);
				ClassWriter cw = new ClassWriter(cr, 0);
				cr.accept(cw, 0);
				byte[] classBytes = cw.toByteArray();
				Class<?> clazz = Class.forName(className);
				all.put(clazz, classBytes);
				if (serializable.isAssignableFrom(clazz)) {
					serial.put(clazz, classBytes);
				}
			}

			return new StoreHierarchy(BuildOrder.computeSystemHierarchy(all, serial), Entry.EntryPoint(serial));
		}
	}
	
	public static StoreHierarchy readRtJar(String pathToRtJar) throws ClassNotFoundException, IOException {
		Map<Class<?>, byte[]> subSerial = new Hashtable<Class<?>, byte[]>();
		Map<Class<?>, byte[]> all = new Hashtable<Class<?>, byte[]>();
		Class<?> serializable = Class.forName("java.io.Serializable");
		CustomLoader rtLoader = Unpack.getJarClassLoader(new Path[]{Paths.get(pathToRtJar)});
		ConfigurationBuilder config = new ConfigurationBuilder();
		Reflections reflections = new Reflections(config.setUrls(ClasspathHelper.forClassLoader(rtLoader)).setScanners(new SubTypesScanner(false)));
		Set<String> allTypes = reflections.getAllTypes();
		allTypes.forEach(t -> {
			try {
				ClassReader cr = new ClassReader(t);
				ClassWriter cw = new ClassWriter(cr, 0);
				cr.accept(cw, 0);
				byte[] classBytes = cw.toByteArray();
				Class<?> clazz = Class.forName(t);
				all.put(clazz, classBytes);
				if (serializable.isAssignableFrom(clazz)) {
					subSerial.put(clazz, classBytes);
				}
			} catch (ClassNotFoundException | IOException e) {
				e.printStackTrace();
			}
		});
		
		
		return new StoreHierarchy(BuildOrder.computeSystemHierarchy(all, subSerial), Entry.EntryPoint(subSerial));
	}
}
