package deprecated;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.analysis.BasicValue;

import methodsEval.MethodInfo;
import methodsEval.MethodTracer;

public class RenderMethod extends ClassVisitor {
	// Inspect a method to find the next method call within this method
	String owner;
	String methodName;
	Map<Integer, BasicValue> userControlledArgPos = new HashMap<>();
	Map<String, BasicValue> UserControlledFields = new HashMap<>();
	MethodTracer mt;
	
	public RenderMethod(ClassVisitor cv, String owner, String methodName, Map<Integer, BasicValue> userControlledArgPos, Map<String, BasicValue> UserControlledFields) {
		super(Opcodes.ASM9, cv);
		this.owner = owner;
		this.methodName = methodName;
		this.userControlledArgPos = userControlledArgPos;
		this.UserControlledFields = UserControlledFields;
	}
	
	@Override
	public MethodVisitor visitMethod(int acc, String name, String desc, String signature, String[] exceptions) {
		MethodVisitor mv;
		mv = cv.visitMethod(acc, name, desc, signature, exceptions);
		if (name.equals(methodName)) {
			mv = new MethodTracer(owner, acc, name, desc, mv, userControlledArgPos, UserControlledFields);
			mt = (MethodTracer) mv;
		}
		return mv;
	}
	
	public List<MethodInfo> getNextInvokedMethods() {
		return mt.getNextInvokedMethods();
	}
}
