package methodsEval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.analysis.BasicValue;

import org.objectweb.asm.FieldVisitor;
import userFields.ConstructorTracer;

// classes without readObject magic method
public class RenderClass extends ClassVisitor {
	String owner;
	String methodName;
	String descriptor;
	Map<Integer, BasicValue> userControlledArgPos;
	MethodTracer mt;
	ConstructorTracer ct;
	Map<String, BasicValue> userControlledFields = new HashMap<>();
	List<String> transientFields = new ArrayList<>();
	boolean isField;
	
	public RenderClass(ClassVisitor cv, String owner, String methodName, String descriptor, Map<Integer, BasicValue> userControlledArgPos, boolean isField) {
		super(Opcodes.ASM9, cv);
		this.owner = owner;
		this.methodName = methodName;
		this.descriptor = descriptor;
		this.userControlledArgPos = userControlledArgPos;
		this.isField = isField;
	}
	
	@Override
	public FieldVisitor visitField(int acc, String name, String desc, String signature, Object value) {
		if (isField && acc >= Opcodes.ACC_TRANSIENT && acc <= 160) {
			transientFields.add(name);
		}
		return cv.visitField(acc, name, desc, signature, value);
	}
	
	@Override
	public MethodVisitor visitMethod(int acc, String name, String desc, String signature, String[] exceptions) {
		MethodVisitor mv;
		mv = cv.visitMethod(acc, name, desc, signature, exceptions);
		if (name.equals("<init>")) { //assume there is some way of accessing the constructor if if it is private
			if (ct != null) {
				userControlledFields.putAll(ct.getUserControlledFields()); // in case there are multiple constructors
			}
			int numOfArgs = MethodInfo.countArgs(desc);

			mv = new ConstructorTracer(owner, acc, name, desc, mv, numOfArgs);
			ct = (ConstructorTracer) mv;
		} else if (name.equals(methodName) && desc.equals(descriptor)) {
			if (ct != null) {
				userControlledFields.putAll(ct.getUserControlledFields());
			} // last constructor before specified method is inspected
			
			if (isField) {
				Iterator<Map.Entry<String, BasicValue>> it = userControlledFields.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String, BasicValue> field = (Map.Entry<String, BasicValue>) it.next();
					if (transientFields.contains(field.getKey())) {
						it.remove();
					}
				} // USER_INFLUENCED should take precedence over USER_DERIVED
			}
			
			mv = new MethodTracer(owner, acc, name, desc, mv, userControlledArgPos, userControlledFields);
			mt = (MethodTracer) mv;
		} // assume constructor comes before any methods
		return mv;
	}
	
	public Set<MethodInfo> getNextInvokedMethods() {
		return mt.getNextInvokedMethods();
	}
}
