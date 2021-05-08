package hierarchy;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import org.objectweb.asm.ClassReader;

import methodsEval.MethodInfo;

public class SortClass {
	private String[] pathToFile;
	private CustomLoader genericLoader;
	private String serverDir;
	private String tempDir;
	private Set<String> noDefClasses;
	
	public SortClass(String[] filesToScan, String ... path) {
		this.pathToFile = filesToScan;
		if (path.length > 0) {
			this.serverDir = path[0];
			if (path.length > 1) {
				this.tempDir = path[1];
			}
		}
	}
	
	// assumption that only user classes will actively digest client input
	// for war file
	@SuppressWarnings("unchecked")
	public List<List<ClassAndBytes>> getSerialAndAllClasses(boolean dependencyResolved) throws IOException, ClassNotFoundException {
		boolean canDeleteTempDir = true;
		if (tempDir != null) {
			Unpack.initialize(tempDir);
			canDeleteTempDir = false;
		} else 
			Unpack.initialize();
		
		if (serverDir != null) {
			Object[] loaderAndPaths = Unpack.getLibLoader(Paths.get(pathToFile[0]));
			Object[] URLAndClass = Unpack.getClassesPath();
			genericLoader = (CustomLoader) loaderAndPaths[0];
			List<String> pathToLibJars = (List<String>) loaderAndPaths[1];
			List<String> pathToAll = new ArrayList<>(pathToLibJars);
			pathToAll.add((String) URLAndClass[0]);
			pathToAll.addAll(Unpack.genericResourceLoader(genericLoader, serverDir));
			
			if (!dependencyResolved)
				noDefClasses = DependencyTree.resolveDependencies(pathToAll.toArray(new String[pathToAll.size()]));
			List<List<ClassAndBytes>> combined = getSerialLibClasses(pathToLibJars);
			
			genericLoader.addURL((URL) URLAndClass[1]);
			List<byte[]> clazzes = (List<byte[]>) URLAndClass[2];

			List<ClassAndBytes> allSerialTypes = combined.get(0);
			List<ClassAndBytes> allClasses = combined.get(1); 
			List<ClassAndBytes> allHandlers = combined.get(2);
		
			Class<?> serial = Class.forName("java.io.Serializable");
			Class<?> invhandler = Class.forName("java.lang.reflect.InvocationHandler");
			clazzes.forEach(b -> {
				ClassReader cr = new ClassReader(b);
				String outputStr = cr.getClassName().replaceAll("/", "\\.");	
				if (noDefClasses == null || !noDefClasses.contains(outputStr)) { 
					try {
						Class<?> userClazz = Class.forName(outputStr, false, genericLoader);
						ClassAndBytes cb = new ClassAndBytes(userClazz, b);
						allClasses.add(cb);
						if (serial.isAssignableFrom(userClazz)) {
							allSerialTypes.add(cb);
							if (invhandler.isAssignableFrom(userClazz)) {
								try {
									Method invoke = userClazz.getDeclaredMethod("invoke", Object.class, Method.class, Object[].class);
									cb.setInvokeDesc(MethodInfo.convertDescriptor(invoke));
									allHandlers.add(cb);
								} catch (NoSuchMethodException e) {
								}
							}
						}
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}
				}
			});
			if (canDeleteTempDir)
				Unpack.deleteDirectory();
			return combined;
		}
		Path[] paths = new Path[pathToFile.length];
		for (int i = 0; i < pathToFile.length; i++) {
			paths[i] = Paths.get(pathToFile[i]);
		}
		genericLoader = Unpack.getJarClassLoader(paths);
		if (!dependencyResolved)
			noDefClasses = DependencyTree.resolveDependencies(pathToFile);
		List<List<ClassAndBytes>> serialAndAllTypes = getJarClasses();
		Unpack.deleteDirectory();
		return serialAndAllTypes;
	}
	
