package precompute;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.Scanner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import chain.Entry;
import hierarchy.BuildOrder;

// compute hierarchy for jdk classes when initiate program append user classes to this hierarchy the append the user class contained hierarchy 
public class ReadSystem {
	private String pathToJdkClasslist = "/home/pcadmin/Deserialization/classlist.txt";
	
	public ReadSystem(String path) {
		if (!path.isBlank()) {
			this.pathToJdkClasslist = path;
		}	
	}
	
	public StoreHierarchy readAndCreate() throws IOException, ClassNotFoundException {
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
}
