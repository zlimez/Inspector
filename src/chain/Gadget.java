package chain;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.analysis.BasicValue;

import methodsEval.MethodInfo;
import methodsEval.RenderClass;
import methodsEval.RenderMethod;
import userFields.RenderConstructorAndStream;
import userFields.UserFieldInterpreter;
import userFields.UserFieldInterpreter.ArrayValue;
import userFields.UserFieldInterpreter.MultiDArray;

public class Gadget implements Serializable {
	private static final long serialVersionUID = 1L;
	private transient Gadget parent;
	private transient Class<?> clazz;
	private MethodInfo method;
	private List<Gadget> children;
	private byte[] byteContent;
	private transient Map<Integer, BasicValue> userControlledArgPos;
	private int depth;
	
	private int id; // for reference during neo4j 
	private List<Gadget> revisedChildren = new ArrayList<Gadget>(); // for gadgets within a potential or valid gadget where some child path are invalid and should be removed to facilitate iterative analysis from end points
	private boolean visited = false;
	
	public String classname; // used only when class cannot be found correspond to 2nd constructor
	public String methodDesc; // used only when method cannot be found correspond to third constructor
	
	private Map<Integer, String> argVals; // to reinitialize userControlledArgPos as BasicValue is not serializable
	
	public Gadget(Class<?> type, MethodInfo m, Gadget parent, byte[] b, Map<Integer, BasicValue> userControlledArgPos, int depth) {
		this.clazz = type;
		this.method = m;
		this.parent = parent;
		this.children = new ArrayList<Gadget>();
		this.byteContent = b;
		this.userControlledArgPos = userControlledArgPos;
		this.depth = depth;
	}
	
	public Gadget(String classname, MethodInfo m, Gadget parent, int depth) {
		this.classname = classname;
		this.method = m;
		this.parent = parent;
		this.depth = depth;
	} 
	
	public Gadget(Class<?> type, String methodDesc, Gadget parent, int depth) {
		this.clazz = type;
		this.methodDesc = methodDesc;
		this.parent = parent;
		this.depth = depth;
	}
	
	public Set<MethodInfo> InspectMethod(Map<String, Map<String, String>> magicMethods) {
		ClassReader cr = new ClassReader(byteContent);
		String owner = cr.getClassName();
		ClassWriter cw = new ClassWriter(cr, 0);
		String cName = owner.replaceAll("/", "\\."); 
		if (magicMethods.containsKey(cName) && method.getIsField()) {
			RenderConstructorAndStream rcs = new RenderConstructorAndStream(cw, owner, magicMethods.get(cName));
			cr.accept(rcs, 0);
			Set<MethodInfo> nextMethods = new HashSet<>();
			nextMethods.addAll(rcs.getNextInvokedMethods());
			RenderMethod rm = new RenderMethod(cw, owner, method.getName(), method.getDesc(), userControlledArgPos, rcs.getUserControlledFields());
			cr.accept(rm, 0);
			nextMethods.addAll(rm.getNextInvokedMethods());
			return nextMethods;
		} 
		RenderClass rc = new RenderClass(cw, owner, method.getName(), method.getDesc(), userControlledArgPos, method.getIsField());
		cr.accept(rc, 0);
		return rc.getNextInvokedMethods(); // find all possible candidate method calls
//		rc.getNextInvokedMethods().forEach(e -> {
//		System.out.print(" " + e.getOwner() + ":" + e.getName() + " ");
//		e.getUserControlledArgPos().forEach((k, v) -> System.out.print(k + ","));
//		System.out.println();
//	});
	}
	
	/*
	 * result of Inspect method should be passed as argument to findChildrenComponents
	 */
	public List<Gadget> findChildren(MethodInfo method, Map<String, Map<Class<?>, byte[]>> hierarchy) throws ClassNotFoundException {
		List<Gadget> childrenForThisMethod = new ArrayList<Gadget>();
		String owner = method.getOwner(); 
		owner = owner.replaceAll("/", "\\.");
		String methodName = method.getName();
		String methodDesc = method.getDesc();
		Map<Class<?>, byte[]> subtypes = new HashMap<>();
		if (hierarchy.containsKey(owner)) {
			subtypes = hierarchy.get(owner);
		}
		if (subtypes.isEmpty()) {
			try {
				Class<?> last = Class.forName(owner);
				Method[] methods = last.getDeclaredMethods();
				for (Method m : methods) {
					if (m.getName().equals(methodName) && methodDesc.equals(MethodInfo.convertDescriptor(m))) {
						Gadget finale = new Gadget(last, method, this, null, method.getUserControlledArgPos(), depth + 1);
						childrenForThisMethod.add(finale);
						children.add(finale);
						return childrenForThisMethod;
					}
				}
				Gadget noSuchMethod = new Gadget(last, methodName + ":" + methodDesc, this, depth + 1);
				childrenForThisMethod.add(noSuchMethod); 
				return childrenForThisMethod;
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} 
			Gadget likelyDeadEnd = new Gadget(owner, method, this, depth + 1);
			childrenForThisMethod.add(likelyDeadEnd);
			children.add(likelyDeadEnd);
			return childrenForThisMethod;
		} // cases where the class the method belongs to is not found as it is not serializable but could be a blacklisted class:method
		
		subtypes.forEach((k, v) -> {
			Method[] allMethods = k.getDeclaredMethods(); 
			for (int i = 0; i < allMethods.length; i++) {
				if (methodName.equals(allMethods[i].getName()) && methodDesc.equals(MethodInfo.convertDescriptor(allMethods[i]))) {
					Gadget nextComp = new Gadget(k, method, this, v, method.getUserControlledArgPos(), depth + 1); // later should include classes who inherited the method to show all gadget chain possibilities
					if (!checkIfRepeated(nextComp, this))
						childrenForThisMethod.add(nextComp);
				}
			}
		});
		children.addAll(childrenForThisMethod);
		return childrenForThisMethod;
	}
	
