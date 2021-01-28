package chain.serialcheck;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MethodChecker extends MethodVisitor{
	private CheckDeserialization cv;
	public MethodChecker(MethodVisitor mv, CheckDeserialization cd) {
		super(Opcodes.ASM9, mv);
		this.cv = cd;
	}
	
	@Override 
	public void visitMethodInsn(int opc, String owner, String name, String desc, boolean isInterface) {
		if (opc == Opcodes.INVOKEVIRTUAL && owner.equals("java/io/ObjectInputStream") && name.equals("readObject") && desc.equals("()Ljava/lang/Object;") && !isInterface) {
			cv.changeBool();
		}
		mv.visitMethodInsn(opc, owner, name, desc, isInterface);
	}
}
