package chain;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.tree.analysis.Value;

import hierarchy.SortClass.ClassAndBytes;
import methodsEval.MethodInfo;
import userFields.CustomInterpreter.ReferenceValue;
import userFields.UserValues;;

public class Entry {
	public static Map<Class<?>, List<Object>> EntryPoint(List<ClassAndBytes> classes) {
		Map<Class<?>, List<Object>> entry = new HashMap<>();
		Iterator<ClassAndBytes> it = classes.iterator();
		while (it.hasNext()) {
			List<Object> info = new ArrayList<Object>();
			ClassAndBytes clazz = it.next();
			Class<?> c = clazz.getClazz();
			Method[] methods = c.getDeclaredMethods();
			for (Method method : methods) {
				String methodName = method.getName();
				if (methodName.equals("readObject") || methodName.equals("readResolve") || methodName.equals("validateObject") || methodName.equals("readObjectNoData") || methodName.equals("readExternal")) {
					boolean isStatic = Modifier.isStatic(method.getModifiers()) ? true : false;
					Map<Integer, Value> sim = new HashMap<Integer, Value>();
					int argLength = method.getParameterCount();  
					for (int i = 0; i < argLength; i++) { 
						sim.put(i + 1, new ReferenceValue(UserValues.USERINFLUENCED_REFERENCE)); // input stream is controlled by user hence whatever is read from it is too
					}
					if (!isStatic) {
						ReferenceValue thisObject = new ReferenceValue(UserValues.USERINFLUENCED_REFERENCE);
						thisObject.setField();
						sim.put(0, thisObject);
					}
					MethodInfo mf = new MethodInfo(methodName, MethodInfo.convertDescriptor(method), isStatic, sim);  
					Gadget possibleEntry = new Gadget(c.getName(), c, mf, null, clazz.getBytes(), sim, 1);
					Collection<MethodInfo> next = possibleEntry.InspectMethod(new HashMap<>());
					//magic methods that actually invoke further method
					if (next.size() > 0) 
						info.add(mf);	 
				}
			}
			if (!info.isEmpty()) {
				info.add(0, clazz.getBytes());
				entry.put(c, info);
			}
			
		}
		return entry;
	}
}
