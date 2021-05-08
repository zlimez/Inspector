package precompute;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import chain.Entry;
import hierarchy.BuildOrder;
import hierarchy.SortClass.ClassAndBytes;
import methodsEval.MethodInfo;

// compute hierarchy for jdk classes when initiate program append user classes to this hierarchy the append the user class contained hierarchy 
public class ReadSystem {
	public static Set<String> javaMods = new HashSet<>();
	
	static {
		javaMods.add("java.base.jmod");
//		javaMods.add("java.compiler.jmod");
//		javaMods.add("java.desktop.jmod");	
//		javaMods.add("java.management.jmod");
//		javaMods.add("java.net.http.jmod");
	}
	
	public static StoreHierarchy readJmods(String pathToJmodDir) throws IOException, ClassNotFoundException {
		List<String> allClasses = new ArrayList<>();
		for (Path p : Files.list(Paths.get(pathToJmodDir)).collect(Collectors.toList())) {
			String entry = "jmod list " + p.toString();
			if (javaMods.contains(p.getFileName().toString())) {
				ProcessBuilder pb = new ProcessBuilder();
				pb.command("bash", "-c", entry);
				try {
					Process process = pb.start();
					try (Scanner in = new Scanner(new BufferedInputStream(process.getInputStream()))) {
						while (in.hasNext()) {
							String line = in.next();
							if (line.endsWith(".class")) {
								String classname = line.replaceAll("/", "\\.");
								classname = classname.substring(8, classname.length() - 6);
								if (!classname.equals("module-info") && !classname.endsWith("Trampoline")) 
									allClasses.add(classname);
							}
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		List<ClassAndBytes> serial = new ArrayList<>();
		List<ClassAndBytes> all = new ArrayList<>();
		List<ClassAndBytes> handlers = new ArrayList<>(); // for invocationhandler subclasses 
		Class<?> serializable = Serializable.class;
		Class<?> invocationHandler = InvocationHandler.class;
		boolean proxyAllowed = serializable.isAssignableFrom(Proxy.class)? true : false;
		
		allClasses.forEach((classname) -> {
			try {
				ClassReader cr = new ClassReader(classname);
				ClassWriter cw = new ClassWriter(cr, 0);
				cr.accept(cw, 0);
				byte[] classBytes = cw.toByteArray();
//				System.out.println(classname);
				Class<?> clazz = Class.forName(classname);
				ClassAndBytes cb = new ClassAndBytes(clazz, classBytes);
				all.add(cb);
				if (serializable.isAssignableFrom(clazz)) {
					serial.add(cb);
					if (proxyAllowed && invocationHandler.isAssignableFrom(clazz)) {
						try {
							Method invoke = clazz.getDeclaredMethod("invoke", Object.class, Method.class, Object[].class);
							cb.setInvokeDesc(MethodInfo.convertDescriptor(invoke));
							handlers.add(cb);
						} catch (NoSuchMethodException e) {
						}
					}
				}
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		});
	
		return new StoreHierarchy(BuildOrder.computeSystemHierarchy(all, serial), Entry.EntryPoint(serial), handlers);
	}
	
	// not available yet
//	public static StoreHierarchy readRtJar(String pathToRtJar) throws ClassNotFoundException, IOException {
//		Map<Class<?>, byte[]> subSerial = new Hashtable<Class<?>, byte[]>();
//		Map<Class<?>, byte[]> all = new Hashtable<Class<?>, byte[]>();
//		Class<?> serializable = Class.forName("java.io.Serializable");
//		CustomLoader rtLoader = Unpack.getJarClassLoader(new Path[]{Paths.get(pathToRtJar)});
//		ConfigurationBuilder config = new ConfigurationBuilder();
//		Reflections reflections = new Reflections(config.setUrls(ClasspathHelper.forClassLoader(rtLoader)).setScanners(new SubTypesScanner(false)));
//		Set<String> allTypes = reflections.getAllTypes();
//		allTypes.forEach(t -> {
//			try {
//				ClassReader cr = new ClassReader(t);
//				ClassWriter cw = new ClassWriter(cr, 0);
//				cr.accept(cw, 0);
//				byte[] classBytes = cw.toByteArray();
//				Class<?> clazz = Class.forName(t);
//				all.put(clazz, classBytes);
//				if (serializable.isAssignableFrom(clazz)) {
//					subSerial.put(clazz, classBytes);
//				}
//			} catch (ClassNotFoundException | IOException e) {
//				e.printStackTrace();
//			}
//		});
//		
//		
//		return new StoreHierarchy(BuildOrder.computeSystemHierarchy(all, subSerial), Entry.EntryPoint(subSerial));
//	}
	
	// outer class need to be serializable for nested class to be
	public static class StoreHierarchy implements Serializable {
		private static final long serialVersionUID = 1L;
		private Map<Class<?>, List<ClassAndBytes>> hierarchy;
		private Map<Class<?>, List<Object>> entries;
		private List<ClassAndBytes> handlers;

		protected StoreHierarchy(Map<Class<?>, List<ClassAndBytes>> hierarchy, Map<Class<?>, List<Object>> entries, List<ClassAndBytes> handlers) {
			this.hierarchy = hierarchy;
			this.entries = entries;
			this.handlers = handlers;
		}
		
		public Map<Class<?>, List<ClassAndBytes>> getHierarchy() {
			return hierarchy;
		}
		
		public Map<Class<?>, List<Object>> getEntryPoints() {
			return entries;
		}
		
		public List<ClassAndBytes> getHandlers() {
			return handlers;
		}
	}
}
