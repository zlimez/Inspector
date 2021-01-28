package methodsEval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.analysis.BasicValue;

import userFields.ConstructorTracer;

public class RenderClass extends ClassVisitor {
	String owner;
	String methodName;
	Map<Integer, BasicValue> userControlledArgPos = new HashMap<>();
	MethodTracer mt;
	ConstructorTracer ct;
	
	public RenderClass(ClassVisitor cv, String owner, String methodName, Map<Integer, BasicValue> userControlledArgPos) {
		super(Opcodes.ASM9, cv);
		this.owner = owner;
		this.methodName = methodName;
		this.userControlledArgPos = userControlledArgPos;
	}
	
	@Override
	public MethodVisitor visitMethod(int acc, String name, String desc, String signature, String[] exceptions) {
		MethodVisitor mv;
		mv = cv.visitMethod(acc, name, desc, signature, exceptions);
		if (acc == Opcodes.ACC_PUBLIC && name.equals("<init>")) {
			int numOfArgs = 0;
			String regexStr = "[ZCBSIFJDL]";
			String extraCounts = "/Z|/C|/B|/I|/S|/F|/J|/D|/L";
			Pattern pattern = Pattern.compile(regexStr);
			Pattern subtract = Pattern.compile(extraCounts);
			Matcher matcher = pattern.matcher(desc);
			Matcher m = subtract.matcher(desc);
			while (matcher.find()) {
				numOfArgs++;
			}
			while (m.find()) {
				numOfArgs--;
			}
			mv = new ConstructorTracer(owner, acc, name, desc, mv, numOfArgs);
			ct = (ConstructorTracer) mv;
		} else if (name.equals(methodName)) {
			mv = new MethodTracer(owner, acc, name, desc, mv, userControlledArgPos, ct.getUserControlledFields());
			mt = (MethodTracer) mv;
		} // assume constructor comes before any methods
		return mv;
	}
	
	public List<MethodInfo> getNextInvokedMethods() {
		return mt.getNextInvokedMethods();
	}
}
