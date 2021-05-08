package hierarchy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import hierarchy.SortClass.ClassAndBytes;

public class BuildOrder {
	public static Map<String, List<ClassAndBytes>> computeHierarchy(List<ClassAndBytes> allTypes, List<ClassAndBytes> serialTypes) {
		Iterator<ClassAndBytes> it = allTypes.iterator();
		Map<String, List<ClassAndBytes>> hierarchy = new HashMap<>();
		while (it.hasNext()) {
			ClassAndBytes e = it.next();
			Class<?> parent = e.getClazz();
			String className = parent.getName();
			List<ClassAndBytes> subtypes = new ArrayList<ClassAndBytes>();
			serialTypes.forEach((k) -> {
				if (parent.isAssignableFrom(k.getClazz())) {
					subtypes.add(k);
				}
			});
			if (subtypes.size() > 0) {
				hierarchy.put(className, subtypes);
			}
		}
		return hierarchy;
	}
	
	public static Map<Class<?>, List<ClassAndBytes>> computeSystemHierarchy(List<ClassAndBytes> allTypes, List<ClassAndBytes> serialTypes) {
		Iterator<ClassAndBytes> it = allTypes.iterator();
		Map<Class<?>, List<ClassAndBytes>> classHierarchy = new HashMap<>();
		while (it.hasNext()) {
			ClassAndBytes e = it.next();
			Class<?> parent = e.getClazz();
			List<ClassAndBytes> subtypes = new ArrayList<>();
			serialTypes.forEach((k) -> {
				if (parent.isAssignableFrom(k.getClazz())) {
					subtypes.add(k);
				}
			});
			classHierarchy.put(parent, subtypes);
		}
		return classHierarchy;
	}

	public static Map<String, List<ClassAndBytes>> combineHierarchies(List<ClassAndBytes> serialTypes, Map<Class<?>, List<ClassAndBytes>> system, Map<String, List<ClassAndBytes>> target) {
		Iterator<Entry<Class<?>, List<ClassAndBytes>>> it = system.entrySet().iterator();
		Map<String, List<ClassAndBytes>> finalHierarchy = new HashMap<>();
		while (it.hasNext()) {
			Map.Entry<Class<?>, List<ClassAndBytes>> e = (Map.Entry<Class<?>, List<ClassAndBytes>>) it.next();
			Class<?> parent = e.getKey();
			String classname = parent.getName();
			serialTypes.forEach((k) -> {
				if (parent.isAssignableFrom(k.getClazz())) {
					e.getValue().add(k);
				}
			});
			List<ClassAndBytes> moreSubtypes = e.getValue();
			if (!moreSubtypes.isEmpty()) {
				finalHierarchy.put(classname, moreSubtypes);
			}
		}
		finalHierarchy.putAll(target);
		return finalHierarchy;
	}
}
