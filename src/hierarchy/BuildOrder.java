package hierarchy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class BuildOrder {
	public static Map<String, Map<Class<?>, byte[]>> computeHierarchy(Map<Class<?>, byte[]> allTypes, Map<Class<?>, byte[]> serialTypes) {
		Iterator<Entry<Class<?>, byte[]>> it = allTypes.entrySet().iterator();
		Map<String, Map<Class<?>, byte[]>> hierarchy = new HashMap<>();
		while (it.hasNext()) {
			Map.Entry<Class<?>, byte[]> e = (Map.Entry<Class<?>, byte[]>) it.next();
			Class<?> parent = e.getKey();
			String className = parent.getCanonicalName();
			Map<Class<?>, byte[]> subtypes = new HashMap<Class<?>, byte[]>();
			serialTypes.forEach((k, v) -> {
				if (parent.isAssignableFrom(k)) {
					subtypes.put(k, v);
				}
			});
			if (subtypes.size() > 0) {
				hierarchy.put(className, subtypes);
			}
		}
		return hierarchy;
	}
	
	public static Map<Class<?>, Map<Class<?>, byte[]>> computeSystemHierarchy(Map<Class<?>, byte[]> allTypes, Map<Class<?>, byte[]> serialTypes) {
		Iterator<Entry<Class<?>, byte[]>> it = allTypes.entrySet().iterator();
		Map<Class<?>, Map<Class<?>, byte[]>> classHierarchy = new HashMap<>();
		while (it.hasNext()) {
			Map.Entry<Class<?>, byte[]> e = (Map.Entry<Class<?>, byte[]>) it.next();
			Class<?> parent = e.getKey();
			Map<Class<?>, byte[]> subtypes = new HashMap<Class<?>, byte[]>();
			serialTypes.forEach((k, v) -> {
				if (parent.isAssignableFrom(k)) {
					subtypes.put(k, v);
				}
			});
			classHierarchy.put(parent, subtypes);
		}
		return classHierarchy;
	}

	public static Map<String, Map<Class<?>, byte[]>> combineHierarchies(Map<Class<?>, byte[]> serialTypes, Map<Class<?>, Map<Class<?>, byte[]>> system, Map<String, Map<Class<?>, byte[]>> target) {
		Iterator<Entry<Class<?>, Map<Class<?>, byte[]>>> it = system.entrySet().iterator();
		Map<String, Map<Class<?>, byte[]>> finalHierarchy = new HashMap<>();
		while (it.hasNext()) {
			Map.Entry<Class<?>, Map<Class<?>, byte[]>> e = (Map.Entry<Class<?>, Map<Class<?>, byte[]>>) it.next();
			Class<?> parent = e.getKey();
			String classname = parent.getCanonicalName();
			serialTypes.forEach((k, v) -> {
				if (parent.isAssignableFrom(k)) {
					e.getValue().put(k, v);
				}
			});
			Map<Class<?>, byte[]> moreSubtypes = e.getValue();
			if (!moreSubtypes.isEmpty()) {
				finalHierarchy.put(classname, moreSubtypes);
			}
		}
		finalHierarchy.putAll(target);
		return finalHierarchy;
	}
}
