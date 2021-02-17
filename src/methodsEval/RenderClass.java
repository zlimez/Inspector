package methodsEval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.analysis.BasicValue;

import org.objectweb.asm.FieldVisitor;
import userFields.ConstructorTracer;

public class RenderClass extends ClassVisitor {
	String owner;
	String methodName;
	String descriptor;
	Map<Integer, BasicValue> userControlledArgPos;
	MethodTracer mt;
	ConstructorTracer ct;
	Map<String, BasicValue> userControlledFields = new HashMap<>();
	List<String> transientFields = new ArrayList<>();
	
	public RenderClass(ClassVisitor cv, String owner, String methodName, String descriptor, Map<Integer, BasicValue> userControlledArgPos) {
		super(Opcodes.ASM9, cv);
		this.owner = owner;
		this.methodName = methodName;
		this.descriptor = descriptor;
		this.userControlledArgPos = userControlledArgPos;
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
		if (name.equals("<init>")) { //assume there is some way of accessing the constructor if if it is private
			if (ct != null) {
				userControlledFields.putAll(ct.getUserControlledFields()); // in case there are multiple constructors
			}
			int numOfArgs = 0;
			String prim = "[ZCBSIFJD]";
			String nonPrim = "L(\\w+/)*[\\w\\$]+;";
			Pattern pattern = Pattern.compile(nonPrim);
			Pattern primPattern = Pattern.compile(prim);
			Matcher matcher = pattern.matcher(desc);
			while (matcher.find()) {
				numOfArgs++;
			}
			String modified = desc.replaceAll(nonPrim, "");
			Matcher m = primPattern.matcher(modified);
			while (m.find()) {
				numOfArgs++;
			}

			mv = new ConstructorTracer(owner, acc, name, desc, mv, numOfArgs);
			ct = (ConstructorTracer) mv;
		} else if (name.equals(methodName) && desc.equals(descriptor)) {
			if (ct != null) {
				userControlledFields.putAll(ct.getUserControlledFields());
			} // last constructor 
//			Iterator<Map.Entry<String, BasicValue>> it = userControlledFields.entrySet().iterator();
//			while (it.hasNext()) {
//				Map.Entry<String, BasicValue> field = (Map.Entry<String, BasicValue>) it.next();
//				if (transientFields.contains(field.getKey())) {
//					it.remove();
//				}
//			} // USER_INFLUENCED should take precedence over USER_DERIVED
			mv = new MethodTracer(owner, acc, name, desc, mv, userControlledArgPos, userControlledFields);
			mt = (MethodTracer) mv;
		} // assume constructor comes before any methods
		return mv;
	}
	
	public List<MethodInfo> getNextInvokedMethods() {
		return mt.getNextInvokedMethods();
	}
}
