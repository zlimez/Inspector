package chain;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.analysis.BasicValue;

import chain.serialcheck.CheckForSerialization;
import hierarchy.BuildOrder;
import hierarchy.SortClass;
import methodsEval.MethodInfo;
import precompute.DbConnector;
import precompute.NeoVisualize;
import precompute.StoreHierarchy;
import userFields.UserFieldInterpreter;

public class Enumerate implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private URL[] urls;
	private transient Map<Class<?>, byte[]> serialClazzes;
	private transient Map<Class<?>, byte[]> allClasses;
	private transient Map<String, Map<Class<?>, byte[]>> hierarchy; // complete hierarchy
	private transient Map<Class<?>, List<Object>> entryPoints;
	private Map<String, List<String>> blacklist; // List of interesting classes with their respective interesting methods
	private transient String outputFile;
	private int maxDepth;
	private int previousDepth; 
	private transient boolean isDFS;
	private transient boolean isContinued;
	private transient LinkedList<Gadget> queue;
	private transient Map<String, List<String>> scanStart;
	private transient ArrayList<Gadget> allPotentialGadgets;
	
	private transient ArrayList<Gadget> startPoints;
	private ArrayList<Gadget> endPoints;
	
	private Map<String, Set<byte[]>> inithierarchy; //used to reinitialize hierarchy when analysis is continued 
	
	public static void main(String[] args) throws ClassNotFoundException, IOException, SQLException, InvalidInputException {
//		String pathToFile = "/home/pcadmin/Deserialization/playground/GadgetChain-sm/TESTS/commons-collections4-4.0.jar";
//		int jdkVersion = 11;
		
		try (Scanner in = new Scanner(System.in)) {
			System.out.println("Are you starting a new analysis or do you wish to continue from one of the end points reached in previous analysis? (new/continue)?");
			String isNew = in.next();
			System.out.println("Do you wish to store the end point gadget nodes of this analysis for further analysis later? (Y/N)");
			String resp = in.next();
			boolean isStore;
			if (resp.equalsIgnoreCase("Y")) {
				isStore = true;
			} else if (resp.equalsIgnoreCase("N")) {
				isStore = false;
			} else {
				throw new InvalidInputException("Invalid input");
			}
			
			Enumerate target;
			if (isNew.equalsIgnoreCase("new")) {
				System.out.println("Specify the path to the jar or war file of the application you wish to scan");
				String pathToFile = in.next();
				System.out.println("Specify the version of java the application will be running in (eg. 11)");
				int jdkVersion = in.nextInt();
				
				DbConnector.initBlacklist(jdkVersion); 
				if (pathToFile.endsWith(".war")) {
					System.out.println("Specify the path to the directory containing all the jar files of the server the war file will be running on (eg. tomcat)");
					String serverDir = in.next();
					if (isStore) {
						System.out.println("Specify the path to the directory where the war file will be unzipped into");
						String tempdir = in.next();
						target = new Enumerate(jdkVersion, Blacklist.getList(), pathToFile, serverDir, tempdir);
					} else 
						target = new Enumerate(jdkVersion, Blacklist.getList(), pathToFile, serverDir);
				} else {
					target = new Enumerate(jdkVersion, Blacklist.getList(), pathToFile);
				}
		 
				target.configCommonVars(in);
				target.isContinued = false;
//				target.maxDepth = 7;
//				target.isDFS = true;
//				target.outputFile = "/home/pcadmin/chains";
				
				System.out.println("Do you wish to select specific class:methods to start the analysis (Y/N)?");
				String choice = in.next();
				if (choice.equalsIgnoreCase("Y")) {
					target.scanStart = new Hashtable<>();
					System.out.println("Specify the path to the file you want to output the list of possible entry points for the analysis");
					String entries = in.next();
					PrintWriter writer = new PrintWriter(entries);
					target.entryPoints.forEach((k, v) -> {
						writer.print(k.getName() + ": ");
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
						Scanner scanLine = new Scanner(line);
						scanLine.useDelimiter("\\s*:\\s*|\\s*\\,\\s*");
						String clazz = scanLine.next();
						List<String> methods = new ArrayList<>();
						while (scanLine.hasNext()) {
							String method = scanLine.next();
							methods.add(method);
						}
						scanLine.close();
						target.scanStart.put(clazz, methods);
					}
					read.close();
				} else if (choice.equalsIgnoreCase("N")) {
				} else {
					throw new InvalidInputException("Invalid input");
				}
			
//		target.scanStart = new Hashtable<>();
//		List<String> methods = new ArrayList<>();
//		methods.add("readObject(Ljava/io/ObjectInputStream;)V");
//		target.scanStart.put("java.util.PriorityQueue", methods);
			
				Map<Class<?>, byte[]> dc = CheckForSerialization.deserializationOccurences(target.allClasses);
				if (dc.isEmpty()) {
					System.out.println("Warning no deserialization process in the application");
				} else {
					PrintWriter out = new PrintWriter(target.outputFile);
					out.print("Location of deserialization: ");
					dc.forEach((k, v) -> {
						out.print(k.getName() + "; ");
					});
					out.println();
					out.flush();
					out.close();
				}
				target.initAllEntry();
				target.allPotentialGadgets.removeIf(g -> !g.getVisitStatus());
				try (NeoVisualize visualize = new NeoVisualize("bolt://localhost:7687", "neo4j", "password")) {
					visualize.indexGraph();
					for (Gadget start : target.allPotentialGadgets) {
						if (start.getParent() == null) {
							visualize.genInitialNode(start);
							storeGadget(start, visualize);
			 			}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			} else if (isNew.equalsIgnoreCase("continue")) {
				System.out.println("Specify the path to the file where the results of previous analysis is stored");
				String location = in.next();
				ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(location)));
				target = (Enumerate) ois.readObject();
				ois.close();
				ClassLoader loader = new URLClassLoader(target.urls);
				target.genHierarchy(loader);
				target.startPoints.forEach(g -> {
					try {
						g.setClazz(loader);
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}
				});
				target.configCommonVars(in);
				target.isContinued = true;
				Files.createFile(Paths.get(target.outputFile));
				System.out.println("Do you wish to continue analysis from specific nodes? (Y/N)");
				String choice = in.next();
				if (choice.equalsIgnoreCase("Y")) {
					in.useDelimiter("\\n");
					System.out.println("With reference to the id of the gadget nodes stored in the neo4j datebase, list out the id of the end point gadget nodes you wish to continue the analysis from (eg. 1, 3, 99)");
					Set<Integer> startIDs = new HashSet<>();
					String ids = in.next();
					Scanner getInt = new Scanner(ids);
					getInt.useDelimiter("\\s*\\,\\s*");
					while (getInt.hasNext()) {
						startIDs.add(getInt.nextInt());
					}
					in.reset();
					getInt.close();
					target.continueAnalysis(startIDs);
				} else if (choice.equalsIgnoreCase("N")) {
					target.continueAnalysis(null);
				} else {
					throw new InvalidInputException("Invalid input");
				}
				try (NeoVisualize visualize = new NeoVisualize("bolt://localhost:7687", "neo4j", "password")) {
					visualize.initializeID();
					for (Gadget start : target.allPotentialGadgets) {
						if (start.getDepth() == target.previousDepth) {
							storeGadget(start, visualize);
			 			}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				throw new InvalidInputException("Invalid input");
			}
			
			if (isStore) {
				System.out.println("Specify the path to the file you wish to store this analysis instance");
				String dest = in.next();
				ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(dest)));
				oos.writeObject(target);
				oos.close();
			} 
		}
	}
	
	private void configCommonVars(Scanner in) throws InvalidInputException {
		System.out.println("Specify the path to the file you wish to store the scan results");
		this.outputFile = in.next();
		System.out.println("Specify the maximum length of the gadget chain you wish to find");
		this.maxDepth = in.nextInt() + this.previousDepth;
		System.out.println("Specify the search algorithm to employ (BFS/DFS)");
		String searchType = in.next();
		if (searchType.equalsIgnoreCase("BFS")) {
			this.isDFS = false;
			this.queue = new LinkedList<Gadget>();
		} else if (searchType.equalsIgnoreCase("DFS")) {
			this.isDFS = true;
		} else {
			throw new InvalidInputException("Invalid search algorithm");
		}
	}
	
	public Enumerate(int jdkVersion, Map<String, List<String>> blacklist, String ... pathToFile) throws ClassNotFoundException, IOException, SQLException {
		SortClass sort = new SortClass(pathToFile);
		List<Map<Class<?>, byte[]>> all = sort.getSerialAndAllClasses();
		urls = sort.getUrls();
		serialClazzes = all.get(0);
		allClasses = all.get(1);
		Map<String, Map<Class<?>, byte[]>> fileHierarchy = BuildOrder.computeHierarchy(allClasses, serialClazzes);
		StoreHierarchy env = DbConnector.getSystemInfo(jdkVersion);
		hierarchy = BuildOrder.combineHierarchies(serialClazzes, env.getHierarchy(), fileHierarchy);
		inithierarchy = new HashMap<>();
		hierarchy.forEach((classname, subclasses) -> {
			Set<byte[]> subset = new HashSet<>();
			subclasses.forEach((clazz, bytes) -> {
				subset.add(bytes);
			});
			inithierarchy.put(classname, subset);
		});
		entryPoints = Entry.EntryPoint(serialClazzes);
		entryPoints.putAll(env.getEntryPoints());
		this.blacklist = blacklist;
		endPoints = new ArrayList<>();
		allPotentialGadgets = new ArrayList<>();
	}
	
	public void continueAnalysis(Set<Integer> startIDs) {
		if (startIDs == null) {
			startPoints.forEach(g -> {
				try {
					if (isDFS) 
						findChain(g);
					else {
						queue.add(g);
					}
				} catch (IOException | ClassNotFoundException e) {
					e.printStackTrace();
				}
			});
		} else {
			List<Gadget> filtered = startPoints.stream().filter(gadget -> startIDs.contains(gadget.getId())).collect(Collectors.toList());
			filtered.forEach(g -> {
				try {
					if (isDFS) 
						findChain(g);
					else {
						queue.add(g);
					}
				} catch (IOException | ClassNotFoundException e) {
					e.printStackTrace();
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
			Map<Class<?>, List<Object>> filtered = entryPoints.entrySet().stream().filter(c -> scanStart.containsKey(c.getKey().getName())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			filtered.forEach((k, v) -> {
				for (int i = 1; i < v.size(); i++) {
					Map<Integer, BasicValue> sim = new Hashtable<Integer, BasicValue>();
					MethodInfo mf = (MethodInfo) v.get(i);
					String signature = mf.getName() + mf.getDesc();
					if (scanStart.get(k.getName()).contains(signature)) {
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
				System.out.println("The method " + gadget.getMethodDesc() + " does not exist in " + gadget.getClazz().getName());
			} else if (blacklist.containsKey(gadget.getName()) || (gadget.getClazz() != null && blacklist.containsKey(gadget.getClazz().getName()))) {
				String clazz;
				if (gadget.getClazz() == null) {
					clazz = gadget.getName();
				} else {
					clazz = gadget.getClazz().getName();
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
				System.out.println("The class " + gadget.getClazz().getName() + " is not found hence terminated path");
			} else if (gadget.getDepth() >= maxDepth) {
				System.out.println("Max recursion depth reached");
				cleanUpCallTree(gadget, null);
				gadget.storeArgVals();
				endPoints.add(gadget);
			} else {
				List<MethodInfo> next = gadget.InspectMethod();
				
				if (next.size() == 0) {
					System.out.println("No next method found");
				}
				
				for (MethodInfo m : next) {
					List<Gadget> children = gadget.findChildren(m, hierarchy);
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
			System.out.println("The method " + gadget.getMethodDesc() + " does not exist in " + gadget.getClazz().getName());
			return;
		}
		
		if (blacklist.containsKey(gadget.getName()) || (gadget.getClazz() != null && blacklist.containsKey(gadget.getClazz().getName()))) {
			String clazz;
			if (gadget.getClazz() == null) {
				clazz = gadget.getName();
			} else {
				clazz = gadget.getClazz().getName();
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
			System.out.println("The class " + gadget.getClazz().getName() + " is not found hence terminated path");
			return; // when class is not included in search space
		}

		if (gadget.getDepth() >= maxDepth) {
			System.out.println("Max recursion depth reached");
			cleanUpCallTree(gadget, null);
			gadget.storeArgVals();
			endPoints.add(gadget);
			return; // prevent excessive recursion
		}
		
		List<MethodInfo> next = gadget.InspectMethod();
		
		if (next.size() == 0) {
			System.out.println("No next method found");
			return;
		} // no next method found to be invoked
		
		for (MethodInfo m : next) {
			List<Gadget> children = gadget.findChildren(m, hierarchy);
			for (Gadget child : children) {
				findChain(child);
			}
		}
	}
	
	// write the result to a file
	public void returnChain(Gadget gadget, PrintWriter out) throws IOException {
		String classname = gadget.getClazz().getName(); // inverse chain printed
		String methodname = gadget.getMethod().getName();
		out.println(classname + ":" + methodname);
		Gadget parent = gadget.getParent();
		if (parent == null) {
			if (isContinued) 
				out.println("Please refer to neo4j db for entire chain construct");
			out.println("End of chain");
			out.flush();
			out.close();
			return;
		}
		returnChain(parent, out);
	}
	
	public void cleanUpCallTree(Gadget gadget, Gadget revisedChild) { // remove all gadgets that cannot potentially or is a already a part of a gadget chain
		gadget.addRevisedChild(revisedChild);
		if (gadget.getVisitStatus()) {
			return;
		}
		gadget.visited();
		Gadget parent = gadget.getParent();
		if (parent == null)
			return;
		cleanUpCallTree(parent, gadget);
	}
	
	// method to enumerate all valid paths referencing allPotentialGadgets
	public static void storeGadget(Gadget parent, NeoVisualize visualize) {
		List<Gadget> revisedChildren = parent.getRevisedChildren();
		if (revisedChildren.isEmpty()) {
			return;
		}
		visualize.genCallGraphFromThisNode(parent);

		for (Gadget child : revisedChildren) {
			storeGadget(child, visualize);
		}
	}
	
	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		ois.defaultReadObject();
		this.startPoints = this.endPoints;
		this.endPoints = new ArrayList<>();
		this.previousDepth = this.maxDepth;
		this.allPotentialGadgets = new ArrayList<>();
	}
	
	private void genHierarchy(ClassLoader loader) {
		hierarchy = new HashMap<>();
		inithierarchy.forEach((classname, subclasses) -> {
			Map<Class<?>, byte[]> subset = new HashMap<>();
			subclasses.forEach(bytes -> {
				ClassReader cr = new ClassReader(bytes);
				String name = cr.getClassName();
				String outputStr = name.replaceAll("/", "\\.");
				try {
					Class<?> clazz = Class.forName(outputStr, false, loader);
					subset.put(clazz, bytes);
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
			});
			hierarchy.put(classname, subset);
		});
	}
	
	private static class InvalidInputException extends Exception {
		private static final long serialVersionUID = 1L;

		public InvalidInputException(String message) {
			super(message);
		}
	}
}
