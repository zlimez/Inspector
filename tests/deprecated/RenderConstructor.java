package deprecated;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.analysis.BasicValue;

import userFields.ConstructorTracer;

public class RenderConstructor extends ClassVisitor{
	String owner;
	ConstructorTracer ct;
	
	public RenderConstructor(ClassVisitor cv, String owner) {
		super(Opcodes.ASM9, cv);
		this.owner = owner;
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
		} 
		return mv;
	}
	
	public Map<String, BasicValue> getUserControlledFields() {
		return ct.getUserControlledFields();
	}
}
