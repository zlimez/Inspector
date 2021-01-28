package methodsEval;

import java.util.Map;

import org.objectweb.asm.tree.analysis.BasicValue;

public class MethodInfo {
	private String owner;
	private String name;
	private String desc;
	private boolean isStatic; // determines do i need to parse the constructor of the next interesting method
	private Map<Integer, BasicValue> userControlledArgPos;
	
	public MethodInfo(String owner, String name, boolean isStatic, Map<Integer, BasicValue> userControlledArgPos, String desc) {
		this.name = name;
		this.owner = owner;
		this.isStatic = isStatic;
		this.userControlledArgPos = userControlledArgPos;
		this.desc = desc;
	}
	
	public String getName() {
		return name;
	}
	
	public boolean getMethodType() {
		return isStatic;
	}
	
	public Map<Integer, BasicValue> getUserControlledArgPos() {
		return userControlledArgPos;
	}
	
	public String getOwner() {
		return owner;
	}
	
	public String getDesc() {
		return desc;
	}
}
 