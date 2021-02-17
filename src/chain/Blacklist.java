package chain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Blacklist {
	private static Map<String, List<String>> blacklist = new HashMap<String, List<String>>();
	
	public static Map<String, List<String>> getList() {
		return blacklist;
	}
	
	// command line function
	public static void changeList(String clazz, String method, String desc) {
		String signature = method + ":" + desc;
		if (!blacklist.containsKey(clazz)) {
			List<String> methods = new ArrayList<String>();
			methods.add(signature);
			blacklist.put(clazz, methods);
		} else {
			List<String> m = blacklist.get(clazz);
			m.add(signature);
		}
	}
}
