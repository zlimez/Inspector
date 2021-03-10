package userFields;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.analysis.BasicValue;

import methodsEval.MethodInfo;
import methodsEval.MethodTracer;

public class RenderConstructorAndStream extends ClassVisitor{
	String owner;
	ConstructorTracer ct;
	MethodTracer mt;
	Map<String, BasicValue> userControlledFields = new HashMap<>();
	Set<MethodInfo> nextInvokedMethods = new HashSet<>();
	Map<String, String> magicMethods = new HashMap<>();
	List<String> transientFields = new ArrayList<>();
	boolean isFirstPass = true;
	
	public RenderConstructorAndStream(ClassVisitor cv, String owner, Map<String, String> methodID) {
		super(Opcodes.ASM9, cv);
		this.owner = owner;
		this.magicMethods = methodID;
	}
	
	@Override //check transient fields remove from usercontrolled list
	public FieldVisitor visitField(int acc, String name, String desc, String signature, Object value) {
		if (acc >= Opcodes.ACC_TRANSIENT && acc <= 160) {
			transientFields.add(name);
		}
		return cv.visitField(acc, name, desc, signature, value);
	}
	
	@Override
	public MethodVisitor visitMethod(int acc, String name, String desc, String signature, String[] exceptions) {		
		MethodVisitor mv;
		mv = cv.visitMethod(acc, name, desc, signature, exceptions);
		if (name.equals("<init>")) {
			if (ct != null) {
				userControlledFields.putAll(ct.getUserControlledFields()); // in case there are multiple constructors
			}
			int numOfArgs = MethodInfo.countArgs(desc);
			
			mv = new ConstructorTracer(owner, acc, name, desc, mv, numOfArgs);
			ct = (ConstructorTracer) mv;
		} else if (magicMethods.containsKey(name) && magicMethods.get(name).equals(desc)) {
			if (isFirstPass) {
				if (ct != null) {
					userControlledFields.putAll(ct.getUserControlledFields());
				}
				Iterator<Map.Entry<String, BasicValue>> it = userControlledFields.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry<String, BasicValue> field = (Map.Entry<String, BasicValue>) it.next();
					if (transientFields.contains(field.getKey())) {
						it.remove();
					}
				} // when all constructors are rendered and the first io method is inspected
				isFirstPass = false;
			} else {
				if (mt != null) {
					userControlledFields.putAll(mt.getUserControlledFields());
					nextInvokedMethods.addAll(mt.getNextInvokedMethods());
				}
			}
			Map<Integer, BasicValue> userControlledArgPos = new HashMap<>();
			for (int i = 0; i <= MethodInfo.countArgs(desc); i++) {
				userControlledArgPos.put(i, UserFieldInterpreter.USER_INFLUENCED);
			}
			mv = new MethodTracer(owner, acc, name, desc, mv, userControlledArgPos, userControlledFields, true);
			mt = (MethodTracer) mv;
		}
		return mv;
	}
	
	public Map<String, BasicValue> getUserControlledFields() {
		if (mt != null) {
			userControlledFields.putAll(mt.getUserControlledFields());
		}
		return userControlledFields;
	}
	
	public Set<MethodInfo> getNextInvokedMethods() { // if object is field and serializable its magic method will be invoked leading to more methods being called
		if (mt != null) {
			nextInvokedMethods.addAll(mt.getNextInvokedMethods());
		}
		return nextInvokedMethods;
	}
}
