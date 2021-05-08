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
import java.util.Stack;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.analysis.Value;

import hierarchy.SortClass.ClassAndBytes;
import methodsEval.MethodInfo;
import methodsEval.RenderClass;
import methodsEval.RenderMethod;
import userFields.CustomInterpreter.ReferenceValue;
import userFields.RenderStream;

public class Gadget implements Serializable {
	private static final long serialVersionUID = 1L;
	// Each gadget is unique per scan but not across several scans
	protected static Map<String, Gadget> allGadgets = new HashMap<>();
	private transient List<Gadget> parents;
	private transient Class<?> clazz;
	private MethodInfo method;
	private List<Gadget> children;
	private byte[] byteContent;
	private Map<Integer, Value> userControlledArgPos;
	private int depth;
	private transient boolean isSink = false;
	// for reference during neo4j 
	private int id; 
	// for gadgets within a potential or valid gadget where some child path are invalid and should be removed to facilitate iterative analysis from end points
	private transient Stack<Gadget> revisedChildren; 
	private transient boolean visited = false;
	
	protected String classname; // used only when class cannot be found correspond to 2nd constructor
	
	public Gadget(String classname, Class<?> type, MethodInfo m, Gadget parent, byte[] b, Map<Integer, Value> userControlledArgPos, int depth) {
		this.classname = classname;
		this.clazz = type;
		this.method = m;
		this.parents = new ArrayList<>();
		if (parent != null)
			this.parents.add(parent);
		this.children = new ArrayList<>();
		this.byteContent = b;
		this.userControlledArgPos = userControlledArgPos;
		this.depth = depth;
		
		allGadgets.put(this.genKey(), this);
	}
	
	public Gadget(String classname, MethodInfo m, Gadget parent, int depth) {
		this.classname = classname;
		this.method = m;
		this.parents = new ArrayList<>();
		this.parents.add(parent);
		this.depth = depth;
		
		allGadgets.put(this.genKey(), this);
	} 
	
