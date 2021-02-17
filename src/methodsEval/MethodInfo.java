package methodsEval;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.tree.analysis.BasicValue;

public class MethodInfo implements Serializable {
	private static final long serialVersionUID = 1L;
	private transient String owner;
	private String name;
	private String desc;
	private boolean isStatic; // determines do i need to parse the constructor of the next interesting method
	private transient Map<Integer, BasicValue> userControlledArgPos;
//	private int parameterCount;
	
	public MethodInfo(String owner, String name, boolean isStatic, Map<Integer, BasicValue> userControlledArgPos, String desc) {
		this.name = name;
		this.owner = owner;
		this.isStatic = isStatic;
		this.userControlledArgPos = userControlledArgPos;
		this.desc = desc;
	}
	
	public MethodInfo(String name, String desc, boolean isStatic) { //for entry point only
		this.name = name;
		this.desc = desc;
		this.isStatic = isStatic;
//		this.parameterCount = parameterCount;
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
	
//	public int getParamCount() {
//		return parameterCount;
//	}
	
	public static String convertDescriptor(Method method) {
		Class<?>[] params = method.getParameterTypes();
		StringBuffer sb = new StringBuffer("(");
		StringBuffer local = new StringBuffer();
		String[] arr = new String[] {"^int(\\[\\])*", "^boolean(\\[\\])*", "^long(\\[\\])*", "^short(\\[\\])*", "^float(\\[\\])*", "^double(\\[\\])*", "^char(\\[\\])*","^byte(\\[\\])*"};
		String brackets = "(\\[\\])";
		Pattern arrCount = Pattern.compile(brackets);
		
		for (int i = 0; i < params.length + 1; i++) { // +1 for returntype
			String type;
			boolean isPrimitive = false;
			if (i == params.length) {
				sb.append(")");
				type = method.getReturnType().getCanonicalName();
				if (type.equals("void")) {
					sb.append("V");
					break;
				}
				;
			} else {
				type = params[i].getCanonicalName();
			}

			Matcher counter = arrCount.matcher(type);
			while (counter.find()) {
				local.append("[");
			}
			for (int j = 0; j < 8; j++) {
				Pattern pattern = Pattern.compile(arr[j]);
				Matcher matcher = pattern.matcher(type);
				if (matcher.find()) {
					switch (j) {
					case 0:
						local.append("I");
					case 1:
						local.append("Z");
					case 2:
						local.append("J");
					case 3:
						local.append("S");
					case 4:
						local.append("F");
					case 5:
						local.append("D");
					case 6:
						local.append("C");
					case 7:
						local.append("B");
					}
					isPrimitive = true;
					break;
				}
			}
			if (!isPrimitive) {
				String classType = type.replaceAll("\\.", "/");
				String rmBrackets = classType.replaceAll(brackets, "");
				local.append("L" + rmBrackets + ";");
			}

			sb.append(local);
			local.delete(0, local.capacity());
		}
		
		return sb.toString();
	}
}
 