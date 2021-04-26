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
import java.util.Collection;
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

import chain.serialcheck.CheckForSerialization;
import hierarchy.BuildOrder;
import hierarchy.SortClass;
import methodsEval.MethodInfo;
import precompute.DbConnector;
import precompute.NeoVisualize;
import precompute.StoreHierarchy;

public class Enumerate implements Serializable {
	private static final long serialVersionUID = 1L;
	static int count = 0;
	
	private URL[] urls;
	private transient Map<Class<?>, byte[]> serialClazzes;
	private transient Map<Class<?>, byte[]> allClasses;
	private transient Map<String, Map<Class<?>, byte[]>> hierarchy; // complete hierarchy
	private transient Map<Class<?>, List<Object>> entryPoints;
	private Map<String, Set<String>> magicMethods;
	private Map<String, List<String>> blacklist; // List of interesting classes with their respective interesting methods
	private transient String outputFile;
	private int maxDepth;
	private int previousDepth; 
	private transient boolean isContinued;
	private transient LinkedList<Gadget> queue;
	private transient Map<String, List<String>> scanStart;
	private transient Set<Gadget> allPotentialGadgets;
	
	private transient ArrayList<Gadget> startPoints;
	private ArrayList<Gadget> endPoints;
	
	private Map<String, Set<byte[]>> inithierarchy; //used to reinitialize hierarchy when analysis is continued 
	
