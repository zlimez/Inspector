package chain;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Entry {
	public static Map<Class<?>, List<Object>> EntryPoint(Map<Class<?>, byte[]> classes) {
		Map<Class<?>, List<Object>> entry = new HashMap<>();
		Iterator<Map.Entry<Class<?>, byte[]>> it = classes.entrySet().iterator();
		while (it.hasNext()) {
			List<Object> info = new ArrayList<Object>();
			Map.Entry<Class<?>, byte[]> clazz = (Map.Entry<Class<?>, byte[]>) it.next();
			Method[] methods = clazz.getKey().getDeclaredMethods();
			info.add(clazz.getValue());
			for (Method method : methods) {
				String methodName = method.getName();
				if (methodName.equals("readObject") || methodName.equals("readResolve") || methodName.equals("validateObject") || methodName.equals("readObjectNoData") || methodName.equals("Finalize") || methodName.equals("readExternal")) {
					info.add(method);
					entry.put(clazz.getKey(), info); // assume one magic method per class first info size 2
				};
			}
		}
		return entry;
	}
}
