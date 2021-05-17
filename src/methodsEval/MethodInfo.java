package methodsEval;

import java.io.Externalizable;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.Value;

import userFields.CustomInterpreter.ArrayValue;
import userFields.CustomInterpreter.MultiDArray;
import userFields.CustomInterpreter.ObjectVal;
import userFields.CustomInterpreter.ReferenceValue;
import userFields.UserValues;

public class MethodInfo implements Serializable {
	private static final long serialVersionUID = 1L;
	private String owner;
	private String name;
	private String desc;
	private boolean isStatic; // determines do i need to parse the constructor of the next interesting method
	private boolean isInterface;
	private Map<Integer, Value> userControlledArgPos;
	private boolean isField; // if the caller is a field in the class where the method is called
	private boolean fixedType;
	
	public MethodInfo(String owner, String name, boolean isStatic, Map<Integer, Value> userControlledArgPos, String desc, boolean isField, boolean fixedType, boolean isInterface) {
		this.owner = owner;
		this.name = name;
		this.isStatic = isStatic;
		this.userControlledArgPos = userControlledArgPos;
		this.desc = desc;
		this.isField = isField;
		this.fixedType = fixedType;
		this.isInterface = isInterface;
	}
	
	public MethodInfo(String name, String desc, boolean isStatic, Map<Integer, Value> userControlledArgPos) { //for entry point only
		this.name = name;
		this.desc = desc;
		this.isField = true;
		this.fixedType = false;
		this.isStatic = isStatic;
		this.userControlledArgPos = userControlledArgPos;
	}
	
	public MethodInfo(String name, String desc) { // used for transformation to handler
		this.name = name;
		this.desc = desc;
		this.isField = true;
		this.fixedType = false;
		this.isStatic = false;
	}
	
	public ReferenceValue limitedConstructorInfluence() { // when the object the method is invoked on is a local var which is tainted when initialized
		if (!isField && userControlledArgPos.containsKey(0) && !isStatic && userControlledArgPos.get(0) instanceof ReferenceValue) 
			return (ReferenceValue) userControlledArgPos.get(0);
		return null;
	}
	
	public String getName() {
		return name;
	}
	
	public boolean getIsStatic() {
		return isStatic;
	}
	
	public Map<Integer, Value> getUserControlledArgPos() {
		return userControlledArgPos;
	}
	
	public String getOwner() {
		return owner;
	}
	
	public String getDesc() {
		return desc;
	}
	
	public boolean getIsField() {
		return isField;
	}
	
	public boolean getFixedType() {
		return fixedType;
	}
	
	public boolean canBeProxy() {
		return (isField && isInterface);
	}
	
	// Could implement better comparison between two identical tainted methods
	// First, compare whether the owner object isField,
	// Second, compare the number of tainted arguments that isField,
	// Third, compare the number of tainted arguments that is not a primitive type
	// Last, compare the number of tainted arguments
	public boolean isSub(MethodInfo other) {
		Set<Integer> keys = userControlledArgPos.keySet();
		Set<Integer> otherKeys = other.userControlledArgPos.keySet();
 		if (otherKeys.containsAll(keys)) {
 			Iterator<Integer> it = keys.iterator();
 			boolean possibleTruth = false;
 			while (it.hasNext()) {
 				int key = it.next();
				Value value = userControlledArgPos.get(key);
				Value otherValue = other.userControlledArgPos.get(key);
				if (!(value instanceof UserValues)) {
					if (value instanceof ReferenceValue) {
						if (!(otherValue instanceof ReferenceValue))
							return false;
						ReferenceValue ref = (ReferenceValue) value;
						ReferenceValue otherRef = (ReferenceValue) otherValue;
						if (ref.getIsField()) {
							if (!otherRef.getIsField())
								return false;
						} else if (ref.getInitDesc() != null) {
							if (!ref.getInitDesc().equals(otherRef.getInitDesc()))
								return false;
							else {
								if (!otherRef.getinitControlledArgPos().keySet().containsAll(ref.getinitControlledArgPos().keySet())) 
									return false;
								else {
									if (otherRef.getinitControlledArgPos().size() > ref.getinitControlledArgPos().size())
										possibleTruth = true;
								}
							}
						}
					} else if (value instanceof ArrayValue) {
						if (!(otherValue instanceof ArrayValue))
							return false;
						ArrayValue arr = (ArrayValue) value;
						ArrayValue otherArr = (ArrayValue) otherValue;
						if (arr.getIsField() && !otherArr.getIsField())
							return false;
						else if (arr.getInitDesc() != null) {
							if (!arr.getInitDesc().equals(otherArr.getInitDesc()))
								return false;
							else {
								if (!otherArr.getinitControlledArgPos().keySet().containsAll(arr.getinitControlledArgPos().keySet())) 
									return false;
								else {
									if (otherArr.getinitControlledArgPos().size() > arr.getinitControlledArgPos().size())
										possibleTruth = true;
								}
							}
						}
					} else {
						if (!(otherValue instanceof MultiDArray))
							return false;
						MultiDArray multiArr = (MultiDArray) value;
						MultiDArray otherMultiArr = (MultiDArray) otherValue;
						if (multiArr.getIsField() && !otherMultiArr.getIsField())
							return false;
						else if (multiArr.getInitDesc() != null) {
							if (!multiArr.getInitDesc().equals(otherMultiArr.getInitDesc()))
								return false;
							else {
								if (!otherMultiArr.getinitControlledArgPos().keySet().containsAll(multiArr.getinitControlledArgPos().keySet())) 
									return false;
								else {
									if (otherMultiArr.getinitControlledArgPos().size() > multiArr.getinitControlledArgPos().size())
										possibleTruth = true;
								}
							}
						}
					}
				}
 			}
 			if (possibleTruth)
 				return true;
 			else {
 				if (other.userControlledArgPos.size() > userControlledArgPos.size())
 					return true;
 				return false;
 			}
 		} else 
			return false;			
	}
	