	public static void startScan() throws ClassNotFoundException, IOException, SQLException, InvalidInputException {
		try (Scanner in = new Scanner(System.in)) {
//			System.out.println("Are you starting a new analysis or do you wish to continue from one of the end points reached in previous analysis? (new/continue)?");
//			String isNew = in.next();
			String isNew = "new";
//			System.out.println("Do you wish to store the end point gadget nodes of this analysis for further analysis later? (Y/N)");
//			String resp = in.next();
			boolean isStore = false;
//			if (resp.equalsIgnoreCase("Y")) {
//				isStore = true;
//			} else if (resp.equalsIgnoreCase("N")) {
//				isStore = false;
//			} else {
//				throw new InvalidInputException("Invalid input");
//			}
			
			Enumerate target;
			if (isNew.equalsIgnoreCase("new")) {
//				in.useDelimiter("\\n");
//				System.out.println("Specify the paths to the jar files or path to the war file of the application you wish to scan (for paths to jar files shoudl be formatted as such {path1}, {path2}, ...");
//				String files = in.next();
//				Scanner divider = new Scanner(files);
//				List<String> paths = new ArrayList<>();
//				divider.useDelimiter("\\s*\\,\\s*");
//				while (divider.hasNext()) {
//					String pathToFile = divider.next();
//					paths.add(pathToFile);
//				}
//				divider.close();
//				String[] pathsToFiles = paths.toArray(new String[paths.size()]);
//				in.reset();
//				System.out.println("Specify the version of java the application will be running in (eg. 11)");
//				int jdkVersion = in.nextInt();
				
				int jdkVersion = 11;
				String[] pathsToFiles = new String[] {"/home/pcadmin/Deserialization/playground/GadgetChain-sm/TESTS/spring-core-5.3.6.jar", "/home/pcadmin/Deserialization/playground/GadgetChain-sm/TESTS/spring-jcl-5.3.6.jar"};
				
				DbConnector.initBlacklist(jdkVersion); 
				if (pathsToFiles[0].endsWith(".war")) {
					System.out.println("Specify the path to the directory containing all the jar files of the server the war file will be running on (eg. tomcat)");
					String serverDir = in.next();
					if (isStore) {
						System.out.println("Specify the path to the directory where the war file will be unzipped into");
						String tempdir = in.next();
						target = new Enumerate(jdkVersion, Blacklist.getList(), pathsToFiles, serverDir, tempdir);
					} else 
						target = new Enumerate(jdkVersion, Blacklist.getList(), pathsToFiles, serverDir);
				} else {
					target = new Enumerate(jdkVersion, Blacklist.getList(), pathsToFiles);
				}
		 
				target.configCommonVars(in);
				target.isContinued = false;
				
//				System.out.println("Do you wish to select specific class:methods to start the analysis (Y/N)?");
//				String choice = in.next();
//				if (choice.equalsIgnoreCase("Y")) {
					target.scanStart = new Hashtable<>();
//					System.out.println("Specify the path to the file you want to output the list of possible entry points for the analysis");
//					String entries = in.next();
//					PrintWriter writer = new PrintWriter(entries);
//					target.entryPoints.forEach((k, v) -> {
//						writer.print(k.getName() + ": ");
//						for (int i = 1; i < v.size(); i++) {
//							MethodInfo mf = (MethodInfo) v.get(i);
//							writer.print(mf.getName() + mf.getDesc() + ", ");
//						}
//						writer.println();
//					});
//					writer.flush();
//					writer.close();
//					System.out.println("Specify the path to the file containing the class:methods (Each class should be written in their canonical name followed by a :, and its methods separated by a , including both the method name and descriptor eg. readObject(Ljava/io/ObjectInputStream;)V");
//					String file = in.next();
				
					String file = "/home/pcadmin/entry";
				
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
//					read.close();
//				} else if (choice.equalsIgnoreCase("N")) {
//				} else {
//					throw new InvalidInputException("Invalid input");
//				}
			
				Map<Class<?>, byte[]> dc = CheckForSerialization.deserializationOccurences(target.allClasses);
				PrintWriter out = new PrintWriter(target.outputFile);
				if (dc.isEmpty()) {
					out.println("Warning no deserialization process in the application");
				} else {
					out.print("Location of deserialization: ");
					dc.forEach((k, v) -> {
						out.print(k.getName() + "; ");
					});
					out.println();
					out.flush();
				}
				out.close();
				target.initAllEntry();
				target.allPotentialGadgets.removeIf(g -> !g.getVisitStatus());
//				System.out.println(target.allPotentialGadgets.size());
//				System.out.println(count);
				in.useDelimiter("\\n");
				System.out.println("Assuming you have neo4j desktop installed, please provide the port your DBMS is running on, your username and password in this format bolt://localhost:{portNum}, {username}, {password}");
				String dbInfo = in.next();
				Scanner parse = new Scanner(dbInfo);
				parse.useDelimiter("\\s*\\,\\s*");
				String port = parse.next();
				String user = parse.next();
				String pw = parse.next();
				parse.close();
				in.reset();
				try (NeoVisualize visualize = new NeoVisualize(port, user, pw)) {
					visualize.indexGraph();
					for (Gadget start : target.allPotentialGadgets) {
						if (start.getParents().get(0) == null) {
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
				in.useDelimiter("\\n");
				System.out.println("Assuming you have neo4j desktop installed, please provide the port your DBMS is running on, your username and password in this format bolt://localhost:{portNum}, {username}, {password}");
				String dbInfo = in.next();
				Scanner parse = new Scanner(dbInfo);
				parse.useDelimiter("\\s*\\,\\s*");
				String port = parse.next();
				String user = parse.next();
				String pw = parse.next();
				parse.close();
				in.reset();
				try (NeoVisualize visualize = new NeoVisualize(port, user, pw)) {
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
	}
	
	public Enumerate(int jdkVersion, Map<String, List<String>> blacklist, String[] pathsToFiles, String ... serverTemp) throws ClassNotFoundException, IOException, SQLException {
		SortClass sort = new SortClass(pathsToFiles, serverTemp);
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
		magicMethods = new HashMap<>();
		entryPoints.forEach((c, l) -> {
			String classname = c.getName();
			Set<String> methodID = new HashSet<>();
			for (int i = 1; i < l.size(); i++) {
				MethodInfo mf = (MethodInfo) l.get(i);
				methodID.add(mf.getName() + mf.getDesc());
			}
			magicMethods.put(classname, methodID);
		});
		
		this.blacklist = blacklist;
		endPoints = new ArrayList<>();
		allPotentialGadgets = new HashSet<>();
		queue = new LinkedList<>();
	}
	
	public void continueAnalysis(Set<Integer> startIDs) {
		if (startIDs == null) {
			queue.addAll(startPoints);
		} else {
			List<Gadget> filtered = startPoints.stream().filter(gadget -> startIDs.contains(gadget.getId())).collect(Collectors.toList());
			queue.addAll(filtered); 
		}
		
		try {
			findChainByBFS();
		} catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
		}
	}
	
	public void initAllEntry() {
		if (scanStart == null) {
			entryPoints.forEach((k, v) -> { // account for more than one magic method per class
				for (int i = 1; i < v.size(); i++) {
					MethodInfo mf = (MethodInfo) v.get(i);
					Gadget gadget = new Gadget(k.getName(), k, mf, null, (byte[]) v.get(0), mf.getUserControlledArgPos(), 1);
					queue.addLast(gadget);
				}
			});
		} else {
			Map<Class<?>, List<Object>> filtered = entryPoints.entrySet().stream().filter(c -> scanStart.containsKey(c.getKey().getName())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			filtered.forEach((k, v) -> {
				for (int i = 1; i < v.size(); i++) {
					MethodInfo mf = (MethodInfo) v.get(i);
					String signature = mf.getName() + mf.getDesc();
					if (scanStart.get(k.getName()).contains(signature)) {
						Gadget gadget = new Gadget(k.getName(), k, mf, null, (byte[]) v.get(0), mf.getUserControlledArgPos(), 1);
						queue.addLast(gadget);
					}
				}
			});
		}

		try {
			findChainByBFS();
		} catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
		}
	}

		
	public void findChainByBFS() throws ClassNotFoundException, IOException {
		ArrayList<Gadget> sinks = new ArrayList<>();
		while (queue.size() > 0) {
			count++;
			Gadget gadget = queue.getFirst();
			allPotentialGadgets.add(gadget);
			queue.removeFirst();
			if (blacklist.containsKey(gadget.getName()) || (gadget.getClazz() != null && blacklist.containsKey(gadget.getClazz().getName()))) {
				String clazz;
				if (gadget.getClazz() == null) {
					clazz = gadget.getName();
				} else 
					clazz = gadget.getClazz().getName();
				
				MethodInfo method = gadget.getMethod();
				if (blacklist.get(clazz).contains(method.getName() + ":" + method.getDesc())) 
					sinks.add(gadget);
			} else if (gadget.getClazz() == null) {
				System.out.println("The class " + gadget.getName() + " is not found as it is not serializable ");
			} else if (gadget.getBytes() == null) {
				System.out.println("The class " + gadget.getClazz().getName() + " is not found hence terminated path");
			} else if (gadget.getDepth() >= maxDepth) {
				System.out.println("Max recursion depth reached");
				endPoints.add(gadget);
			} else {
				Collection<MethodInfo> next = gadget.InspectMethod(magicMethods);	
				if (next.size() == 0) 
					System.out.println("No next method found");
				
				for (MethodInfo m : next) {
					List<Gadget> children = gadget.findChildren(m, hierarchy);
					for (Gadget child : children) {
						queue.addLast(child);
					}
				}
			}
		}
		
		System.out.println(Gadget.allGadgets.size() + " " + sinks.size());
		//output all chains to outputFile
		try (PrintWriter out = new PrintWriter(new FileWriter(this.outputFile, true))) {
			sinks.forEach(sink -> {
				LinkedList<LinkedList<Gadget>> subChains = new LinkedList<>();
				LinkedList<Gadget> subChain = new LinkedList<Gadget>();
				subChain.addFirst(sink);
				subChains.addFirst(subChain);
				try {
					returnChain(out, subChains);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		}
		
		endPoints.forEach(end -> {
			cleanUpCallTree(end, null);
		});
	}
	
	public void returnChain(PrintWriter out, LinkedList<LinkedList<Gadget>> subChains) throws IOException {
		while (!subChains.isEmpty()) {
			LinkedList<Gadget> subChain = subChains.getLast();
			List<Gadget> parents = subChain.getFirst().getParents();
			if (parents.get(0) == null) { //reached entry point can print chain
				if (isContinued)
					out.println("... Please refer to neo4j database for the full chain");
				subChain.forEach(g -> {
					out.println(g.getClazz().getName() + ":" + g.getMethod().getName());
				});
				out.println();
				subChains.removeLast();
			} else if (subChain.size() >= 7) {
				subChains.removeLast();
			} else {
				if (parents.size() == 1) {		
					subChain.addFirst(parents.get(0));
				} else {
					for (int i = 1; i < parents.size(); i++) {
						Gadget parent = parents.get(i);
						if (!subChain.contains(parent)) {
							LinkedList<Gadget> nextSubChains = new LinkedList<>(subChain);
							nextSubChains.addFirst(parent);
							subChains.addLast(nextSubChains);
						}	
					}
					subChain.addFirst(parents.get(0));
				}
			}
		}
	}
	
	// remove all gadgets that cannot potentially or is a already a part of a gadget chain
	public void cleanUpCallTree(Gadget gadget, Gadget revisedChild) { 
		gadget.addRevisedChild(revisedChild);
		if (gadget.getVisitStatus()) 
			return;
		gadget.visited();
		List<Gadget> parents = gadget.getParents();
		if (parents.get(0) == null)
			return;
		for (Gadget parent : parents) {
			cleanUpCallTree(parent, gadget);
		}
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
		this.allPotentialGadgets = new HashSet<>();
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
	
	public static class InvalidInputException extends Exception {
		private static final long serialVersionUID = 1L;

		public InvalidInputException(String message) {
			super(message);
		}
	}
}
