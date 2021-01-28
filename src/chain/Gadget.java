package chain;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
	private Method method;
	private List<Gadget> children;
	private byte[] byteContent;
	private Map<Integer, BasicValue> userControlledArgPos;
	
	public Gadget(Class<?> type, Method m, Gadget parent, byte[] b, Map<Integer, BasicValue> userControlledArgPos) {
		this.clazz = type;
		this.method = m;
		this.parent = parent;
		this.children = new ArrayList<Gadget>();
		this.byteContent = b;
		this.userControlledArgPos = userControlledArgPos;
	}
	
	public List<MethodInfo> InspectMethod() {
		ClassReader cr = new ClassReader(byteContent);
		String owner = cr.getClassName();
		ClassWriter cw = new ClassWriter(cr, 0);
		RenderClass rc = new RenderClass(cw, owner, method.getName(), userControlledArgPos);
		cr.accept(rc, 0);
		return rc.getNextInvokedMethods(); // find all possible candidate method calls
	}
	
	/*
	 * result of Inspect method should be passed as argument to findChildrenComponents
	 */
	public List<Gadget> findChildren(MethodInfo method) throws ClassNotFoundException {
		List<Gadget> childrenForThisMethod = new ArrayList<Gadget>();
		String owner = method.getOwner(); // omit method descriptor first
		owner = owner.replaceAll("/", "\\.");
		System.out.println(owner);
		String methodName = method.getName();
		System.out.println(methodName);
		Map<Class<?>, byte[]> subtypes;
		if (Enumerate.hierarchy.containsKey(owner)) {
			subtypes = Enumerate.hierarchy.get(owner);
		} else {
			subtypes = null; // jdk classes last gadget in chain
		} // because haven't included jdk standard library
		if (subtypes == null) {
			Class<?> last = Class.forName(owner);
			Method[] methods = last.getDeclaredMethods();
			for (Method m : methods) {
				if (m.getName().equals(methodName)) {
					Gadget finale = new Gadget(last, m, this, null, null);
					childrenForThisMethod.add(finale);
					children.add(finale);
					return childrenForThisMethod;
				}
			}
		}
		subtypes.forEach((k, v) -> {
			Method[] allMethods = k.getDeclaredMethods();
			for (int i = 0; i < allMethods.length; i++) {
				if (methodName.equals(allMethods[i].getName())) {
					Gadget nextComp = new Gadget(k, allMethods[i], this, v, method.getUserControlledArgPos()); // later should include classes who inherited the method to show all gadget chain possibilities
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
	
	public Method getMethod() {
		return method;
	}
}
