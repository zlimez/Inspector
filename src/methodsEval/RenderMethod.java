package methodsEval;

import java.util.Collection;
import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.analysis.Value;

public class RenderMethod extends ClassVisitor {
	String owner;
	String methodName;
	String descriptor;
	Map<Integer, Value> userControlledArgPos;
	Map<String, Value> userControlledFields;
	MethodTracer mt;
	
	public RenderMethod(ClassVisitor cv, String owner, String methodName, String desc, Map<Integer, Value> userControlledArgPos, Map<String, Value> userControlledFields) {
		super(Opcodes.ASM9, cv);
		this.owner = owner;
		this.methodName = methodName;
		this.descriptor = desc;
		this.userControlledArgPos = userControlledArgPos;
		this.userControlledFields = userControlledFields;
	}
	
	@Override
	public MethodVisitor visitMethod(int acc, String name, String desc, String signature, String[] exceptions) {
		MethodVisitor mv;
		mv = cv.visitMethod(acc, name, desc, signature, exceptions);
		if (name.equals(methodName) && desc.equals(descriptor)) {
			mv = new MethodTracer(owner, acc, name, desc, mv, userControlledArgPos, userControlledFields);
			mt = (MethodTracer) mv;
		}
		return mv;
	}
	
	public Collection<MethodInfo> getNextInvokedMethods() {
		return mt.getNextInvokedMethods();
	}
}
