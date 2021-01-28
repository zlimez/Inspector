package chain.serialcheck;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public class CheckForSerialization {
	public static Map<Class<?>, byte[]> deserializationOccurences(Map<Class<?>, byte[]> clazzes) throws IOException {
		Map<Class<?>, byte[]> deserialLocation = new HashMap<Class<?>, byte[]>();
		Iterator<Map.Entry<Class<?>, byte[]>> it = clazzes.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Class<?>, byte[]> clazz = (Map.Entry<Class<?>, byte[]>) it.next();
			ClassReader cr = new ClassReader(clazz.getValue());
			ClassWriter cw = new ClassWriter(cr, 0);
			CheckDeserialization cd = new CheckDeserialization(cw);
			cr.accept(cd, 0);
			if (cd.deserializing) {
				deserialLocation.put(clazz.getKey(), clazz.getValue());
			}	
		}
		return deserialLocation;
	}
}
