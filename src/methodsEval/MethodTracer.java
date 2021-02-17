package methodsEval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import userFields.UserFieldInterpreter;

public class MethodTracer extends MethodVisitor{
	String owner;
	MethodVisitor next;
	Map<Integer, BasicValue> userControlledArgPos = new HashMap<>();
	Map<String, BasicValue> UserControlledFields = new HashMap<>();
	List<MethodInfo> nextInvokedMethods;
	
	public MethodTracer(String owner, int access, String name, String desc, MethodVisitor mv, Map<Integer, BasicValue> userControlledArgPos, Map<String, BasicValue> UserControlledFields) {
		super(Opcodes.ASM9, new MethodNode(access, name, desc, null, null));
		this.owner = owner;
		this.next = mv;
		this.userControlledArgPos = userControlledArgPos;
		this.UserControlledFields = UserControlledFields;
	}
	
	@Override
	public void visitEnd() {
		MethodNode mn = (MethodNode) mv;
		UserFieldInterpreter interpreter = new UserFieldInterpreter(false, true, 0, userControlledArgPos);
		interpreter.setUserControlledFields(UserControlledFields);
		Analyzer<BasicValue> a = new Analyzer<BasicValue>(interpreter); // still need to add in control flow analysis 
		try {
			a.analyze(owner, mn);
		} catch (AnalyzerException e) {
			e.printStackTrace();
		}
		nextInvokedMethods = interpreter.getNextInvokedMethods();
//		Frame<BasicValue>[] frames = a.getFrames();
//		AbstractInsnNode[] insns = mn.instructions.toArray();
//		for (int i = 0; i < frames.length; i++) {
//			if (insns[i] instanceof MethodInsnNode) {
//				if (((MethodInsnNode) insns[i]).name.equals("doSomething")) {
//					BasicValue val = frames[i].getStack(frames[i].getStackSize() - 2);
//					System.out.println(frames[i].getStackSize());
//					System.out.println(val == UserFieldInterpreter.USER_DERIVED);
//				}
//			}
//		}
//		List<MethodInfo> methods = interpreter.getNextInvokedMethods();
//		methods.forEach(e -> {
//			System.out.println(e.getName());
//			e.getUserControlledArgPos().forEach((k, v) -> {
//				System.out.println(k);
//			});
//		});

		mn.accept(next);
	}
	
	public List<MethodInfo> getNextInvokedMethods() {
		return nextInvokedMethods;
	}
}
