package chain.serialcheck;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CheckDeserialization extends ClassVisitor {
	public boolean deserializing; 
	public CheckDeserialization(ClassVisitor cv) {
		super(Opcodes.ASM9, cv);
		deserializing = false;
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		MethodVisitor mv;
		mv = cv.visitMethod(access, name, desc, signature, exceptions);
		if (mv != null) {
			mv = new MethodChecker(mv, this);
		}
		return mv;
	}
	
	public void changeBool() {
		deserializing = true;
	}
}