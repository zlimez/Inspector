package methodsEval.userFields;

import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;

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
		UserControlledFields = interpreter.getUserControlledFieldsFromConstructor();
		mn.accept(next);
	}
	
	public Map<String, BasicValue> getUserControlledFields() {
		return UserControlledFields;
	}
}
