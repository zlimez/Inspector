package methodsEval;

import java.util.Collection;
import java.util.Map;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Value;

import userFields.CustomAnalyzer;
import userFields.CustomInterpreter;

public class MethodTracer extends MethodVisitor{
	String owner;
	MethodVisitor next;
	Map<Integer, Value> userControlledArgPos;
	Map<String, Value> userControlledFields;
	Collection<MethodInfo> nextInvokedMethods;
	boolean isMagicMethod = false;
	
	public MethodTracer(String owner, int access, String name, String desc, MethodVisitor mv, Map<Integer, Value> userControlledArgPos, Map<String, Value> userControlledFields, boolean ... isMagicMethod) {
		super(Opcodes.ASM9, new MethodNode(access, name, desc, null, null));
		this.owner = owner;
		this.next = mv;
		this.userControlledArgPos = userControlledArgPos;
		this.userControlledFields = userControlledFields;
		if (isMagicMethod.length > 0) {
			this.isMagicMethod = isMagicMethod[0];
		}
	}
	
	@Override
	public void visitEnd() {
		MethodNode mn = (MethodNode) mv;
		CustomInterpreter interpreter = new CustomInterpreter(owner, isMagicMethod, userControlledArgPos);
		interpreter.setUserControlledFields(userControlledFields);
		CustomAnalyzer a = new CustomAnalyzer(interpreter); // still need to add in control flow analysis 
		try {
			a.analyze(owner, mn);
		} catch (AnalyzerException e) {
			e.printStackTrace();
		}
		nextInvokedMethods = interpreter.getNextInvokedMethods();
		if (isMagicMethod) {
			userControlledFields = a.getFinalControlledFields();
		}
		mn.accept(next);
	}
	
	public Collection<MethodInfo> getNextInvokedMethods() {
		return nextInvokedMethods;
	}
	
	public Map<String, Value> getUserControlledFields() { // called only for magic method to get the full list of userControlledFields
		return userControlledFields;
	}
}