	// not very good transformation as various argument values are condense into an array which uses the most tainted element to represemt its value
	// emphasis on isField;
	public MethodInfo transformToHandler(String invokeDesc) {
		MethodInfo handler = new MethodInfo("invoke", invokeDesc);
		Map<Integer, Value> handlerControlledArgPos = new HashMap<>();
		ReferenceValue thisHandler = new ReferenceValue(UserValues.USERINFLUENCED_REFERENCE);
		thisHandler.setField();
		ReferenceValue proxy = new ReferenceValue(UserValues.USERINFLUENCED_REFERENCE);
		proxy.setField();
		handlerControlledArgPos.put(0, thisHandler);
		handlerControlledArgPos.put(1, proxy);
		ArrayValue condensedArgs = new ArrayValue(Type.getObjectType("java/lang/Object"), UserValues.INT_VALUE);
		boolean isTainted = false;
		Iterator<Map.Entry<Integer, Value>> it = this.userControlledArgPos.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Value> arg = (Map.Entry<Integer, Value>) it.next();
			// this object already considered
			if (arg.getKey() != 0) {
				Value argVal = arg.getValue();
				if (argVal instanceof ObjectVal) {
					if (((ObjectVal) argVal).getIsField()) {
						condensedArgs.setField();
						isTainted = true;
						break;
					}
					if ((argVal instanceof ArrayValue && ((ArrayValue) argVal).isTainted()) 
							|| (argVal instanceof MultiDArray && ((MultiDArray) argVal).isTainted())
							|| (argVal instanceof ReferenceValue && ((ReferenceValue) argVal).isTainted())
							) {
						isTainted = true;
					} 
				} else {
					if (((UserValues) argVal).isTainted())
						isTainted = true;
				}
			}
		}
		if (isTainted) {
			condensedArgs.setContents(new ReferenceValue(UserValues.USERDERIVED_REFERENCE));
			handlerControlledArgPos.put(3, condensedArgs);
		}
		handler.userControlledArgPos = handlerControlledArgPos;
		return handler;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((desc == null) ? 0 : desc.hashCode());
		result = prime * result + (isField ? 1231 : 1237);
		result = prime * result + (isStatic ? 1231 : 1237);
		result = prime * result + (fixedType ? 1231 : 1237);
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((owner == null) ? 0 : owner.hashCode());
		result = prime * result + ((userControlledArgPos == null) ? 0 : userControlledArgPos.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MethodInfo other = (MethodInfo) obj;
		if (desc == null) {
			if (other.desc != null)
				return false;
		} else if (!desc.equals(other.desc))
			return false;
		if (isField != other.isField)
			return false;
		if (isStatic != other.isStatic)
			return false;
		if (fixedType != other.fixedType)
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (owner == null) {
			if (other.owner != null)
				return false;
		} else if (!owner.equals(other.owner))
			return false;
		if (userControlledArgPos == null) {
			if (other.userControlledArgPos != null)
				return false;
		} else if (!userControlledArgPos.equals(other.userControlledArgPos))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "MethodInfo [owner=" + owner + ", name=" + name + ", desc=" + desc + ", isStatic=" + isStatic
				+ ", isField=" + isField + ", fixedType=" + fixedType + ", userControlledArgPos=" + userControlledArgPos + "]";
	}
	