	public boolean checkIfRepeated(Gadget gadget, Gadget parent) {
		if (parent.equals(gadget)) 
			return true;
		if (parent.getParent() == null) 
			return false;
		Gadget grandparent = parent.getParent();
		boolean result = checkIfRepeated(gadget, grandparent);
		return result;
	}
	
	@Override 
	public boolean equals(Object o) { // classname, methodDesc not considered gadgets that will be compared will possess clazz and method field
		Gadget another = (Gadget) o;
		if (this.getClazz().getName().equals(another.getClazz().getName()) && this.getMethod().equals(another.getMethod()) && Arrays.equals(this.getBytes(), another.getBytes())) {
			return true;
		}
		return false;
	}
	
	public Gadget getParent() {
		return parent;
	}
	
	public void setClazz(ClassLoader loader) throws ClassNotFoundException {
		ClassReader cr = new ClassReader(byteContent);
		String inputClazz = cr.getClassName();
		String outputStr = inputClazz.replaceAll("/", "\\.");
		clazz = Class.forName(outputStr, false, loader);
	}
	
	public Class<?> getClazz() {
		return clazz;
	}
	
	public MethodInfo getMethod() {
		return method;
	}
	
	public byte[] getBytes() {
		return byteContent;
	}
	
	public int getDepth() {
		return depth;
	}
	
	public String getName() {
		return classname;
	}
	
	public String getMethodDesc() {
		return methodDesc;
	}
	
	public boolean getVisitStatus() {
		return visited;
	}
	
	public List<Gadget> getRevisedChildren() {
		return revisedChildren;
	}
	
	public Map<Integer, BasicValue> getUserContolledArgPos() {
		return userControlledArgPos;
	}
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public void visited() {
		visited = true;
	}
	
	public void storeArgVals() {
		argVals = new Hashtable<>();
		userControlledArgPos.forEach((k, v) -> {
			String valRep;
			if (v == UserFieldInterpreter.USER_INFLUENCED) {
				valRep = "Influenced";
			} else if (v instanceof ArrayValue) {
				valRep = ((ArrayValue) v).toString();
			} else if (v instanceof MultiDArray) {
				valRep = ((MultiDArray) v).toString();
			} else {
				valRep = "Derived";
			}
			argVals.put(k, valRep);
		});
	}
	
	public void addRevisedChild(Gadget revisedChild) {
		if (revisedChild != null) {
			revisedChildren.add(revisedChild);
		}
	}
	
	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		ois.defaultReadObject();
		userControlledArgPos = new Hashtable<>();
		argVals.forEach((k, v) -> {
			if (v.equals("Derived")) {
				userControlledArgPos.put(k, UserFieldInterpreter.USER_DERIVED);
			} else if (v.equals("Influenced")) {
				userControlledArgPos.put(k, UserFieldInterpreter.USER_INFLUENCED);
			} else if (v.startsWith("ArrayValue")) {
				Scanner scan = new Scanner(v);
				scan.useDelimiter("\\,");
				scan.next();
				String l = scan.next();
				String c = scan.next();
				scan.close();
				BasicValue length = l.equals("Influenced")? UserFieldInterpreter.USER_INFLUENCED : UserFieldInterpreter.USER_DERIVED;
				BasicValue content = c.equals("Influenced")? UserFieldInterpreter.USER_INFLUENCED : UserFieldInterpreter.USER_DERIVED;
				ArrayValue arr = new ArrayValue(null, length);
				arr.setContents(content);
				userControlledArgPos.put(k, arr);
			} else if (v.startsWith("MultiDArray")) {
				Scanner scan = new Scanner(v);
				scan.useDelimiter("\\,|/");
				scan.next();
				int maxDim = Integer.parseInt(v.substring(v.length() - 1));
				MultiDArray gparent = new MultiDArray(null, maxDim, null);
				int dim = scan.nextInt();
				String al = scan.next();
				String cl = scan.next();
				BasicValue allLength = al.equals("Influenced")? UserFieldInterpreter.USER_INFLUENCED : UserFieldInterpreter.USER_DERIVED;
				BasicValue allContent = cl.equals("Influenced")? UserFieldInterpreter.USER_INFLUENCED : UserFieldInterpreter.USER_DERIVED;
				MultiDArray.setEntireNest(gparent, 1, allLength);
				MultiDArray.setEntireNest(gparent, 2, allContent);
				
				MultiDArray currentD = gparent;
				for (int i = 0; i < maxDim; i++) {
					String l = scan.next();
					BasicValue length = l.equals("Influenced")? UserFieldInterpreter.USER_INFLUENCED : UserFieldInterpreter.USER_DERIVED;
					currentD.setLength(length);
					currentD = currentD.getNested();
				}
				scan.close();
				
				MultiDArray actualArr = gparent;
				for (int i = 0; i < maxDim - dim; i++) {
					actualArr = actualArr.getNested();
				}
				userControlledArgPos.put(k, actualArr);
			}
		});
	}
}
