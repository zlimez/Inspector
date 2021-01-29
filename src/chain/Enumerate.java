package chain;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.tree.analysis.BasicValue;

import chain.serialcheck.CheckForSerialization;
import hierarchy.SortClass;
import methodsEval.MethodInfo;

public class Enumerate {
	private Map<Class<?>, byte[]> serialClazzes;
	protected static Map<Class<?>, byte[]> allClasses;
	protected static Map<String, Map<Class<?>, byte[]>> hierarchy; 
	private static Map<Class<?>, List<Object>> entryPoints;
	private Map<String, List<String>> blacklist; // List of interesting classes with their respective interesting methods
	private static PrintWriter out;
	
	public static void main(String[] args) throws ClassNotFoundException, IOException {
		Blacklist.changeList(1, "java.lang.Runtime", new String[]{"exec"});
		Blacklist.changeList(1, "java.lang.reflect.Method", new String[]{"invoke"});
		Enumerate target = new Enumerate(args[0], Blacklist.getList(), args[1]); 
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
	
	public Enumerate(String pathToFile, Map<String, List<String>> blacklist, String outputFile) throws ClassNotFoundException, IOException {
		SortClass sort = new SortClass(pathToFile);
		List<Map<Class<?>, byte[]>> all = sort.getSerialAndAllClasses();
		serialClazzes = all.get(0);
		allClasses = all.get(1);
		hierarchy = SortClass.computeHierarchy(allClasses, serialClazzes);
		entryPoints = Entry.EntryPoint(serialClazzes);
		this.blacklist = blacklist;
		out = new PrintWriter(outputFile);
	}
	
	public void initAllEntry() {
		Map<Integer, BasicValue> sim = new Hashtable<Integer, BasicValue>();
		entryPoints.forEach((k, v) -> {
			Gadget gadget = new Gadget(k, (Method) v.get(1), null, (byte[]) v.get(0), sim);
			try {
				findChain(gadget);
			} catch (FileNotFoundException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		});
	}
	/*
	 *  must add in queue function for BFS
	 */
	public void findChain(Gadget gadget) throws FileNotFoundException, ClassNotFoundException { // depth first search
		System.out.println(gadget.getClazz().getCanonicalName());
		System.out.println(gadget.getMethod().getName());
		if (blacklist.containsKey(gadget.getClazz().getCanonicalName())) {
			String clazz = gadget.getClazz().getCanonicalName();
			if (blacklist.get(clazz).contains(gadget.getMethod().getName())) {
				returnChain(gadget);
				return; // chain found parent reference to show the chain
			}
		}
		List<MethodInfo> next = gadget.InspectMethod();
		if (next.size() == 0) {
			return;
		}
		for (MethodInfo m : next) {
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
