package chain;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

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
	protected String outputFile;
	private static int maxDepth;
	private static boolean isDFS;
	private static LinkedList<Gadget> queue;
	private static Map<String, List<String>> scanStart;
	private static ArrayList<Gadget> allPotentialGadgets = new ArrayList<>();
	
	public static void main(String[] args) throws ClassNotFoundException, IOException, SQLException, InvalidInputException {
		try (Scanner in = new Scanner(System.in)) {
			System.out.println("Specify the path to the jar or war file of the application you wish to scan");
			String pathToFile = in.next();
			System.out.println("Specify the version of java the application will be running in (eg. 11)");
			int jdkVersion = in.nextInt();
			System.out.println("Specify the path to the file you wish to store the scan results");
			String resultFile = in.next();
			System.out.println("Specify the maximum length of the gadget chain you wish to find");
			maxDepth = in.nextInt();
			System.out.println("Specify the search algorithm to employ (BFS/DFS)");
			String searchType = in.next();
			if (searchType.equalsIgnoreCase("BFS")) {
				isDFS = false;
				queue = new LinkedList<Gadget>();
			} else if (searchType.equalsIgnoreCase("DFS")) {
				isDFS = true;
			} else {
				throw new InvalidInputException("Invalid search algorithm");
			}
			
			DbConnector.initBlacklist(jdkVersion); 
			Enumerate target = new Enumerate(jdkVersion, pathToFile, Blacklist.getList(), resultFile); 
			
			System.out.println("Do you wish to select specific class:methods to start the analysis (Y/N)?");
			String choice = in.next();
			if (choice.equalsIgnoreCase("Y")) {
				scanStart = new Hashtable<>();
				System.out.println("Specify the path to the file you want to output the list of possible entry points for the analysis");
				String entries = in.next();
				PrintWriter writer = new PrintWriter(entries);
				entryPoints.forEach((k, v) -> {
					writer.print(k.getCanonicalName() + ": ");
					for (int i = 1; i < v.size(); i++) {
						MethodInfo mf = (MethodInfo) v.get(i);
						writer.print(mf.getName() + mf.getDesc() + ", ");
					}
					writer.println();
				});
				writer.flush();
				writer.close();
				System.out.println("Specify the path to the file containing the class:methods (Each class should be written in their canonical name followed by a :, and its methods separated by a , including both the method name and descriptor eg. readObject(Ljava/io/ObjectInputStream;)V");
				String file = in.next();
				Scanner read = new Scanner(new FileInputStream(file));
				while (read.hasNextLine()) {
					String line = read.nextLine();
					System.out.println(line);
					Scanner scanLine = new Scanner(line);
					scanLine.useDelimiter(":\\s*|\\,\\s*");
					String clazz = scanLine.next();
					System.out.println(clazz);
					List<String> methods = new ArrayList<>();
					while (scanLine.hasNext()) {
						String method = scanLine.next();
						System.out.println(method);
						methods.add(method);
					}
					scanLine.close();
					scanStart.put(clazz, methods);
				}
				read.close();
			} else if (choice.equalsIgnoreCase("N")) {
			} else {
				throw new InvalidInputException("Invalid input");
			}
			
			Map<Class<?>, byte[]> dc = CheckForSerialization.deserializationOccurences(allClasses);
			if (dc.isEmpty()) {
				System.out.println("Warning no deserialization process in the application");
			} else {
				PrintWriter out = new PrintWriter(target.outputFile);
				out.print("Location of deserialization: ");
				dc.forEach((k, v) -> {
					out.print(k.getCanonicalName() + "; ");
				});
				out.println();
				out.flush();
				out.close();
			}
			target.initAllEntry();
			allPotentialGadgets.removeIf(g -> !g.getVisitStatus());
//			for (Gadget start : allPotentialGadgets) {
//				if (start.getParent() == null) {
//					storeGadget(start);
//	 			}
//			}
		}
	}
	
	public Enumerate(int jdkVersion, String pathToFile, Map<String, List<String>> blacklist, String outputFile) throws ClassNotFoundException, IOException, SQLException {
		SortClass sort = new SortClass(pathToFile);
		List<Map<Class<?>, byte[]>> all = sort.getSerialAndAllClasses();
		serialClazzes = all.get(0);
		allClasses = all.get(1);
		Map<String, Map<Class<?>, byte[]>> fileHierarchy = BuildOrder.computeHierarchy(allClasses, serialClazzes);
		StoreHierarchy env = DbConnector.getSystemInfo(jdkVersion);
		hierarchy = BuildOrder.combineHierarchies(serialClazzes, env.getHierarchy(), fileHierarchy);
		entryPoints = Entry.EntryPoint(serialClazzes);
		entryPoints.putAll(env.getEntryPoints());
		Enumerate.blacklist = blacklist;
		this.outputFile = outputFile;
	}
	
	public void initAllEntry() {
		if (scanStart == null) {
			entryPoints.forEach((k, v) -> { // account for more than one magic method per class
				for (int i = 1; i < v.size(); i++) {
					Map<Integer, BasicValue> sim = new Hashtable<Integer, BasicValue>();
					MethodInfo mf = (MethodInfo) v.get(i);
					if (!mf.getMethodType()) {
						sim.put(0, UserFieldInterpreter.USER_DERIVED);
					}
					int argLength = mf.getParamCount();
					for (int j = 0; j < argLength; j++) {
						sim.put(j + 1, UserFieldInterpreter.USER_INFLUENCED);
					}
					Gadget gadget = new Gadget(k, mf, null, (byte[]) v.get(0), sim, 1);
					try {
						if (isDFS) 
						findChain(gadget);
						else {
							queue.addLast(gadget);
						}
					} catch (IOException | ClassNotFoundException e) {
						e.printStackTrace();
					}
				}
			});
		} else {
			entryPoints.forEach((k, v) -> { // account for more than one magic method per class
				String clazz = k.getCanonicalName();
				if (scanStart.containsKey(clazz)) {
					for (int i = 1; i < v.size(); i++) {
						Map<Integer, BasicValue> sim = new Hashtable<Integer, BasicValue>();
						MethodInfo mf = (MethodInfo) v.get(i);
						String signature = mf.getName() + mf.getDesc();
						if (scanStart.get(clazz).contains(signature)) {
							if (!mf.getMethodType()) {
								sim.put(0, UserFieldInterpreter.USER_DERIVED);
							}
							int argLength = mf.getParamCount();
							for (int j = 0; j < argLength; j++) {
								sim.put(j + 1, UserFieldInterpreter.USER_INFLUENCED);
							}
							Gadget gadget = new Gadget(k, mf, null, (byte[]) v.get(0), sim, 1);
							try {
								if (isDFS) 
									findChain(gadget);
								else {
									queue.addLast(gadget);
								}
							} catch (IOException | ClassNotFoundException e) {
								e.printStackTrace();
							}
						}
					}
				}
			});
		}

		try {
			if (!isDFS)
			findChainByBFS();
		} catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
		}
	}

		
	public void findChainByBFS() throws ClassNotFoundException, IOException {
		while (queue.size() > 0) {
			Gadget gadget = queue.getFirst();
			allPotentialGadgets.add(gadget);
			queue.removeFirst();
			if (gadget.getMethod() == null) {
				System.out.println("The method " + gadget.getMethodDesc() + " does not exist in " + gadget.getClazz().getCanonicalName());
			} else if (blacklist.containsKey(gadget.getName()) || (gadget.getClazz() != null && blacklist.containsKey(gadget.getClazz().getCanonicalName()))) {
				String clazz;
				if (gadget.getClazz() == null) {
					clazz = gadget.getName();
				} else {
					clazz = gadget.getClazz().getCanonicalName();
				}
				MethodInfo method = gadget.getMethod();
				if (blacklist.get(clazz).contains(method.getName() + ":" + method.getDesc())) {
					PrintWriter out = new PrintWriter(new FileWriter(this.outputFile, true));
					returnChain(gadget, out);
					cleanUpCallTree(gadget, null);
				}
			} else if (gadget.getClazz() == null) {
				System.out.println("The class " + gadget.getName() + " is not found as it is not serializable ");
			} else if (gadget.getBytes() == null) {
				System.out.println("The class " + gadget.getClazz().getCanonicalName() + " is not found hence terminated path");
			} else if (gadget.getDepth() >= maxDepth) {
				System.out.println("Max recursion depth reached");
				cleanUpCallTree(gadget, null);
			} else {
				List<MethodInfo> next = gadget.InspectMethod();
				
				if (next.size() == 0) {
					System.out.println("No next method found");
				}
				
				for (MethodInfo m : next) {
					List<Gadget> children = gadget.findChildren(m);
					for (Gadget child : children) {
						queue.addLast(child);
					}
				}
			}
		}
	}
	
	public void findChain(Gadget gadget) throws ClassNotFoundException, IOException { // depth first search
		allPotentialGadgets.add(gadget);

		if (gadget.getMethod() == null) {
			System.out.println("The method " + gadget.getMethodDesc() + " does not exist in " + gadget.getClazz().getCanonicalName());
			return;
		}
		
		if (blacklist.containsKey(gadget.getName()) || (gadget.getClazz() != null && blacklist.containsKey(gadget.getClazz().getCanonicalName()))) {
			String clazz;
			if (gadget.getClazz() == null) {
				clazz = gadget.getName();
			} else {
				clazz = gadget.getClazz().getCanonicalName();
			}
			MethodInfo method = gadget.getMethod();
			if (blacklist.get(clazz).contains(method.getName() + ":" + method.getDesc())) {
				PrintWriter out = new PrintWriter(new FileWriter(this.outputFile, true));
				returnChain(gadget, out);
				cleanUpCallTree(gadget, null);
				return; // chain found parent reference to show the chain
			}
		}
		
		if (gadget.getClazz() == null) {
			System.out.println("The class " + gadget.getName() + " is not found as it is not serializable ");
			return;
		}
		
		if (gadget.getBytes() == null) {
			System.out.println("The class " + gadget.getClazz().getCanonicalName() + " is not found hence terminated path");
			return; // when class is not included in search space
		}

		if (gadget.getDepth() >= maxDepth) {
			System.out.println("Max recursion depth reached");
			cleanUpCallTree(gadget, null);
			return; // prevent excessive recursion
		}
		
		List<MethodInfo> next = gadget.InspectMethod();
		
		if (next.size() == 0) {
			System.out.println("No next method found");
			return;
		} // no next method found to be invoked
		
		for (MethodInfo m : next) {
			List<Gadget> children = gadget.findChildren(m);
			for (Gadget child : children) {
				findChain(child);
			}
		}
	}
	
	// write the result to a file
	public void returnChain(Gadget gadget, PrintWriter out) throws IOException {
		String classname = gadget.getClazz().getCanonicalName(); // inverse chain printed
		String methodname = gadget.getMethod().getName();
		out.println(classname + ":" + methodname);
		Gadget parent = gadget.getParent();
		if (parent == null) {
			out.println("End of chain");
			out.flush();
			out.close();
			return;
		}
		returnChain(parent, out);
	}
	
	public void cleanUpCallTree(Gadget gadget, Gadget revisedChild) { // remove all gadgets that cannot potentially or is a already a part of a gadget chain
		gadget.visited(revisedChild);
		Gadget parent = gadget.getParent();
		if (parent == null)
			return;
		cleanUpCallTree(parent, gadget);
	}
	
	// method to enumerate all valid paths referencing allPotentialGadgets
//	public static void storeGadget(Gadget parent) {
		// store parent
//		List<Gadget> revisedChildren = parent.getRevisedChildren();
//		for (Gadget child : revisedChildren) {
//			storeGadget(child);
//		}
//	}
	
	private static class InvalidInputException extends Exception {
		private static final long serialVersionUID = 1L;

		public InvalidInputException(String message) {
			super(message);
		}
	}
}
