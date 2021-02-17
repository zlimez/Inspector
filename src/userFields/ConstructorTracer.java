package userFields;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.Map;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ConstructorTracer extends MethodVisitor {
	String owner;
    int localVarLength;
	MethodVisitor next;
	Map<String, BasicValue> UserControlledFields;
	
	public ConstructorTracer(String owner, int access, String name, String desc, MethodVisitor mv, int localVarLength) {
		super(Opcodes.ASM9, new MethodNode(access, name, desc, null, null));
		this.owner = owner;
		this.localVarLength = localVarLength;
		this.next = mv;
	}
	
	@Override
	public void visitEnd() { //need to consider jump instruction that changes the frame (newControlFlowEdge
		MethodNode mn = (MethodNode) mv;
		UserFieldInterpreter interpreter = new UserFieldInterpreter(true, false, localVarLength, null);
		Analyzer<BasicValue> a = new Analyzer<BasicValue>(interpreter);
		try {
			a.analyze(owner, mn);
		} catch (AnalyzerException e) {
			e.printStackTrace();
		}
//		AbstractInsnNode[] insns = mn.instructions.toArray();
//		for (int i = 0; i < insns.length; i++) {
//			if (i == 10) {
//				System.out.println(insns[i].getOpcode());
//			}
//		}
//		Frame<BasicValue>[] frames = a.getFrames();
//		for (int i = 0; i < frames.length; i++) {
//			if (i == 11) {
//			System.out.println(frames[i].getStackSize());
//			}
//		}
		UserControlledFields = interpreter.getUserControlledFieldsFromConstructor();

//		interpreter.getUserControlledFieldsFromConstructor().forEach((k, v) -> {
//			boolean equals = (v == UserFieldInterpreter.USER_INFLUENCED);
//			System.out.println(k + " " + equals);
//		});
		mn.accept(next);
	}
	
	public Map<String, BasicValue> getUserControlledFields() {
		return UserControlledFields;
	}
}
