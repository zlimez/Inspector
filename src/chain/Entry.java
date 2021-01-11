package chain;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Entry {
	public static Map<Class<?>, byte[]> EntryPoint(Map<Class<?>, byte[]> classes) {
		Map<Class<?>, byte[]> entry = new HashMap<>();
		List<String> temp = new ArrayList<String>();
		Iterator<Map.Entry<Class<?>, byte[]>> it = classes.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Class<?>, byte[]> clazz = (Map.Entry<Class<?>, byte[]>) it.next();
			Method[] methods = clazz.getKey().getDeclaredMethods();
			for (Method method : methods) {
				temp.add(method.getName());
			}
			if (temp.contains("readObject") || temp.contains("readResolve") || temp.contains("validateObject") || temp.contains("readObjectNoData") || temp.contains("Finalize") || temp.contains("readExternal")) {
				entry.put(clazz.getKey(), clazz.getValue());
			}
		}
		return entry;
	}
}
