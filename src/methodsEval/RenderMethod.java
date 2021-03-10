package methodsEval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.analysis.BasicValue;

public class RenderMethod extends ClassVisitor {
	// Inspect a method to find the next method call within this method
	String owner;
	String methodName;
	String descriptor;
	Map<Integer, BasicValue> userControlledArgPos = new HashMap<>();
	Map<String, BasicValue> userControlledFields = new HashMap<>();
	MethodTracer mt;
	
	public RenderMethod(ClassVisitor cv, String owner, String methodName, String desc, Map<Integer, BasicValue> userControlledArgPos, Map<String, BasicValue> userControlledFields) {
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
	
	public Set<MethodInfo> getNextInvokedMethods() {
		return mt.getNextInvokedMethods();
	}
}
