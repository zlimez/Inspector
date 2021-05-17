package chain;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import methodsEval.MethodInfo;

public class Blacklist {
	private static Map<String, int[]> blacklist;
	
	static {
		blacklist = new HashMap<>();
		String signature = "java.lang.reflect.Method.invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;";
		int[] mustTaintArgs = new int[] {0, 1};
		blacklist.put(signature, mustTaintArgs);
		signature = "java.lang.Runtime.exec";
		blacklist.put(signature, null);
	}
	
	public static boolean isBlacklisted(Gadget gadget) {
		MethodInfo method = gadget.getMethod();
		String signature = gadget.getClassname() + "." + method.getName() + method.getDesc();
		if (blacklist.containsKey(signature)) {
			int[] mustTaintArgs = blacklist.get(signature);
			if (mustTaintArgs == null) 
				return true;
			Set<Integer> taintedArgs = method.getUserControlledArgPos().keySet();
			for (int i : mustTaintArgs) {
				if (!taintedArgs.contains(i))
					return false;
				// method caller must be directly come from tainted byte stream
				if (i == 0 && !method.getIsField()) 
					return false;
			}
			return true;
		} else 
			return false;
	}
}
