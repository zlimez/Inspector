package hierarchy;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import precompute.ReadSystem;

public class DependencyTree {
	public static Map<String, Clazz> allClazzes = new HashMap<>();
	
	public static class Clazz {
		private String classname;
		private boolean resolvable;
		private Set<Clazz> dependentClasses;
		
		public Clazz(String classname) {
			this.classname = classname;
			this.resolvable = true;
			this.dependentClasses = new HashSet<>();
		}
		
		public Set<String> allTransitiveDependents(int option) throws IOException {
			Set<String> all = new HashSet<>();
			all.add(classname);
			resolvable = false;
			LinkedList<Clazz> queue = new LinkedList<>();
			queue.add(this);
			
			while (!queue.isEmpty()) {
				Clazz first = queue.pollFirst();
				for (Clazz c : first.dependentClasses) {
					if (c.resolvable) {
						queue.add(c);
						c.resolvable = false;
						all.add(c.classname);
					}
				}
			}
			return all;
		}
	}
	
	// output classes that will cause a NoClassDefFoundError
	public static Set<String> resolveDependencies(String[] pathToFile) throws IOException {
		StringBuffer sb = new StringBuffer("jdeps -v ");
		for (String path : pathToFile) {
			sb.append("\"" + path + "\"" + " ");
		}

		ProcessBuilder pb = new ProcessBuilder();
		if (ReadSystem.isUnix) {
			pb.command("bash","-c", sb.toString());
		} else 
			pb.command("CMD", "/C", sb.toString());
		Process process = pb.start();

		Set<String> NotDefinedClass = new HashSet<String>();
		try (Scanner in = new Scanner(new BufferedInputStream(process.getInputStream()))) {
			boolean removed = false;
			String classname = "";
			while (in.hasNextLine()) {
				String line = in.nextLine();
				if (line.startsWith(" ")) {
					Scanner inline = new Scanner(line);
					inline.useDelimiter("\\s+(->)*\\s+");				
					String temp = inline.next();
					if (!temp.equals(classname)) {
						removed = false;
						classname = temp;
					}
					if (!removed) {
						String dependency = inline.next();
						
						if (!dependency.startsWith("java.")) {
							Clazz thisClass;
							if (!allClazzes.containsKey(classname)) {
								thisClass = new Clazz(classname);
								allClazzes.put(classname, thisClass);
							} else 
								thisClass = allClazzes.get(classname);
							
							boolean notFound = line.contains("not found")? true : false;
							
							if (!notFound) {
								if (!allClazzes.containsKey(dependency)) {
									Clazz dep = new Clazz(dependency);
									allClazzes.put(dependency, dep);
									dep.dependentClasses.add(thisClass);
								} else {
									Clazz dep = allClazzes.get(dependency);
									dep.dependentClasses.add(thisClass);
									if (!dep.resolvable) {	
										NotDefinedClass.addAll(thisClass.allTransitiveDependents(1));
										removed = true;
									}
								}
							} else {
								removed = true;
								NotDefinedClass.addAll(thisClass.allTransitiveDependents(2));
							}
						}
					}
					inline.close();
				}
			}
		}

		return NotDefinedClass;
	}
}
