package chain;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import hierarchy.SortClass;

public class CheckForSerialization {
	public static void main(String[] args) throws ClassNotFoundException, IOException {
		deserializationOccurences(SortClass.getSerialClasses()).forEach((k, v) -> {
			System.out.println(k + ":" + v);
		});
	}
	
	public static Map<Class<?>, byte[]> deserializationOccurences(Map<Class<?>, byte[]> serialclazzes) throws IOException {
		Map<Class<?>, byte[]> deserialLocation = new HashMap<Class<?>, byte[]>();
		Iterator<Map.Entry<Class<?>, byte[]>> it = serialclazzes.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Class<?>, byte[]> serialclazz = (Map.Entry<Class<?>, byte[]>) it.next();
			ClassReader cr = new ClassReader(serialclazz.getValue());
			ClassWriter cw = new ClassWriter(cr, 0);
			CheckDeserialization cd = new CheckDeserialization(cw);
			cr.accept(cd, 0);
			if (cd.deserializing) {
				deserialLocation.put(serialclazz.getKey(), serialclazz.getValue());
			}	
		}
		return deserialLocation;
	}
}
