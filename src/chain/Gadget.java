package chain;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.analysis.BasicValue;

import methodsEval.MethodInfo;
import methodsEval.RenderClass;

public class Gadget {
	private Gadget parent;
	private Class<?> clazz;
	private MethodInfo method;
	private List<Gadget> children;
	private byte[] byteContent;
	private Map<Integer, BasicValue> userControlledArgPos;
	private int depth;
	
	public Gadget(Class<?> type, MethodInfo m, Gadget parent, byte[] b, Map<Integer, BasicValue> userControlledArgPos, int depth) {
		this.clazz = type;
		this.method = m;
		this.parent = parent;
		this.children = new ArrayList<Gadget>();
		this.byteContent = b;
		this.userControlledArgPos = userControlledArgPos;
		this.depth = depth;
	}
	
	public List<MethodInfo> InspectMethod() {
		ClassReader cr = new ClassReader(byteContent);
		String owner = cr.getClassName();
		ClassWriter cw = new ClassWriter(cr, 0);
		RenderClass rc = new RenderClass(cw, owner, method.getName(), method.getDesc(), userControlledArgPos);
		cr.accept(rc, 0);
//		rc.getNextInvokedMethods().forEach(e -> System.out.println(" " + e.getOwner() + ":" + e.getName()));
		return rc.getNextInvokedMethods(); // find all possible candidate method calls
	}
	
	/*
	 * result of Inspect method should be passed as argument to findChildrenComponents
	 */
	public List<Gadget> findChildren(MethodInfo method) throws ClassNotFoundException {
		List<Gadget> childrenForThisMethod = new ArrayList<Gadget>();
		String owner = method.getOwner(); 
		owner = owner.replaceAll("/", "\\.");
		String methodName = method.getName();
		String methodDesc = method.getDesc();
		Map<Class<?>, byte[]> subtypes = new HashMap<>();
		if (Enumerate.hierarchy.containsKey(owner)) {
			subtypes = Enumerate.hierarchy.get(owner);
		}
		if (subtypes.isEmpty()) {
			Class<?> last = Class.forName(owner);
			Method[] methods = last.getDeclaredMethods();
			for (Method m : methods) {
				if (m.getName().equals(methodName) && methodDesc.equals(MethodInfo.convertDescriptor(m))) {
					Gadget finale = new Gadget(last, method, this, null, null, depth + 1);
					childrenForThisMethod.add(finale);
					children.add(finale);
					return childrenForThisMethod;
				}
			}
		} // cases where the class the method belongs to is not found
		subtypes.forEach((k, v) -> {
			Method[] allMethods = k.getDeclaredMethods(); // should include accessor rendering as well to check whether the method can indeed be called
			for (int i = 0; i < allMethods.length; i++) {
				if (methodName.equals(allMethods[i].getName()) && methodDesc.equals(MethodInfo.convertDescriptor(allMethods[i]))) {
					Gadget nextComp = new Gadget(k, method, this, v, method.getUserControlledArgPos(), depth + 1); // later should include classes who inherited the method to show all gadget chain possibilities
					childrenForThisMethod.add(nextComp);
				}
			}
		});
		children.addAll(childrenForThisMethod);
		return childrenForThisMethod;
	}
	
	public Gadget getParent() {
		return parent;
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
}
