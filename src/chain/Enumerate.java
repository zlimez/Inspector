package chain;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.tree.analysis.BasicValue;

import chain.serialcheck.CheckForSerialization;
import hierarchy.BuildOrder;
import hierarchy.SortClass;
import methodsEval.MethodInfo;
import precompute.DbConnector;
import precompute.StoreHierarchy;
import userFields.UserFieldInterpreter;

public class Enumerate {
	private static Map<Class<?>, byte[]> serialClazzes;
	protected static Map<Class<?>, byte[]> allClasses;
	protected static Map<String, Map<Class<?>, byte[]>> hierarchy; // complete hierarchy
	private static Map<Class<?>, List<Object>> entryPoints;
	private static Map<String, List<String>> blacklist; // List of interesting classes with their respective interesting methods
	private static PrintWriter out;
	private static int maxDepth = 6;

	
	public static void main(String[] args) throws ClassNotFoundException, IOException, SQLException {
//		Blacklist.changeList("java.lang.Runtime", "exec", "(Ljava/lang/String;)Ljava/lang/Process;");
//		Blacklist.changeList("java.lang.reflect.Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
		int jdkVersion = Integer.parseInt(args[0]);
		DbConnector.initBlacklist(jdkVersion); 
		Enumerate target = new Enumerate(jdkVersion, args[1], Blacklist.getList(), args[2]); 
		Map<Class<?>, byte[]> dc = CheckForSerialization.deserializationOccurences(allClasses);
		if (dc.isEmpty()) {
			System.out.println("Warning no deserialization process in the application");
		} else {
			out.print("Location of deserialization: ");
			dc.forEach((k, v) -> {
				out.print(k.getCanonicalName() + "; ");
			});
			out.println();
		}
		target.initAllEntry();
		out.flush();
		out.close();
	}
	
	public Enumerate(int jdkVersion, String pathToFile, Map<String, List<String>> blacklist, String outputFile) throws ClassNotFoundException, IOException, SQLException {
		SortClass sort = new SortClass(pathToFile);
		List<Map<Class<?>, byte[]>> all = sort.getSerialAndAllClasses();
		serialClazzes = all.get(0);
		allClasses = all.get(1);
//		hierarchy.forEach((k, v) -> {
//			System.out.print(k + ":");
//			v.forEach((c, e) -> System.out.print(c.getCanonicalName() + ";"));
//			System.out.println();
//		});
//		hierarchy = BuildOrder.computeHierarchy(allClasses, serialClazzes);
		Map<String, Map<Class<?>, byte[]>> fileHierarchy = BuildOrder.computeHierarchy(allClasses, serialClazzes);
		StoreHierarchy env = DbConnector.getSystemInfo(jdkVersion);
		hierarchy = BuildOrder.combineHierarchies(serialClazzes, env.getHierarchy(), fileHierarchy);
		entryPoints = Entry.EntryPoint(serialClazzes);
		entryPoints.putAll(env.getEntryPoints());
		Enumerate.blacklist = blacklist;
		out = new PrintWriter(outputFile);
	}
	
	public void initAllEntry() {
		Map<Integer, BasicValue> sim = new Hashtable<Integer, BasicValue>();
		entryPoints.forEach((k, v) -> { // account for more than one magic method per class
			if (k.getCanonicalName().equals("java.util.PriorityQueue")) {
				for (int i = 1; i < v.size(); i++) {
					MethodInfo mf = (MethodInfo) v.get(i);
					if (!mf.getMethodType()) {
						sim.put(0, UserFieldInterpreter.USER_DERIVED);
					}
//					int argLength = mf.getParamCount();
//					for (int j = 1; j <= argLength; j++) {
//						sim.put(j, UserFieldInterpreter.USER_INFLUENCED);
//					}
					Gadget gadget = new Gadget(k, mf, null, (byte[]) v.get(0), sim, 1);
					try {
						findChain(gadget);
					} catch (FileNotFoundException | ClassNotFoundException e) {
						e.printStackTrace();
					}
					sim.clear();
				}
			}
		});
	}
	/*
	 *  must add in queue function for BFS
	 */
	public void findChain(Gadget gadget) throws FileNotFoundException, ClassNotFoundException { // depth first search
		System.out.println(gadget.getClazz().getCanonicalName());
		System.out.println(gadget.getMethod().getName() + ":" + gadget.getMethod().getDesc());
		
		if (blacklist.containsKey(gadget.getClazz().getCanonicalName())) {
			String clazz = gadget.getClazz().getCanonicalName();
			MethodInfo method = gadget.getMethod();
			if (blacklist.get(clazz).contains(method.getName() + ":" + method.getDesc())) {
				returnChain(gadget);
				return; // chain found parent reference to show the chain
			}
		}
		
		if (gadget.getBytes() == null) {
			System.out.println("The class " + gadget.getClazz().getCanonicalName() + " is not found hence terminated path");
			return; // when class is not included in search space
		}

		if (gadget.getDepth() >= maxDepth) {
			System.out.println("Max recursion depth reached");
			return; // prevent excessive recursion
		}
		
		List<MethodInfo> next = gadget.InspectMethod();
		
		if (next.size() == 0) {
			System.out.println("No next method found");
			return;
		} // no next method found to be invoked
		
		for (MethodInfo m : next) {
//			System.out.println(m.getOwner() + ":" + m.getName());
			List<Gadget> children = gadget.findChildren(m);
			for (Gadget child : children) {
				findChain(child);
			}
		}
	}
	
	// write the result to a file
	public void returnChain(Gadget gadget) throws FileNotFoundException {
		String classname = gadget.getClazz().getCanonicalName(); // inverse chain printed
		String methodname = gadget.getMethod().getName();
		Enumerate.out.println(classname + ":" + methodname);
		Gadget parent = gadget.getParent();
		if (parent == null) {
			out.println("End of chain");
			return;
		}
		returnChain(parent);
	}
}
