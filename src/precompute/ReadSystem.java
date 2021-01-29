package precompute;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import hierarchy.SortClass;

// compute hierarchy for jdk classes when initiate program append user classes to this hierarchy the append the user class contained hierarchy 
public class ReadSystem {
	private String pathToJdkClasslist = "/usr/lib/jvm/java-11-openjdk-amd64/lib/classlist";
	
	public ReadSystem(String path) {
		if (!path.isBlank()) {
			this.pathToJdkClasslist = path;
		}	
	}
	
	public StoreHierarchy readAndCreate() throws IOException, ClassNotFoundException {
		List<Map<Class<?>, byte[]>> serialAndAllClasses = new ArrayList<Map<Class<?>,byte[]>>();
		Map<Class<?>, byte[]> serial = new Hashtable<Class<?>, byte[]>();
		Map<Class<?>, byte[]> all = new Hashtable<Class<?>, byte[]>();
		Class<?> serializable = Class.forName("java.io.Serializable");
		if (pathToJdkClasslist.isBlank()) {
			pathToJdkClasslist = "/usr/lib/jvm/java-11-openjdk-amd64/lib/classlist";
		}
		try (
			FileInputStream in = new FileInputStream(pathToJdkClasslist);
			Scanner scan = new Scanner(in);
		) {
			while (scan.hasNextLine()) {
				String className = scan.nextLine();
				if (!(className.contains("\\$") || className.contains("jdk/internal"))) { //exclude nested class first and classes for internal jdk usage
					String proper = className.replaceAll("/", "\\.");
					ClassReader cr = new ClassReader(proper);
					ClassWriter cw = new ClassWriter(cr, 0);
					cr.accept(cw, 0);
					byte[] classBytes = cw.toByteArray();
					Class<?> clazz = Class.forName(proper);
					all.put(clazz, classBytes);
					if (serializable.isAssignableFrom(clazz)) {
						serial.put(clazz, classBytes);
					}
				}
			}
			serialAndAllClasses.add(serial);
			serialAndAllClasses.add(all);
			return new StoreHierarchy(SortClass.computeHierarchy(all, serial));
		}
	}
	
	public class StoreHierarchy implements Serializable {
		private static final long serialVersionUID = 1L;
		private Map<String, Map<Class<?>, byte[]>> hierarchy;
		protected StoreHierarchy(Map<String, Map<Class<?>, byte[]>> hierarchy) {
			this.hierarchy = hierarchy;
		}
		
		public Map<String, Map<Class<?>, byte[]>> getHierarchy() {
			return hierarchy;
		}
	}
}
