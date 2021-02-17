package chain;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.tree.analysis.BasicValue;

import methodsEval.MethodInfo;
import userFields.UserFieldInterpreter;

public class Entry {
	public static Map<Class<?>, List<Object>> EntryPoint(Map<Class<?>, byte[]> classes) {
		Map<Class<?>, List<Object>> entry = new HashMap<>();
		Iterator<Map.Entry<Class<?>, byte[]>> it = classes.entrySet().iterator();
		while (it.hasNext()) {
			List<Object> info = new ArrayList<Object>();
			Map.Entry<Class<?>, byte[]> clazz = (Map.Entry<Class<?>, byte[]>) it.next();
			Class<?> c = clazz.getKey();
			Method[] methods = c.getDeclaredMethods();
			for (Method method : methods) {
				String methodName = method.getName();
				if (methodName.equals("readObject") || methodName.equals("readResolve") || methodName.equals("validateObject") || methodName.equals("readObjectNoData") || methodName.equals("readExternal")) {
					boolean isStatic = Modifier.isStatic(method.getModifiers()) ? true : false;
					Map<Integer, BasicValue> sim = new Hashtable<Integer, BasicValue>();
//					int argLength = method.getParameterCount();  // include finalize as magic method?
//					for (int i = 1; i <= argLength; i++) { 
//						sim.put(i, UserFieldInterpreter.USER_INFLUENCED); // input stream is controlled by user hence whatever is read from it is too
//					}
					if (!isStatic) {
						sim.put(0, UserFieldInterpreter.USER_DERIVED);
					}
					MethodInfo mf = new MethodInfo(methodName, MethodInfo.convertDescriptor(method), isStatic);  
					Gadget possibleEntry = new Gadget(c, mf, null, clazz.getValue(), sim, 1);
					List<MethodInfo> next = possibleEntry.InspectMethod();

					if (next.size() > 0) {
//						System.out.println(c.getCanonicalName() + ":" + mf.getName());
//						for (MethodInfo e : next) {
//							System.out.println(" " + e.getOwner() + ":" + e.getName());
//						}
						info.add(mf);
					} //magic methods that actually invoke further method
				}
			}
			if (!info.isEmpty()) {
				info.add(0, clazz.getValue());
				entry.put(c, info);
			}
			
		}
		return entry;
	}
}