	public Collection<MethodInfo> InspectMethod(Map<String, Set<String>> magicMethods) {
		ClassReader cr = new ClassReader(byteContent);
		ClassWriter cw = new ClassWriter(cr, 0); 
		String owner = classname.replaceAll("\\.", "/");
		System.out.println(classname + ":" + method.getName() + userControlledArgPos.keySet());
		if (method.getIsField()) {
			if (magicMethods.containsKey(classname) && !magicMethods.get(classname).contains(method.getName() + method.getDesc())) {
				RenderStream rs = new RenderStream(cw, owner, magicMethods.get(classname));
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
	public List<Gadget> findChildren(MethodInfo method, Enumerate container) throws ClassNotFoundException {
		Map<String, List<ClassAndBytes>> hierarchy = container.hierarchy;
		List<Gadget> childrenForThisMethod = new ArrayList<>();
		String owner = method.getOwner().replaceAll("/", "\\.");
		String methodName = method.getName();
		String methodDesc = method.getDesc();
		List<ClassAndBytes> subtypes = new ArrayList<>();
		
		if (hierarchy.containsKey(owner)) 
			subtypes.addAll(hierarchy.get(owner));
		
		if (subtypes.isEmpty() && !method.canBeProxy()) {
			try {
				Class<?> last = Class.forName(owner);
				Method[] methods = last.getDeclaredMethods();
				for (Method m : methods) {
					if (m.getName().equals(methodName) && methodDesc.equals(MethodInfo.convertDescriptor(m))) {
						Gadget finale = new Gadget(owner, last, method, this, null, method.getUserControlledArgPos(), depth + 1);
						childrenForThisMethod.add(finale);
						children.add(finale);
						return childrenForThisMethod;
					}
				}
				System.out.println(methodName + methodDesc + " does not exist in " + owner);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} 
			Gadget likelyDeadEnd = new Gadget(owner, method, this, depth + 1);
			childrenForThisMethod.add(likelyDeadEnd);
			children.add(likelyDeadEnd);
			return childrenForThisMethod;
		} // cases where the class the method belongs to is not found as it is not serializable but could be a blacklisted class:method	
		String methodStr = method.gadgetMethodString();
		//Only methods with isField = true will allow polymorphism
		if (method.getFixedType()) {
			subtypes.forEach(k -> {
				Class<?> subtype = k.getClazz();
				if (subtype.getName().equals(owner)) {
					String key = subtype.getName() + methodStr;
					Method[] allMethods = subtype.getDeclaredMethods(); 
					for (int i = 0; i < allMethods.length; i++) {
						if (methodName.equals(allMethods[i].getName()) && methodDesc.equals(MethodInfo.convertDescriptor(allMethods[i]))) {
							Gadget nextComp;
							if (allGadgets.containsKey(key)) {					
								nextComp = allGadgets.get(key);
								nextComp.parents.add(this);
							} else {
								nextComp = new Gadget(subtype.getName(), subtype, method, this, k.getBytes(), method.getUserControlledArgPos(), depth + 1); // later should include classes who inherited the method to show all gadget chain possibilities
								childrenForThisMethod.add(nextComp);
							}
						}
					}
				}
			});
		} else {		
			subtypes.forEach(k -> {
				Class<?> subtype = k.getClazz();
				String key = subtype.getName() + methodStr;
				Method[] allMethods = subtype.getDeclaredMethods(); 
				for (int i = 0; i < allMethods.length; i++) {
					if (methodName.equals(allMethods[i].getName()) && methodDesc.equals(MethodInfo.convertDescriptor(allMethods[i]))) {
						Gadget nextComp;
						if (allGadgets.containsKey(key)) {					
							nextComp = allGadgets.get(key);
							nextComp.parents.add(this);
						} else {
							nextComp = new Gadget(subtype.getName(), subtype, method, this, k.getBytes(), method.getUserControlledArgPos(), depth + 1); // later should include classes who inherited the method to show all gadget chain possibilities
							childrenForThisMethod.add(nextComp);
						}
					}
				}
			});
			if (method.canBeProxy()) {
				for (ClassAndBytes handler : container.handlers) {
					Gadget nextComp;
					Class<?> handlerClazz = handler.getClazz();
					MethodInfo transformedMethod = method.transformToHandler(handler.getInvokeDesc());
					String key = handlerClazz.getName() + transformedMethod.gadgetMethodString();
					if (allGadgets.containsKey(key)) {
						nextComp = allGadgets.get(key);
						nextComp.parents.add(this);
					} else {
						nextComp = new Gadget(handlerClazz.getName(), handlerClazz, transformedMethod, this, handler.getBytes(), transformedMethod.getUserControlledArgPos(), depth + 1); // later should include classes who inherited the method to show all gadget chain possibilities
						childrenForThisMethod.add(nextComp);
					}
				}
			}
		}
		children.addAll(childrenForThisMethod);
		return childrenForThisMethod;
	}
	
	public List<Gadget> getParents() {
		return parents;
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
	
	public String getClassname() {
		return classname;
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
	
	public boolean getVisitStatus() {
		return visited;
	}
	
	public Stack<Gadget> getRevisedChildren() {
		return revisedChildren;
	}
	
	public Map<Integer, Value> getUserContolledArgPos() {
		return userControlledArgPos;
	}
	
	public int getId() {
		return id;
	}
	
	public boolean getIsSink() {
		return isSink;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public void setIsSink() {
		this.isSink = true;
	}
	
	public void visited() {
		visited = true;
	}
	
	public void addRevisedChild(Gadget revisedChild) {
		if (revisedChildren == null)
			revisedChildren = new Stack<>();
		if (revisedChild != null) 
			revisedChildren.push(revisedChild);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(byteContent);
		result = prime * result + ((classname == null) ? 0 : classname.hashCode());
		result = prime * result + ((method == null) ? 0 : method.hashCode());
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
		return true;
	}

	public String genKey() {
		return classname + method.gadgetMethodString();
	}
}
