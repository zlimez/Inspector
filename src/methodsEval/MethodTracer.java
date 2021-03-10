package methodsEval;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;

import userFields.UserFieldInterpreter;

public class MethodTracer extends MethodVisitor{
	String owner;
	MethodVisitor next;
	Map<Integer, BasicValue> userControlledArgPos = new HashMap<>();
	Map<String, BasicValue> UserControlledFields = new HashMap<>();
	Set<MethodInfo> nextInvokedMethods;
	boolean isMagicMethod = false;
	
	public MethodTracer(String owner, int access, String name, String desc, MethodVisitor mv, Map<Integer, BasicValue> userControlledArgPos, Map<String, BasicValue> UserControlledFields, boolean ... isMagicMethod) {
		super(Opcodes.ASM9, new MethodNode(access, name, desc, null, null));
		this.owner = owner;
		this.next = mv;
		this.userControlledArgPos = userControlledArgPos;
		this.UserControlledFields = UserControlledFields;
		if (isMagicMethod.length > 0) {
			this.isMagicMethod = isMagicMethod[0];
		}
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
		if (isMagicMethod) {
			UserControlledFields = interpreter.getUserControlledFields();
		}
		mn.accept(next);
	}
	
	public Set<MethodInfo> getNextInvokedMethods() {
		return nextInvokedMethods;
	}
	
	public Map<String, BasicValue> getUserControlledFields() { // called only for magic method to get the full list of userControlledFields
		return UserControlledFields;
	}
}
