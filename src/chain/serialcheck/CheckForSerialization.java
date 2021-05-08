package chain.serialcheck;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import hierarchy.SortClass.ClassAndBytes;

public class CheckForSerialization {
	public static List<ClassAndBytes> deserializationOccurences(List<ClassAndBytes> clazzes) throws IOException {
		List<ClassAndBytes> deserialLocation = new ArrayList<>();
		Iterator<ClassAndBytes> it = clazzes.iterator();
		while (it.hasNext()) {
			ClassAndBytes clazz = it.next();
			ClassReader cr = new ClassReader(clazz.getBytes());
			ClassWriter cw = new ClassWriter(cr, 0);
			CheckDeserialization cd = new CheckDeserialization(cw);
			cr.accept(cd, 0);
			if (cd.deserializing) {
				deserialLocation.add(clazz);
			}	
		}
		return deserialLocation;
	}
}