	// for jar 
	private List<List<ClassAndBytes>> getJarClasses() throws ClassNotFoundException, FileNotFoundException, IOException {
		List<List<ClassAndBytes>> result = new ArrayList<>();
		List<Class<?>> temp = new ArrayList<Class<?>>();
		List<ClassAndBytes> subtypes = new ArrayList<>();
		List<ClassAndBytes> all = new ArrayList<>();
		List<ClassAndBytes> handlers = new ArrayList<>();
		Set<String> allTypes = getAllClass(pathToFile);
//		System.out.println(allTypes.size());
		if (noDefClasses != null)
			allTypes.removeAll(noDefClasses);
//		System.out.println(allTypes.size());
		allTypes.forEach(t -> {
			try {
				temp.add(Class.forName(t, false, genericLoader));
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		});
		Class<?> serial = Class.forName("java.io.Serializable");
		Class<?> invhandler = Class.forName("java.lang.reflect.InvocationHandler");
		for (Class<?> type : temp) {
			String clazzName = type.getName();
			String output = clazzName.replaceAll("\\.", "/");
			Path libDir = Unpack.warDir;
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
				ClassAndBytes cb = new ClassAndBytes(type, libClazzBytes);
				if (serial.isAssignableFrom(type)) {
					subtypes.add(cb);
					if (invhandler.isAssignableFrom(type)) {
						try {
							Method invoke = type.getDeclaredMethod("invoke", Object.class, Method.class, Object[].class);
							cb.setInvokeDesc(MethodInfo.convertDescriptor(invoke));
							handlers.add(cb);
						} catch (NoSuchMethodException e) {
						}
					}
				}
				all.add(cb);
			}
		}
		
		result.add(subtypes);
		result.add(all);
		result.add(handlers);
		return result;
	}
	
	//for war
	private List<List<ClassAndBytes>> getSerialLibClasses(List<String> pathToLibJars) throws ClassNotFoundException, FileNotFoundException, IOException {
		List<Class<?>> temp = new ArrayList<Class<?>>();
		List<List<ClassAndBytes>> result = new ArrayList<>();
		List<ClassAndBytes> subtypes = new ArrayList<>();
		List<ClassAndBytes> all = new ArrayList<>();
		List<ClassAndBytes> handlers = new ArrayList<>();
		Set<String> allTypes = getAllClass(pathToLibJars.toArray(new String[pathToLibJars.size()]));
		if (noDefClasses != null)
			allTypes.removeAll(noDefClasses);
		
		allTypes.forEach(t -> {
			try {
				temp.add(Class.forName(t, false, genericLoader));
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		});
		Class<?> serial = Class.forName("java.io.Serializable");
		Class<?> invhandler = Class.forName("java.lang.reflect.InvocationHandler");
		for (Class<?> type : temp) {
			String clazzName = type.getName();
			String output = clazzName.replaceAll("\\.", "/");
			Path libDir = Unpack.warDir;
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
				ClassAndBytes cb = new ClassAndBytes(type, libClazzBytes);
				if (serial.isAssignableFrom(type)) {
					subtypes.add(cb);
					if (invhandler.isAssignableFrom(type)) {
						try {
							Method invoke =type.getDeclaredMethod("invoke", Object.class, Method.class, Object[].class);
							cb.setInvokeDesc(MethodInfo.convertDescriptor(invoke));
							handlers.add(cb);
						} catch (NoSuchMethodException e) {
						}
					}
				}
				all.add(cb); 
			}
		}
		result.add(subtypes);
		result.add(all);
		result.add(handlers);
		return result;
	}
	
	public URL[] getUrls() {
		return genericLoader.getURLs();
	}
	
	public static Set<String> getAllClass(String[] pathToFile) throws IOException {
		Set<String> allClass = new HashSet<String>();
		for (String path : pathToFile) {
			StringBuffer sb = new StringBuffer("jar tf ");
			sb.append(path);
			
			ProcessBuilder pb = new ProcessBuilder();
			pb.command("bash","-c", sb.toString());
			Process process = pb.start();
		
			try (Scanner in = new Scanner(new BufferedInputStream(process.getInputStream()))) {
				while (in.hasNext()) {
					String line = in.next();
					if (line.endsWith(".class")) {
						String classname = line.replaceAll("/", "\\.");
						classname = classname.substring(0, classname.length() - 6);
						allClass.add(classname);
					}
				}
			}
		}
		return allClass;
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
	
	public static class ClassAndBytes implements Serializable {
		private static final long serialVersionUID = 1L;
		private Class<?> clazz;
		private byte[] bytes;
		private String invokeDesc; // for handler invoke method descriptor
		
		public ClassAndBytes(Class<?> clazz, byte[] bytes) {
			this.clazz = clazz;
			this.bytes = bytes;
		}

		public Class<?> getClazz() {
			return clazz;
		}

		public byte[] getBytes() {
			return bytes;
		}
		
		public String getInvokeDesc() {
			return invokeDesc;
		}
		
		public void setInvokeDesc(String invokeDesc) {
			this.invokeDesc = invokeDesc;
		}
	}
}
