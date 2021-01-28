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
	public static void changeList(int action, String clazz, String[] methods) {
		if (action == -1) { // remove method from blackList
			if (methods == null) {
				blacklist.remove(clazz);
			} else {
				List<String> m = blacklist.get(clazz);
				for (String method : methods) {
					m.remove(method);
				}
			}
		} else if (action == 1) { // add method to list
			if (!blacklist.containsKey(clazz)) {
				List<String> m = new ArrayList<String>();
				for (String method : methods) {
					m.add(method);
				}
				blacklist.put(clazz, m);
			} else {
				List<String> m = blacklist.get(clazz);
				for (String method : methods) {
					m.add(method);
				}
			}
		}
	}
}