	public String gadgetMethodString() {
		return "MethodInfo [name=" + name + ", desc=" + desc + ", isStatic=" + isStatic
				+ ", isField=" + isField + ", fixedType=" + fixedType + ", userControlledArgPos=" + userControlledArgPos + "]";
	}

	public static int countArgs(String desc) {
		String signature = desc.replaceAll("\\)\\S+", "");
		int numOfArgs = 0;
		String prim = "[ZCBSIFJD]";
		String nonPrim = "L(\\w+/)*[\\w\\$]+;";
		Pattern pattern = Pattern.compile(nonPrim);
		Pattern primPattern = Pattern.compile(prim);
		Matcher matcher = pattern.matcher(signature);
		while (matcher.find()) {
			numOfArgs++;
		}
		String modified = signature.replaceAll(nonPrim, "");
		Matcher m = primPattern.matcher(modified);
		while (m.find()) {
			numOfArgs++;
		}
		return numOfArgs;
	}
	
	public static String convertDescriptor(Method method) {
		Class<?>[] params = method.getParameterTypes();
		StringBuffer sb = new StringBuffer("(");
		StringBuffer local = new StringBuffer();
		String[] arr = new String[] {"^int$", "^boolean$", "^long$*", "^short$", "^float$", "^double$*", "^char$","^byte$"};
		String brackets = "(\\[)";
		Pattern arrCount = Pattern.compile(brackets);
		
		for (int i = 0; i < params.length + 1; i++) { // +1 for returntype
			String type;
			if (i == params.length) {
				sb.append(")");
				type = method.getReturnType().getName();
				if (type.equals("void")) {
					sb.append("V");
					break;
				}
			} else {
				type = params[i].getName();
			}

			Matcher counter = arrCount.matcher(type);
			if (!counter.find()) {
				boolean isPrimitive = false;
				for (int j = 0; j < 8; j++) {
					Pattern pattern = Pattern.compile(arr[j]);
					Matcher matcher = pattern.matcher(type);
					if (matcher.find()) {
						switch (j) {
						case 0:
							local.append("I");
							break;
						case 1:
							local.append("Z");
							break;
						case 2:
							local.append("J");
							break;
						case 3:
							local.append("S");
							break;
						case 4:
							local.append("F");
							break;
						case 5:
							local.append("D");
							break;
						case 6:
							local.append("C");
							break;
						case 7:
							local.append("B");
							break;
						}
						isPrimitive = true;
						break;
					}
				}
				if (!isPrimitive) {
					String classType = type.replaceAll("\\.", "/");
					local.append("L" + classType + ";");
				}
			} else {
				String classType = type.replaceAll("\\.", "/");
				local.append(classType);
			}
		
			sb.append(local);
			local.delete(0, local.length());
		}
		
		return sb.toString();
	}
	
	public static boolean checkIsInputStream(MethodInfo mf) {
		String owner = mf.getOwner();
		String name = mf.getName();
		if (owner.equals("java/io/ObjectInputStream")) {
			Class<?> io = ObjectInputStream.class;
			Method[] methods = io.getDeclaredMethods();
			for (Method m : methods) {
				if (m.getName().equals(name)) {
					return true;
				}
			}
		} else if (owner.equals("java/io/Externalizable")) {
			Class<?> io = Externalizable.class;
			Method[] methods = io.getDeclaredMethods();
			for (Method m : methods) {
				if (m.getName().equals(name)) {
					return true;
				}
			}
		} else if (owner.equals("java/io/ObjectInputValidation")) {
			if (name.equals("validateObject")) {
				return true;
			}
		} else if (owner.equals("java/io/ObjectInputStream$GetField")) {
			if (name.equals("get")) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean propagateIsField(final List<? extends Value> values) {
		for (int i = 1; i < values.size(); i++) {
			Value value = values.get(i);
			if (value instanceof UserValues) {
				if (!((UserValues) value).isInfluenced())
					return false;
			} else if (value instanceof ReferenceValue) {
				if (!((ReferenceValue) value).getIsField()) 
					return false;
			} else if (value instanceof ArrayValue) {
				if (!((ArrayValue) value).contentStatus()) 
					return false;
			} else {
				if (!((MultiDArray) value).contentStatus()) 
					return false;
			}
		}
		return true;
	}
}