package chain;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.analysis.Value;

import methodsEval.MethodInfo;
import methodsEval.RenderClass;
import methodsEval.RenderMethod;
import userFields.CustomInterpreter.ReferenceValue;
import userFields.RenderStream;

public class Gadget implements Serializable {
	private static final long serialVersionUID = 1L;
	private transient Gadget parent;
	private transient Class<?> clazz;
	private MethodInfo method;
	private List<Gadget> children;
	private byte[] byteContent;
	private Map<Integer, Value> userControlledArgPos;
	private int depth;
	
	private int id; // for reference during neo4j 
	private List<Gadget> revisedChildren = new ArrayList<Gadget>(); // for gadgets within a potential or valid gadget where some child path are invalid and should be removed to facilitate iterative analysis from end points
	private boolean visited = false;
	
	public String classname; // used only when class cannot be found correspond to 2nd constructor
	public String methodDesc; // used only when method cannot be found correspond to third constructor
	
	public Gadget(Class<?> type, MethodInfo m, Gadget parent, byte[] b, Map<Integer, Value> userControlledArgPos, int depth) {
		this.clazz = type;
		this.method = m;
		this.parent = parent;
		this.children = new ArrayList<>();
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
	
	public Collection<MethodInfo> InspectMethod(Map<String, Set<String>> magicMethods) {
		ClassReader cr = new ClassReader(byteContent);
		String owner = cr.getClassName();
		ClassWriter cw = new ClassWriter(cr, 0);
		String cName = owner.replaceAll("/", "\\."); 
		System.out.println(cName + ":" + method.getName());
		if (method.getIsField()) {
			if (magicMethods.containsKey(cName) && !magicMethods.get(cName).contains(method.getName() + method.getDesc())) {
				RenderStream rs = new RenderStream(cw, owner, magicMethods.get(cName));
				cr.accept(rs, 0);
				RenderMethod rm = new RenderMethod(cw, owner, method.getName(), method.getDesc(), userControlledArgPos, rs.getUserControlledFields());
				cr.accept(rm, 0);
				return rm.getNextInvokedMethods();
			} else if (Serializable.class.isAssignableFrom(clazz)) { // all classes are certainly serializable now which will be altered later for a better analysis
				RenderClass rc = new RenderClass(cw, owner, method.getName(), method.getDesc(), userControlledArgPos, true, null);
				cr.accept(rc, 0);
//				rc.getNextInvokedMethods().forEach(mf -> {
//					System.out.println(" " + mf.getOwner() + " " + mf.getName() + " " + mf.getUserControlledArgPos().keySet());
//				});
				return rc.getNextInvokedMethods();
			}
		}
		/*
		 * for methods called on local var in the method/ first being the case where the var is tainted second being the args called by the var
		 * field takes precedence over <init>
		 */
		ReferenceValue ownerObject = method.limitedConstructorInfluence();
		if (ownerObject != null) {
			RenderClass rc = new RenderClass(cw, owner, method.getName(), method.getDesc(), userControlledArgPos, false, ownerObject);
			cr.accept(rc, 0);
			return rc.getNextInvokedMethods();
		}
		// for methods where only the arguments are tainted
		RenderMethod rc = new RenderMethod(cw, owner, method.getName(), method.getDesc(), userControlledArgPos, new HashMap<String, Value>()); 
		cr.accept(rc, 0);
		return rc.getNextInvokedMethods(); // find all possible candidate method calls
	}
	
	// result of Inspect method should be passed as argument to findChildrenComponents
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
	
	public Map<Integer, Value> getUserContolledArgPos() {
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
	
	public void addRevisedChild(Gadget revisedChild) {
		if (revisedChild != null) {
			revisedChildren.add(revisedChild);
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(byteContent);
		result = prime * result + ((classname == null) ? 0 : classname.hashCode());
		result = prime * result + ((method == null) ? 0 : method.hashCode());
		result = prime * result + ((methodDesc == null) ? 0 : methodDesc.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Gadget other = (Gadget) obj;
		if (!Arrays.equals(byteContent, other.byteContent))
			return false;
		if (classname == null) {
			if (other.classname != null)
				return false;
		} else if (!classname.equals(other.classname))
			return false;
		if (method == null) {
			if (other.method != null)
				return false;
		} else if (!method.equals(other.method))
			return false;
		if (methodDesc == null) {
			if (other.methodDesc != null)
				return false;
		} else if (!methodDesc.equals(other.methodDesc))
			return false;
		return true;
	}
}
