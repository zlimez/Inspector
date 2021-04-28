package userFields;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;

import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

import methodsEval.MethodInfo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

/*
 * values circulating in the interpreter UserValues, Reference, MulitDArray and Array values
 * hence for all values that will be primitive types can be casted to UserValues 
 */

public class CustomInterpreter extends Interpreter<Value> implements Opcodes {
	public static final Type NULL_TYPE = Type.getObjectType("null");
	
	private String owner;
	private Map<String, Value> userControlledFields = new HashMap<>();
	private boolean isMagicMethod;
	private Map<Integer, Value> userControlledArgPos;
	private int counter = 0;
	// used to check for duplicate tainted method already exists in the map
	private HashMap<String, List<MethodInfo>> nextInvokedMethods = new HashMap<>();
	

	public CustomInterpreter() {
		super(ASM9);
		if (getClass() != CustomInterpreter.class) {
			throw new IllegalStateException();
		}
	}

	public CustomInterpreter(String owner, boolean isMagicMethod, Map<Integer, Value> userControlledArgPos) {
		super(ASM9);
		this.owner = owner;
		this.isMagicMethod = isMagicMethod;
		this.userControlledArgPos = userControlledArgPos;
	}

	protected CustomInterpreter(final int api) {
		super(api);
	}

	/* 
	 * definition to evaluate USER_INFLUENCED | USERCONTROLLED
	 * let all possible expected results from an expression (bytecode instruction) be set R
	 * if restricting user's freedom to control the input into an expression reduces the number of outcomes to a subset of R, S
	 * I conclude the outcome to be user derived if not where the set of outcomes remains the same to be USER_INFLUENCED
	 */

	public Map<String, Value> getUserControlledFieldsForFrame() {
		return userControlledFields;
	} 

	// When filtering the map to obtain the final userControlledFields remove fields that do not belong to this class
	public Map<String, Value> getUserControlledFields() {
		Iterator<Map.Entry<String, Value>> it = userControlledFields.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Value> field = (Map.Entry<String, Value>) it.next();
			Value value = field.getValue();
			if ((value instanceof UserValues && ((UserValues) value).isTainted()) || (value instanceof ArrayValue && ((ArrayValue) value).isTainted()) || (value instanceof MultiDArray && ((MultiDArray) value).isTainted()) || (value instanceof ReferenceValue && ((ReferenceValue) value).isTainted()) && field.getKey().startsWith(owner + "%")) { 
			} else 
				it.remove();	
		} 
		return userControlledFields;
	}

	public Collection<MethodInfo> getNextInvokedMethods() {
		Collection<MethodInfo> allTaintedMethods = new ArrayList<>();
		nextInvokedMethods.values().forEach((sublist) -> {
			allTaintedMethods.addAll(sublist);
		});
		return allTaintedMethods;
	}

	public void setUserControlledFields(Map<String, Value> controlledFields) {
		this.userControlledFields = controlledFields;
	}

	/*
	 * Assume the array is already occupied by at least one element
	 */
	public Value arrayInit(Type type, int option) {
		String arrSig = "\\[\\]";
		Pattern pattern = Pattern.compile(arrSig);
		Matcher matcher = pattern.matcher(type.getClassName());
		int dim = 0;
		while (matcher.find()) {
			dim++;
		}
		Value element = null;
		Type arrType;
		switch (type.getElementType().getSort()) {
		case Type.BOOLEAN:
		case Type.CHAR:
		case Type.BYTE:
		case Type.SHORT:
		case Type.INT:
			if (option == 1) {
				element = UserValues.INT_VALUE;
			} else if (option == 2) {
				element = UserValues.USERDERIVED_INT;
			} else 
				element = UserValues.USERINFLUENCED_INT;
			
			arrType = Type.INT_TYPE;
			break;
		case Type.FLOAT:
			if (option == 1) {
				element = UserValues.FLOAT_VALUE;
			} else if (option == 2) {
				element = UserValues.USERDERIVED_FLOAT;
			} else 
				element = UserValues.USERINFLUENCED_FLOAT;
			arrType = Type.FLOAT_TYPE;
			break;
		case Type.LONG:
			if (option == 1) {
				element = UserValues.LONG_VALUE;
			} else if (option == 2) {
				element = UserValues.USERDERIVED_LONG;
			} else 
				element = UserValues.USERINFLUENCED_LONG;
			arrType = Type.LONG_TYPE;
			break;
		case Type.DOUBLE:
			if (option == 1) {
				element = UserValues.DOUBLE_VALUE;
			} else if (option == 2) {
				element = UserValues.USERDERIVED_DOUBLE;
			} else 
				element = UserValues.USERINFLUENCED_DOUBLE;
			arrType = Type.DOUBLE_TYPE;
			break;
		case Type.OBJECT:
			if (option == 1) {
				element = new ReferenceValue(UserValues.REFERENCE_VALUE);
			} else if (option == 2) {
				element = new ReferenceValue(UserValues.USERDERIVED_REFERENCE);
			} else 
				element = new ReferenceValue(UserValues.USERINFLUENCED_REFERENCE);
			arrType = Type.getObjectType("java/lang/Object");
			break;
		default:
			throw new AssertionError("Unknown element type");
		}
		if (dim == 1) {
			if (option == 1) {
				ArrayValue arr = new ArrayValue(arrType, UserValues.INT_VALUE);
				arr.setContents(element);
				return arr;
			} else if (option == 2) {
				ArrayValue arr = new ArrayValue(arrType, UserValues.USERDERIVED_INT);
				arr.setContents(element);
				return arr;
			} else {
				ArrayValue arr = new ArrayValue(arrType, UserValues.USERINFLUENCED_INT);
				arr.setContents(element);
				if (element instanceof ReferenceValue)
					arr.setField();
				return arr;
			}	
		} else {
			MultiDArray multiArr = new MultiDArray(arrType, dim, null);
			MultiDArray currentD = multiArr;
			if (option == 1) {
				for (int i = 0; i < dim; i++) {
					currentD.indivLength = UserValues.INT_VALUE;
					currentD = currentD.nested;
				}
				MultiDArray.setEntireContent(multiArr, 3, UserValues.INT_VALUE);
			} else if (option == 2) {
				for (int j = 0; j < dim; j++) {
					currentD.indivLength = UserValues.USERDERIVED_INT;
					currentD = currentD.nested;
				}
				MultiDArray.setEntireContent(multiArr, 3, UserValues.USERDERIVED_INT);
			} else {
				for (int j = 0; j < dim; j++) {
					currentD.indivLength = UserValues.USERINFLUENCED_INT;
					currentD = currentD.nested;
				}
				MultiDArray.setEntireContent(multiArr, 3, UserValues.USERINFLUENCED_INT);
				if (element instanceof ReferenceValue)
					multiArr.setField();
			}	
			MultiDArray.setEntireContent(multiArr, 1, element);
			return multiArr;
		}
	}

	public Value returnValueInit(Type returnType, boolean isField) {
		switch (returnType.getSort()) {
		case Type.VOID:
			return null;
		case Type.BOOLEAN:
		case Type.CHAR:
		case Type.BYTE:
		case Type.SHORT:
		case Type.INT:
			return UserValues.USERDERIVED_INT;
		case Type.FLOAT:
			return UserValues.USERDERIVED_FLOAT;
		case Type.LONG:
			return UserValues.USERDERIVED_LONG;
		case Type.DOUBLE:
			return UserValues.USERDERIVED_DOUBLE;
		case Type.ARRAY:
			// Assume all length content etc. of the returned array is user derived isTainted = true
			Value arr = arrayInit(returnType, 2);
			if (isField) {
				if (arr instanceof ArrayValue) {
					((ArrayValue) arr).setField();
				} else 
					((MultiDArray) arr).setField();
			} 
			return arr;
		case Type.OBJECT:
			ReferenceValue ref = new ReferenceValue(UserValues.USERDERIVED_REFERENCE);
			if (isField)
				ref.setField();
			return ref;
		default:
			throw new AssertionError("Return value initialization error");
		}
	}

	public Value ioReturnValue(Type returnType) {
		switch (returnType.getSort()) {
		case Type.VOID:
			return null;
		case Type.BOOLEAN:
		case Type.CHAR:
		case Type.BYTE:
		case Type.SHORT:
		case Type.INT:
			return UserValues.USERINFLUENCED_INT;
		case Type.FLOAT:
			return UserValues.USERINFLUENCED_FLOAT;
		case Type.LONG:
			return UserValues.USERINFLUENCED_LONG;
		case Type.DOUBLE:
			return UserValues.USERINFLUENCED_DOUBLE;
		case Type.ARRAY:
			// Assume all length content etc. of the returned array is user derived isTainted = true
			return arrayInit(returnType, 3); 
		case Type.OBJECT:
			ReferenceValue ioRef = new ReferenceValue(UserValues.USERINFLUENCED_REFERENCE);
			ioRef.setField();
			return ioRef;
		default:
			throw new AssertionError("IO return value initialization error");
		}
	}

	@Override
	public Value newEmptyValue(final int local) {
		return UserValues.UNINITIALIZED_VALUE;
	}

	public Value newValue(final Type type) {
		if (userControlledArgPos.containsKey(counter)) {
			counter++;
			return userControlledArgPos.get(counter - 1);
		}
		counter++;

		if (type == null) {
			return UserValues.UNINITIALIZED_VALUE;
		}
		switch (type.getSort()) {
		case Type.VOID:
			return null;
		case Type.BOOLEAN:
		case Type.CHAR:
		case Type.BYTE:
		case Type.SHORT:
		case Type.INT:
			return UserValues.INT_VALUE;
		case Type.FLOAT:
			return UserValues.FLOAT_VALUE;
		case Type.LONG:
			return UserValues.LONG_VALUE;
		case Type.DOUBLE:
			return UserValues.DOUBLE_VALUE;
		case Type.ARRAY:
			return arrayInit(type, 1);
		case Type.OBJECT:
			return new ReferenceValue(UserValues.REFERENCE_VALUE);
		default:
			throw new AssertionError();
		}
	}

	@Override
	public Value newOperation(final AbstractInsnNode insn) throws AnalyzerException {
		switch (insn.getOpcode()) {
		case ACONST_NULL:
			return newValue(NULL_TYPE);
		case ICONST_M1:
		case ICONST_0:
		case ICONST_1:
		case ICONST_2:
		case ICONST_3:
		case ICONST_4:
		case ICONST_5:
			return UserValues.INT_VALUE;
		case LCONST_0:
		case LCONST_1:
			return UserValues.LONG_VALUE;
		case FCONST_0:
		case FCONST_1:
		case FCONST_2:
			return UserValues.FLOAT_VALUE;
		case DCONST_0:
		case DCONST_1:
			return UserValues.DOUBLE_VALUE;
		case BIPUSH:
		case SIPUSH:
			return UserValues.INT_VALUE;
		case LDC:
			Object value = ((LdcInsnNode) insn).cst;
			if (value instanceof Integer) {
				return UserValues.INT_VALUE;
			} else if (value instanceof Float) {
				return UserValues.FLOAT_VALUE;
			} else if (value instanceof Long) {
				return UserValues.LONG_VALUE;
			} else if (value instanceof Double) {
				return UserValues.DOUBLE_VALUE;
			} else if (value instanceof String) {
				return newValue(Type.getObjectType("java/lang/String"));
			} else if (value instanceof Type) {
				int sort = ((Type) value).getSort();
				if (sort == Type.OBJECT) {
					return newValue(Type.getObjectType("java/lang/Class"));
				} else if (sort == Type.ARRAY) {
					return newValue((Type) value);
				} else if (sort == Type.METHOD) {
					return newValue(Type.getObjectType("java/lang/invoke/MethodType"));
				} else {
					throw new AnalyzerException(insn, "Illegal LDC value " + value);
				}
			} else if (value instanceof Handle) {
				return newValue(Type.getObjectType("java/lang/invoke/MethodHandle"));
			} else if (value instanceof ConstantDynamic) {
				return newValue(Type.getType(((ConstantDynamic) value).getDescriptor()));
			} else {
				throw new AnalyzerException(insn, "Illegal LDC value " + value);
			}
		case JSR:
			return UserValues.RETURNADDRESS_VALUE;
		case GETSTATIC:
			FieldInsnNode fieldInsn = (FieldInsnNode) insn;
			String fieldName = fieldInsn.owner + "%" + fieldInsn.name;
			Type fieldType = Type.getType(fieldInsn.desc);
			if (userControlledFields.containsKey(fieldName)) {
				return userControlledFields.get(fieldName);
			} else if (fieldType.getSort() == Type.OBJECT) { 
				ReferenceValue ref = new ReferenceValue(UserValues.REFERENCE_VALUE);
				return ref;
			} else if (fieldType.getSort() == Type.ARRAY) {
				return arrayInit(fieldType, 1);
			}
			return newValue(Type.getType(fieldInsn.desc));
		case NEW:
			return new ReferenceValue(UserValues.REFERENCE_VALUE); // though the value is thus far empty init will follow immediately folloed whcih will then determine whether it stay as such or become derived
		default:
			throw new AssertionError();
		}
	}

	@Override
	public Value copyOperation(final AbstractInsnNode insn, final Value value) throws AnalyzerException {
		return value;
	}

	@Override
	public Value unaryOperation(final AbstractInsnNode insn, final Value value) throws AnalyzerException {
		switch (insn.getOpcode()) {
		case INEG:
		case IINC:
		case L2I:
		case F2I:
		case D2I:
		case I2B:
		case I2C:
		case I2S:
			if (!(value instanceof UserValues)) {
				throw new AnalyzerException(insn, "Unary numeric type absent");
			}
			if (((UserValues) value).isTainted()) {
				if (((UserValues) value).isInfluenced())
					return UserValues.USERINFLUENCED_INT;
				return UserValues.USERDERIVED_INT;
			}
			return UserValues.INT_VALUE;
		case FNEG:
		case I2F:
		case L2F:
		case D2F:
			if (!(value instanceof UserValues)) {
				throw new AnalyzerException(insn, "Unary numeric type absent");
			}
			if (((UserValues) value).isTainted()) {
				if (((UserValues) value).isInfluenced())
					return UserValues.USERINFLUENCED_FLOAT;
				return UserValues.USERDERIVED_FLOAT;
			}
			return UserValues.FLOAT_VALUE;
		case LNEG:
		case I2L:
		case F2L:
		case D2L:
			if (!(value instanceof UserValues)) {
				throw new AnalyzerException(insn, "Unary numeric type absent");
			}
			if (((UserValues) value).isTainted()) {
				if (((UserValues) value).isInfluenced())
					return UserValues.USERINFLUENCED_LONG;
				return UserValues.USERDERIVED_LONG;
			}
			return UserValues.LONG_VALUE;
		case DNEG:
		case I2D:
		case L2D:
		case F2D:
			if (!(value instanceof UserValues)) {
				throw new AnalyzerException(insn, "Unary numeric type absent");
			}
			if (((UserValues) value).isTainted()) {
				if (((UserValues) value).isInfluenced())
					return UserValues.USERINFLUENCED_DOUBLE;
				return UserValues.USERDERIVED_DOUBLE;
			}
			return UserValues.DOUBLE_VALUE;
		case IFEQ:
		case IFNE:
		case IFLT:
		case IFGE:
		case IFGT:
		case IFLE:
		case TABLESWITCH:
		case LOOKUPSWITCH:
		case IRETURN:
		case LRETURN:
		case FRETURN:
		case DRETURN:
		case ARETURN:
			return null;
		case PUTSTATIC:
			FieldInsnNode staticFieldInsn = (FieldInsnNode) insn;
			String staticFieldName = staticFieldInsn.owner + "%" + staticFieldInsn.name;
			if ((value instanceof UserValues && ((UserValues) value).isTainted()) || value instanceof ArrayValue || value instanceof MultiDArray || value instanceof ReferenceValue) {
				userControlledFields.put(staticFieldName, value);
			}
			return null;
		case GETFIELD:
			FieldInsnNode fieldInsn = (FieldInsnNode) insn;
			String fieldName = fieldInsn.owner + "%" + fieldInsn.name;
			if (userControlledFields.containsKey(fieldName)) {
				return userControlledFields.get(fieldName);
			} else if (!fieldName.startsWith(owner + "%")) {
				// fields of object not of this class
				ReferenceValue ref = (ReferenceValue) value;
				if (ref.value == UserValues.USERINFLUENCED_REFERENCE) {
					// field of an object which is a field in this class
					return ioReturnValue(Type.getType(fieldInsn.desc));
				} else if (ref.isField) {
					return returnValueInit(Type.getType(fieldInsn.desc), true);
				} else if (ref.isTainted()) 
					return returnValueInit(Type.getType(fieldInsn.desc), false);
			} 
			return newValue(Type.getType(fieldInsn.desc));
		case NEWARRAY:
			int operand = ((IntInsnNode) insn).operand;
			Type type;
			switch (operand) {
			case T_BOOLEAN:
			case T_CHAR:
			case T_BYTE:
			case T_SHORT:
			case T_INT:
				type = Type.INT_TYPE;
				break;
			case T_FLOAT:
				type = Type.FLOAT_TYPE;
				break;
			case T_LONG:
				type = Type.LONG_TYPE;
				break;
			case T_DOUBLE:
				type = Type.DOUBLE_TYPE;
				break;
			default:
				throw new AnalyzerException(insn, "Invalid operand for NEWARRAY");
			}
			ArrayValue arr = new ArrayValue(type, (UserValues) value);
			return arr;
		case ANEWARRAY:
			ArrayValue refArr = new ArrayValue(Type.getObjectType("java/lang/Object"), (UserValues) value);
			return refArr;
		case ARRAYLENGTH:
			if (value instanceof ArrayValue) {
				ArrayValue array = (ArrayValue) value;
				return array.length;
			}    	  
			if (value instanceof MultiDArray) {
				MultiDArray multiArr = (MultiDArray) value;
				return multiArr.indivLength;
			}
			throw new AnalyzerException(insn, "Expected array for arraylength call absent");
		case ATHROW:
			return null;
		case CHECKCAST:
			if (!(value instanceof ReferenceValue || value instanceof ArrayValue || value instanceof MultiDArray))
				throw new AnalyzerException(insn, "Casted value is not a reference or array value");
			Type desc = Type.getObjectType(((TypeInsnNode) insn).desc);
			if (value instanceof ReferenceValue) {
				ReferenceValue ref = (ReferenceValue) value;
				if (ref.isTainted()) {
					if (desc.getSort() == Type.ARRAY) {
						if (ref.value == UserValues.USERINFLUENCED_REFERENCE) {
							return arrayInit(desc, 3);
						} else 
							return arrayInit(desc, 2);
					}
					return value;
				}
			} else if (value instanceof ArrayValue || value instanceof MultiDArray) {
				if (desc.getSort() == Type.ARRAY) 
					return value;
			}
			return newValue(desc);
		case INSTANCEOF:
			return UserValues.INT_VALUE;
		case MONITORENTER:
		case MONITOREXIT:
		case IFNULL:
		case IFNONNULL:
			return null;
		default:
			throw new AssertionError();
		}
	}

	@Override
	public Value binaryOperation(final AbstractInsnNode insn, final Value value1, final Value value2)throws AnalyzerException {
		switch (insn.getOpcode()) {
		case IALOAD:
		case BALOAD:
		case CALOAD:
		case SALOAD:
		case FALOAD:
		case DALOAD:
		case LALOAD:
			if (!(value2 instanceof UserValues) || ((UserValues) value2).typecode != 1)
				throw new AnalyzerException(insn, "Array index not of int type");
			UserValues index = (UserValues) value2;
			if (value1 instanceof ArrayValue) {
				ArrayValue arr = (ArrayValue) value1;
				Value contents = arr.contents;
				if (contents instanceof UserValues && ((UserValues) contents).isTainted()) {
					UserValues con = (UserValues) contents;
					if (index.isTainted()) {
						if (con.isInfluenced() && index.isInfluenced()) 
							return contents;
					}
					if (contents == UserValues.USERINFLUENCED_INT || contents == UserValues.USERDERIVED_INT) {
						return UserValues.USERDERIVED_INT;
					} else if (contents == UserValues.USERINFLUENCED_FLOAT || contents == UserValues.USERDERIVED_FLOAT) {
						return UserValues.USERDERIVED_FLOAT;
					} else if (contents == UserValues.USERINFLUENCED_LONG || contents == UserValues.USERDERIVED_LONG) {
						return UserValues.USERDERIVED_LONG;
					} else 
						return UserValues.USERDERIVED_DOUBLE;
				} 
				if (index.isTainted()) {
					if (contents == UserValues.INT_VALUE) {
						return UserValues.USERDERIVED_INT;
					} else if (contents == UserValues.FLOAT_VALUE) {
						return UserValues.USERDERIVED_FLOAT;
					} else if (contents == UserValues.DOUBLE_VALUE) {
						return UserValues.USERDERIVED_DOUBLE;
					} else 
						return UserValues.USERDERIVED_LONG;	  
				}
				return contents;	  
			} else if (value1 instanceof MultiDArray) {
				MultiDArray multiArr = (MultiDArray) value1;
				if (multiArr.dim == 1) {
					Value contents = multiArr.content;
					if (contents instanceof UserValues && ((UserValues) contents).isTainted()) {
						UserValues con = (UserValues) contents;
						if (index.isTainted()) {
							if (con.isInfluenced() && index.isInfluenced()) {
								return contents;
							}
						}
						if (contents == UserValues.USERINFLUENCED_INT || contents == UserValues.USERDERIVED_INT) {
							return UserValues.USERDERIVED_INT;
						} else if (contents == UserValues.USERINFLUENCED_FLOAT || contents == UserValues.USERDERIVED_FLOAT) {
							return UserValues.USERDERIVED_FLOAT;
						} else if (contents == UserValues.USERINFLUENCED_LONG || contents == UserValues.USERDERIVED_LONG) {
							return UserValues.USERDERIVED_LONG;
						} else
							return UserValues.USERDERIVED_DOUBLE;
					} 
					if (index.isTainted()) {
						if (contents == UserValues.INT_VALUE) {
							return UserValues.USERDERIVED_INT;
						} else if (contents == UserValues.FLOAT_VALUE) {
							return UserValues.USERDERIVED_FLOAT;
						} else if (contents == UserValues.DOUBLE_VALUE) {
							return UserValues.USERDERIVED_DOUBLE;
						} else 
							return UserValues.USERDERIVED_LONG;
					}
					return contents;
				} else 
					throw new AnalyzerException(insn, "Multidimensional array should have only one dimension left");			
			} else {
				throw new AnalyzerException(insn, "Expected numeric array absent");  
			}
		case AALOAD:
			if (!(value2 instanceof UserValues) || ((UserValues) value2).typecode != 1)
				throw new AnalyzerException(insn, "Array index not of int type");
			UserValues indexA = (UserValues) value2;
			// Derived or influenced in array don't matter as the most influenced element is used to represent the array 
			// Whether the index is derived or influenced ignored in this situation
			if (value1 instanceof ArrayValue) {
				ArrayValue arr = (ArrayValue) value1;
				if (!(arr.type.equals(Type.getObjectType("java/lang/Object"))))
					throw new AnalyzerException(insn, "AALOAD should load object reference");
				ReferenceValue contents = (ReferenceValue) arr.contents;
				ReferenceValue selected; 
				if (contents != null && contents.isTainted()) {
					if (indexA.isTainted()) {
						if (arr.isField) { // when index is derived the element that isField might not be selected but to ensure overestimation and aligning the fact that when isField = true reference must be user influenced, an influenced and isField refValue is returned
							selected = new ReferenceValue(UserValues.USERINFLUENCED_REFERENCE);
							selected.setField();
							return selected;
						} else if (arr.actualType != null) {
							selected = new ReferenceValue(UserValues.USERDERIVED_REFERENCE);
							selected.actualType = arr.actualType;
							selected.constructorDesc = arr.constructorDesc;
							selected.initControlledPos = arr.initControlledPos;
							return selected;
						} 		
					}
					// mock count of the ratio of isField elements to not isField elements and ratio of isField + constructed elements to those that are not
					return new ReferenceValue(UserValues.USERDERIVED_REFERENCE); 
				} 
				if (indexA.isTainted()) 
					return new ReferenceValue(UserValues.USERDERIVED_REFERENCE);
				
				return new ReferenceValue(UserValues.REFERENCE_VALUE);
			} else if (value1 instanceof MultiDArray) {
				MultiDArray multiArr = (MultiDArray) value1;
				if (multiArr.dim == 1) {
					if (!(multiArr.type.equals(Type.getObjectType("java/lang/Object")))) 
						throw new AnalyzerException(insn, "Element of array should be a reference");
					ReferenceValue con = (ReferenceValue) multiArr.content;
					ReferenceValue selected;
					if (con != null && con.isTainted()) {
						if (indexA.isTainted()) {
							if (multiArr.isField) { 
								selected = new ReferenceValue(UserValues.USERINFLUENCED_REFERENCE);
								selected.setField();
								return selected;
							} else if (multiArr.actualType != null) {
								selected = new ReferenceValue(UserValues.USERDERIVED_REFERENCE);
								selected.actualType = multiArr.actualType;
								selected.constructorDesc = multiArr.constructorDesc;
								selected.initControlledPos = multiArr.initControlledPos;
								return selected;
							} 		
						}
						return new ReferenceValue(UserValues.USERDERIVED_REFERENCE);
					} 
					if (indexA.isTainted()) 
						return new ReferenceValue(UserValues.USERDERIVED_REFERENCE);	
					
					return new ReferenceValue(UserValues.REFERENCE_VALUE);
				} else 
					return multiArr.nested;		
			} else {
				throw new AnalyzerException(insn, "Expected reference array absent");  
			}
		case IADD:
		case ISUB:
		case IXOR:
			if (((UserValues) value1).typecode != 1 || ((UserValues) value2).typecode != 1)
				throw new AnalyzerException(insn, "Expected integer type absent");
			if (value1 == UserValues.USERINFLUENCED_INT || value2 == UserValues.USERINFLUENCED_INT) {
				return UserValues.USERINFLUENCED_INT;
			} else if (value1 == UserValues.USERDERIVED_INT || value2 == UserValues.USERDERIVED_INT) {
				return UserValues.USERDERIVED_INT;
			}
			return UserValues.INT_VALUE;
		case FADD:
		case FSUB:
		case FMUL:
		case FDIV:
			if (((UserValues) value1).typecode != 2 || ((UserValues) value2).typecode != 2)
				throw new AnalyzerException(insn, "Expected float type absent");
			if (value1 == UserValues.USERINFLUENCED_FLOAT || value2 == UserValues.USERINFLUENCED_FLOAT) {
				return UserValues.USERINFLUENCED_FLOAT;
			} else if (value1 == UserValues.USERDERIVED_FLOAT || value2 == UserValues.USERDERIVED_FLOAT) {
				return UserValues.USERDERIVED_FLOAT;
			}
			return UserValues.FLOAT_VALUE;
		case LADD:
		case LSUB:
		case LMUL:
		case LDIV:
		case LXOR:
			if (((UserValues) value1).typecode != 3 || ((UserValues) value2).typecode != 3)
				throw new AnalyzerException(insn, "Expected long type absent");
			if (value1 == UserValues.USERINFLUENCED_LONG || value2 == UserValues.USERINFLUENCED_LONG) {
				return UserValues.USERINFLUENCED_LONG;
			} else if (value1 == UserValues.USERDERIVED_LONG || value2 == UserValues.USERDERIVED_LONG) {
				return UserValues.USERDERIVED_LONG;
			}
			return UserValues.LONG_VALUE;
		case DADD:
		case DSUB:      
		case DMUL:
		case DDIV:
			if (((UserValues) value1).typecode != 4 || ((UserValues) value2).typecode != 4)
				throw new AnalyzerException(insn, "Expected double type absent");
			if (value1 == UserValues.USERINFLUENCED_DOUBLE || value2 == UserValues.USERINFLUENCED_DOUBLE) {
				return UserValues.USERINFLUENCED_DOUBLE;
			} else if (value1 == UserValues.USERDERIVED_DOUBLE || value2 == UserValues.USERDERIVED_DOUBLE) {
				return UserValues.USERDERIVED_DOUBLE;
			}
			return UserValues.DOUBLE_VALUE;
		case IMUL:
		case IDIV:
		case IREM:
		case ISHL:
		case ISHR:
		case IUSHR:
		case IAND:
		case IOR:
			if (((UserValues) value1).typecode != 1 || ((UserValues) value2).typecode != 1)
				throw new AnalyzerException(insn, "Expected integer type absent");
			if (value1 == UserValues.USERINFLUENCED_INT && value2 == UserValues.USERINFLUENCED_INT) {
				return UserValues.USERINFLUENCED_INT;
			} else if (value1 == UserValues.USERDERIVED_INT || value2 == UserValues.USERDERIVED_INT || value1 == UserValues.USERINFLUENCED_INT || value2 == UserValues.USERINFLUENCED_INT) { // can be made more specific for taint analysis
				return UserValues.USERDERIVED_INT;
			}
			return UserValues.INT_VALUE;
		case FREM:
			if (((UserValues) value1).typecode != 2 || ((UserValues) value2).typecode != 2)
				throw new AnalyzerException(insn, "Expected float type absent");
			if (value1 == UserValues.USERINFLUENCED_FLOAT && value2 == UserValues.USERINFLUENCED_FLOAT) {
				return UserValues.USERINFLUENCED_FLOAT;
			} else if (value1 == UserValues.USERDERIVED_FLOAT || value2 == UserValues.USERDERIVED_FLOAT || value1 == UserValues.USERINFLUENCED_FLOAT || value2 == UserValues.USERINFLUENCED_FLOAT) { // can be made more specific for taint analysis
				return UserValues.USERDERIVED_FLOAT;
			}
			return UserValues.FLOAT_VALUE; 
		case LREM:
		case LAND:
		case LOR:
			if (((UserValues) value1).typecode != 3 || ((UserValues) value2).typecode != 3)
				throw new AnalyzerException(insn, "Expected long type absent");
			if (value1 == UserValues.USERINFLUENCED_LONG && value2 == UserValues.USERINFLUENCED_LONG) {
				return UserValues.USERINFLUENCED_LONG;
			} else if (value1 == UserValues.USERDERIVED_LONG || value2 == UserValues.USERDERIVED_LONG || value1 == UserValues.USERINFLUENCED_LONG || value2 == UserValues.USERINFLUENCED_LONG) { // can be made more specific for taint analysis
				return UserValues.USERDERIVED_LONG;
			}
			return UserValues.LONG_VALUE;
		case LSHL:
		case LSHR:
		case LUSHR:
			if (((UserValues) value1).typecode != 3 || ((UserValues) value2).typecode != 1)
				throw new AnalyzerException(insn, "Expected long or int type absent");
			if (value1 == UserValues.USERINFLUENCED_LONG && value2 == UserValues.USERINFLUENCED_LONG) {
				return UserValues.USERINFLUENCED_LONG;
			} else if (value1 == UserValues.USERDERIVED_LONG || value2 == UserValues.USERDERIVED_LONG || value1 == UserValues.USERINFLUENCED_LONG || value2 == UserValues.USERINFLUENCED_LONG) { // can be made more specific for taint analysis
				return UserValues.USERDERIVED_LONG;
			}
			return UserValues.LONG_VALUE;
		case DREM:
			if (((UserValues) value1).typecode != 4 || ((UserValues) value2).typecode != 4)
				throw new AnalyzerException(insn, "Expected double type absent");
			if (value1 == UserValues.USERINFLUENCED_DOUBLE && value2 == UserValues.USERINFLUENCED_DOUBLE) {
				return UserValues.USERINFLUENCED_DOUBLE;
			} else if (value1 == UserValues.USERDERIVED_DOUBLE || value2 == UserValues.USERDERIVED_DOUBLE || value1 == UserValues.USERINFLUENCED_DOUBLE || value2 == UserValues.USERINFLUENCED_DOUBLE) { // can be made more specific for taint analysis
				return UserValues.USERDERIVED_DOUBLE;
			}
			return UserValues.DOUBLE_VALUE;
		case LCMP:
		case FCMPL:
		case FCMPG:
		case DCMPL:
		case DCMPG:
			if (((UserValues) value1).typecode != ((UserValues) value2).typecode) 
				throw new AnalyzerException(insn, "Cannot compare two different numeric types");
			boolean minDerived = false;
			if (value1 instanceof UserValues && ((UserValues) value1).isTainted()) {
				if (((UserValues) value1).isInfluenced()) {
					return UserValues.USERINFLUENCED_INT;
				}
				minDerived = true;
			}
			if (value2 instanceof UserValues && ((UserValues) value2).isTainted()) {
				if (((UserValues) value2).isInfluenced()) {
					return UserValues.USERINFLUENCED_INT;
				}
				return UserValues.USERDERIVED_INT;
			}
			if (minDerived) {
				return UserValues.USERDERIVED_INT;
			}
			return UserValues.INT_VALUE;
		case IF_ICMPEQ:
		case IF_ICMPNE:
		case IF_ICMPLT:
		case IF_ICMPGE:
		case IF_ICMPGT:
		case IF_ICMPLE:
		case IF_ACMPEQ:
		case IF_ACMPNE:
			return null;
		case PUTFIELD:
			FieldInsnNode fieldInsn = (FieldInsnNode) insn;
			String fieldName = fieldInsn.owner + "%" + fieldInsn.name;
			if ((value2 instanceof UserValues && ((UserValues) value2).isTainted()) || value2 instanceof ArrayValue || value2 instanceof MultiDArray || value2 instanceof ReferenceValue) {
				userControlledFields.put(fieldName, value2);
			}
			return null;
		default:
			throw new AssertionError();
		}
	}
	
	/*
	 * (Flaw similar to the memory address problem) Subarray that contains object or array references and exists in more than one parent mulitDArray
	 * exists as mulitple independent subarrays within the parent multiDArrays losing their alias connection  
	 */
	@Override
	public Value ternaryOperation(final AbstractInsnNode insn, final Value value1, final Value value2, final Value value3) throws AnalyzerException {
		if (value1 instanceof ArrayValue) {
			ArrayValue arr = (ArrayValue) value1;
			arr.setContents(value3);

			if (value3 instanceof UserValues) {
				if (arr.type != ((UserValues) value3).getType())
					throw new AnalyzerException(insn, "Array type mismatch with element type");
			} else if (value3 instanceof ReferenceValue) {
				if (!arr.type.equals(Type.getObjectType("java/lang/Object")))
					throw new AnalyzerException(insn, "Array type mismatch with element type");
			}

			if (arr.isField == false) {
				if (value3 instanceof ObjectVal) {
					ObjectVal element = (ObjectVal) value3;
					if (element.isField) {
						arr.isField = true;
						arr.actualType = null;
						arr.constructorDesc = null;
						arr.initControlledPos = null;
					} else if (element.constructorDesc != null) {
						if (arr.initControlledPos == null || element.initControlledPos.size() > arr.initControlledPos.size()) {
							arr.actualType = element.actualType;
							arr.constructorDesc = element.constructorDesc;
							arr.initControlledPos = element.initControlledPos;
						}
					}
				}
			}
		} else if (value1 instanceof MultiDArray) {
			MultiDArray multiArr = (MultiDArray) value1;
			if (value3 instanceof ArrayValue) {
				ArrayValue arr = (ArrayValue) value3;
				MultiDArray.setEntireContent(multiArr, 1, arr.contents);
				if (!arr.type.equals(multiArr.type))
					throw new AnalyzerException(insn, "Array type mismatch with element type");
				if (arr.isField && !multiArr.isField) {
					MultiDArray.setEntireContent(multiArr, 2, null);
					MultiDArray.setAllInitDescAndPos(multiArr, null, null, null);
				} else if (multiArr.isField == false && multiArr.type.equals(Type.getObjectType("java/lang/Object"))) {
					if (arr.constructorDesc != null)
						MultiDArray.setAllInitDescAndPos(multiArr, arr.actualType, arr.constructorDesc, arr.initControlledPos);
				}				
			} else if (value3 instanceof MultiDArray) {
				MultiDArray smMultiArr = (MultiDArray) value3;
				MultiDArray.setEntireContent(multiArr, 1, smMultiArr.content);
				if (!smMultiArr.type.equals(multiArr.type))
					throw new AnalyzerException(insn, "Array type mismatch with element type");
				if (smMultiArr.isField && !multiArr.isField) {
					MultiDArray.setEntireContent(multiArr, 2, null);
					MultiDArray.setAllInitDescAndPos(multiArr, null, null, null);
				} else if (multiArr.isField == false && multiArr.type.equals(Type.getObjectType("java/lang/Object"))) {
					if (smMultiArr.constructorDesc != null)
						MultiDArray.setAllInitDescAndPos(multiArr, smMultiArr.actualType, smMultiArr.constructorDesc, smMultiArr.initControlledPos);
				}
			} else {
				if (value3 instanceof UserValues) {
					if (multiArr.type != ((UserValues) value3).getType())
						throw new AnalyzerException(insn, "Array type mismatch with element type");
				} else if (value3 instanceof ReferenceValue) {
					if (!multiArr.type.equals(Type.getObjectType("java/lang/Object")))
						throw new AnalyzerException(insn, "Array type mismatch with element type");
				}
				MultiDArray.setEntireContent(multiArr, 1, value3);
				if (multiArr.isField == false && value3 instanceof ReferenceValue) {
					ReferenceValue element = (ReferenceValue) value3;
					if (element.isField) {
						MultiDArray.setEntireContent(multiArr, 2, null);
						MultiDArray.setAllInitDescAndPos(multiArr, null, null, null);
					} else if (element.constructorDesc != null) 
						MultiDArray.setAllInitDescAndPos(multiArr, element.actualType, element.constructorDesc, element.initControlledPos);		
				}
			}
		} else {
			throw new AnalyzerException(insn, "Expected array absent");
		}
		return null;
	}

	/*
	 * InvokeDynamic can be worked on to resolve the owner of the invoked object
	 */
	@Override
	public Value naryOperation(final AbstractInsnNode insn, final List<? extends Value> values) throws AnalyzerException {
		if (insn instanceof MethodInsnNode) {
			int opcode = insn.getOpcode();
			MethodInsnNode method = (MethodInsnNode) insn;
			String methodOwner = method.owner;
			String methodName = method.name;
			String methodDesc = method.desc;
			Map<Integer, Value> map = new Hashtable<>(); 
			boolean isStatic = false;
			boolean isField = false;
			boolean fixedType = false;
			boolean notAnalyzable = false; 
			MethodInfo mf;

			if (opcode == INVOKESTATIC) 
				isStatic = true;

			int i = 0;
			while (i < values.size()) {
				Value value = values.get(i);
				if (value instanceof UserValues && ((UserValues) value).isTainted()) {					
					map.put(i, value);
				} else if (value instanceof ArrayValue && ((ArrayValue) value).isTainted()) {
					ArrayValue original = (ArrayValue) value;
					map.put(i, original.newArr()); // need to copy the vals and store in object to prevent same reference across several branch of analysis interfereing with one another
				} else if (value instanceof MultiDArray && ((MultiDArray) value).isTainted()) {
					MultiDArray original = (MultiDArray) value;
					map.put(i, original.newMultiArr());
				} else if (value instanceof ReferenceValue && ((ReferenceValue) value).isTainted()) {
					ReferenceValue original = (ReferenceValue) value;
					map.put(i, original.newRef());
				}
				i++;
			}
			Type returnType = Type.getReturnType(methodDesc);

			if (!map.isEmpty()) {
				if (!isStatic) {
					if (map.containsKey(0) && map.get(0) instanceof ReferenceValue) {
						ReferenceValue ref = (ReferenceValue) values.get(0); // newRef reset isField to false, assumes 
						if (ref.isField) {
							isField = true;
							if (methodName.startsWith("set") && values.size() > map.size()) // some args in the setter method are not tainted reducing userinfluence on ref
								ref.value = UserValues.USERDERIVED_REFERENCE; // extra check to see if all args are userinfluenced
						} else if (ref.actualType == null) {
							// Object who are tainted from sources other than user input and constructors (eg. method return values) as for now cannot be meaningfully analyzed hence removed
							// Might cause undertainting alternative is assume all fields in a USERDERIVED object is USERDERIVED as well 
							map.remove(0); 			
							if (map.isEmpty())
								notAnalyzable = true;
						} else {
							fixedType = true;
							methodOwner = ref.actualType;
						}
					} else if (values.get(0) instanceof ReferenceValue) {
						ReferenceValue ref = (ReferenceValue) values.get(0);
						if (opcode == INVOKESPECIAL && methodName.equals("<init>")) {  
							ref.actualType = methodOwner;
							ref.constructorDesc = methodDesc;
							ref.initControlledPos = map;					 
							ref.value = UserValues.USERDERIVED_REFERENCE; 	 // if init takes tainted args then the resulting object is tainted
						} else {
							if ((returnType.getSort() == Type.VOID || returnType.getSort() == Type.BOOLEAN) || methodName.startsWith("set"));
							ref.value = UserValues.USERDERIVED_REFERENCE; // for setters and cases like list where add/remove are responsible for the values in a class instance not hundred percent accurate
						} 
					}
				}

				mf = new MethodInfo(methodOwner, methodName, isStatic, map, methodDesc, isField, fixedType);
				// during magic method inspection io methods are not added to the list
				if (isMagicMethod && MethodInfo.checkIsInputStream(mf)) {
					return ioReturnValue(returnType);
				} else if (!notAnalyzable) {
					String sig = methodOwner + methodName + methodDesc;
					if (nextInvokedMethods.containsKey(sig)) {
						List<MethodInfo> variants = nextInvokedMethods.get(sig);
						Iterator<MethodInfo> it = variants.iterator();
						boolean shouldAdd = false;
						while (it.hasNext()) {
							if (it.next().isSub(mf)) {
								it.remove();
								shouldAdd = true;
							}
						}
						if (shouldAdd)
							variants.add(mf);
					} else {
						List<MethodInfo> variants = new ArrayList<>();
						variants.add(mf);
						nextInvokedMethods.put(sig, variants);
					}
				}
				
				// if the object a method is invoked on isField == true and for each argument, 
				// 		if is ReferenceValue, isField == true,
				// 		else if is UserValues, descriptor.equals("Influenced"),
				//		else if  is Array, content is USER_INFLUENCED
				// then the isField attribute of the return value will be true
				boolean propagateIsField = false;
				if (isField && map.size() == values.size())
					propagateIsField = MethodInfo.propagateIsField(values);
				return returnValueInit(returnType, propagateIsField);		
			}
			return newValue(returnType);
		} else if (insn instanceof InvokeDynamicInsnNode) {
			Type returnType = Type.getReturnType(((InvokeDynamicInsnNode) insn).desc);
			for (Value value : values) {
				if ((value instanceof UserValues && ((UserValues) value).isTainted()) || (value instanceof ArrayValue && ((ArrayValue) value).isTainted()) || (value instanceof MultiDArray && ((MultiDArray) value).isTainted()) || (value instanceof ReferenceValue && ((ReferenceValue) value).isTainted())) 
					return returnValueInit(returnType, false); 
			} 
			return newValue(returnType);
		} else {
			String arrDesc = ((MultiANewArrayInsnNode) insn).desc;		
			Type arrType = Type.getType(arrDesc);
			if (arrType != Type.INT_TYPE && arrType != Type.FLOAT_TYPE && arrType != Type.DOUBLE_TYPE && arrType != Type.LONG_TYPE)
				arrType = Type.getObjectType("java/lang/Object");	
			MultiDArray multiArr = new MultiDArray(arrType, values.size(), null);
			MultiDArray currentD = multiArr;
			for (int i = 0; i < values.size(); i++) {
				Value value = values.get(i);
				currentD.indivLength = (UserValues) value;
				MultiDArray.setEntireParentLength(currentD, value);
				currentD = currentD.nested;
			}
			return multiArr;
		}
	}

	@Override
	public void returnOperation(final AbstractInsnNode insn, final Value value, final Value expected) throws AnalyzerException {
		// Nothing to do.
	}

	// value types are expected to be equal
	@Override
	public Value merge(final Value value1, final Value value2) {
		if (value1 instanceof UserValues && value2 instanceof UserValues) {
			if (((UserValues) value1).getType() == ((UserValues) value2).getType()) 
				// new value takes precedence as the old value has been analyzed already
				return value2; 
		} else if (value1 instanceof ArrayValue && value2 instanceof ArrayValue) {
			if (((ArrayValue) value1).type.equals(((ArrayValue) value2).type))
				return value2;
		} else if (value1 instanceof MultiDArray && value2 instanceof MultiDArray) {
			if (((MultiDArray) value1).type.equals(((MultiDArray) value2).type))
				return value2;
		} else if (value1 instanceof ReferenceValue && value2 instanceof ReferenceValue) {
			return value2;
		}
		// should only happen for empty stack slots. Locals and stacks of two frames in terms of type should match exactly
		return UserValues.UNINITIALIZED_VALUE; 
	}

	/*
	 * Fields of type Value will always be either ReferenceVal or UserValues thus though value does not implement hashCode nor equals no error will occur.
	 * For arrays, content is the union of all the values inserted into the array.
	 * For MultiDArrays, length is the union of all the values of all the dimension of the arrays nested inside.
	 * Does not take the proportion of tainting within the array into account, 
	 * the tainting properties of the element with the greatest degree of influence is used instead for the array's isField and constructor related attributes.
	 * This policy can result in overtainting.
	 * When *LOAD operations are performed, isField and constructor related properties are passed on to the ReferenceValue representing the selected 
	 * Does not take element replacement or "removal into account" degree of influence in terms of isField and constructor can only increment
	 * When isField is true, constructorDesc and initControlledPos will be null
	 */
	public static class ArrayValue extends ObjectVal {
		private static final long serialVersionUID = 1L;
		private UserValues length;
		private Value contents;
		private transient Type type;
		// temp holder for the elements values within when the array holds ref values
		
		public ArrayValue(Type type, UserValues length) {
			super();
			this.length = length;
			this.type = type;
		}

		public void setContents(Value val) {
			Value insert;
			if (val instanceof ArrayValue) {
				ArrayValue e = (ArrayValue) val;
				ReferenceValue ref;
				if (e.isTainted()) {
					ref = new ReferenceValue(UserValues.USERDERIVED_REFERENCE);
				} else 
					ref = new ReferenceValue(UserValues.REFERENCE_VALUE);
				insert = ref;
			} else if (val instanceof MultiDArray) {
				MultiDArray e = (MultiDArray) val;
				ReferenceValue ref;
				if (e.isTainted()) {
					ref = new ReferenceValue(UserValues.USERDERIVED_REFERENCE);
				} else
					ref = new ReferenceValue(UserValues.REFERENCE_VALUE);
				insert = ref;
			} else 
				insert = val;
			contents = UserValues.valueOverriding(contents, insert);
		}
		
		public Type getType() {
			return type;
		}
		
		public boolean contentStatus() {
			if (contents != null) {
				if (contents instanceof UserValues) {
					if (((UserValues) contents).isInfluenced())
						return true;
				} else {
					if (((ReferenceValue) contents).value.isInfluenced())
						return true;
				}
			} else {
				// If contents not inserted yet use length
				if (length.isInfluenced())
					return true;
			}
			return false;
		}

		private boolean isTainted() {
			if (length.isTainted() || ((contents instanceof UserValues && ((UserValues) contents).isTainted()) || (contents instanceof ReferenceValue && ((ReferenceValue) contents).isTainted()))) 
				return true;
			return false;
		}		

		public ArrayValue newArr() {
			ArrayValue copy = new ArrayValue(type, length);
			copy.contents = contents;
			if (this.isField) {
				copy.isField = isField;
			} else if (constructorDesc != null) {
				copy.actualType = actualType;
				copy.constructorDesc = constructorDesc;
				copy.initControlledPos = initControlledPos;
			}
			return copy;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (isField ? 1231 : 1237);
			result = prime * result + ((actualType == null) ? 0 : actualType.hashCode());
			result = prime * result + ((constructorDesc == null) ? 0 : constructorDesc.hashCode());
			result = prime * result + ((contents == null) ? 0 : contents.hashCode());
			result = prime * result + ((initControlledPos == null) ? 0 : initControlledPos.hashCode());
			result = prime * result + ((length == null) ? 0 : length.hashCode());
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
			ArrayValue other = (ArrayValue) obj;
			if (isField != other.isField)
				return false;
			if (actualType == null) {
				if (other.actualType != null)
					return false;
			} else if (!actualType.equals(other.actualType))
				return false;
			if (constructorDesc == null) {
				if (other.constructorDesc != null)
					return false;
			} else if (!constructorDesc.equals(other.constructorDesc))
				return false;
			if (contents == null) {
				if (other.contents != null)
					return false;
			} else if (!contents.equals(other.contents))
				return false;
			if (initControlledPos == null) {
				if (other.initControlledPos != null)
					return false;
			} else if (!initControlledPos.equals(other.initControlledPos))
				return false;
			if (length == null) {
				if (other.length != null)
					return false;
			} else if (!length.equals(other.length))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "ArrayValue [type=" + type + ", length=" + length + ", contents=" + contents + ", isField=" + isField
					+ ", actualType=" + actualType + ", constructorDesc=" + constructorDesc + ", initControlledPos=" + initControlledPos + "]";
		}

		private void writeObject(ObjectOutputStream oos) throws IOException {
			oos.defaultWriteObject();
			int typecode;
			if (this.type == null) {
				typecode = 0;
			} else if (this.type == Type.INT_TYPE) {
				typecode = 1;
			} else if (this.type == Type.FLOAT_TYPE) {
				typecode = 2;
			} else if (this.type == Type.LONG_TYPE) {
				typecode = 3;
			} else if (this.type == Type.DOUBLE_TYPE) {
				typecode = 4;
			} else if (this.type == Type.VOID_TYPE) {
				typecode = 6;
			} else
				typecode = 5;
			oos.writeInt(typecode);
		}

		private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
			ois.defaultReadObject();
			int typecode = ois.readInt();
			switch (typecode) {
			case 0:
				this.type = null;
				break;
			case 1:
				this.type = Type.INT_TYPE;
				break;
			case 2:
				this.type = Type.FLOAT_TYPE;
				break;
			case 3:
				this.type = Type.LONG_TYPE;
				break;
			case 4:
				this.type = Type.DOUBLE_TYPE;
				break;
			case 5: 
				this.type = Type.getObjectType("java/lang/Object");
				break;
			case 6:
				this.type = Type.VOID_TYPE;
				break;
			default:
				throw new AssertionError("Invalid int typecode");
			}
		}
	}

	public static class MultiDArray extends ObjectVal {
		private static final long serialVersionUID = 1L;
		private MultiDArray nested;
		private MultiDArray parent;
		private int dim;
		private Value content;
		private UserValues length;
		private UserValues indivLength;
		private transient Type type;

		public MultiDArray(Type type, int dim, MultiDArray parent) {
			super();
			this.parent = parent;
			this.type = type;
			this.dim = dim; //inversed
			if (dim > 1) {
				nested = new MultiDArray(type, dim - 1, this);
			}
		}

		public MultiDArray getNested() {
			return nested;
		}
		
		public Type getType() {
			return type;
		}

		//for external reinit use
		public void setLength(UserValues val) {
			length = val;
		}

		public void setIndivLength(UserValues val) {
			indivLength = val;
		}

		public void setAllLengths(UserValues val) {
			UserValues newVal = (UserValues) UserValues.valueOverriding(length, val);
			length = newVal;
		}

		public static void setAllInitDescAndPos(MultiDArray multiArr, String actualType, String constructorDesc, Map<Integer, Value> initControlledPos) {
			List<MultiDArray> links = new ArrayList<>();
			links.add(multiArr);
			MultiDArray currentD = multiArr;
			while (currentD.parent != null) {
				currentD = currentD.parent;
				links.add(currentD);
			}
			currentD = multiArr;
			while (currentD.nested != null) {
				currentD = currentD.nested;
				links.add(currentD);
			}
			if (multiArr.initControlledPos == null || initControlledPos.size() > multiArr.initControlledPos.size()) {
				links.forEach(e -> {
					e.actualType = actualType;
					e.constructorDesc = constructorDesc;
					e.initControlledPos = initControlledPos;
				});		
			} else if (constructorDesc == null) {
				links.forEach(e -> {
					e.actualType = null;
					e.constructorDesc = null;
					e.initControlledPos = null;
				}); // when isfield set to true wipe all contructordesc and initcontrolledpos values
			}
		}

		public static void setEntireContent(MultiDArray multiArr, int operation, Value val) {
			List<MultiDArray> links = new ArrayList<>();
			links.add(multiArr);
			MultiDArray currentD = multiArr;
			while (currentD.parent != null) {
				currentD = currentD.parent;
				links.add(currentD);
			}
			currentD = multiArr;
			while (currentD.nested != null) {
				currentD = currentD.nested;
				links.add(currentD);
			}
			if (operation == 1) {
				links.forEach(e -> e.setAllContents(val));
			} else if (operation == 2) {
				links.forEach(e -> e.isField = true);
			} else if (operation == 3) {
				links.forEach(e -> e.setAllLengths((UserValues) val)); 
			}
		}

		public static void setEntireParentLength(MultiDArray multiArr, Value val) {
			List<MultiDArray> links = new ArrayList<>();
			links.add(multiArr);
			MultiDArray currentD = multiArr;
			while (currentD.parent != null) {
				currentD = currentD.parent;
				links.add(currentD); // only dimensions above containing this subarray will be affected by insertion of the indivlength
			}		
			links.forEach(e -> e.setAllLengths((UserValues) val));
		}

		public void setAllContents(Value val) {
			Value newVal = UserValues.valueOverriding(content, val);
			content = newVal;
		}
		
		public boolean contentStatus() {
			if (content != null) {
				if (content instanceof UserValues) {
					if (((UserValues) content).isInfluenced())
						return true;
				} else {
					if (((ReferenceValue) content).value.isInfluenced())
						return true;
				}
			} else {
				MultiDArray currentD = this;
				while (true) {
					if (!currentD.indivLength.isInfluenced())
						return false;
					if (currentD.dim == 1)
						break;
					currentD = currentD.nested;
				}
				return true;
			}
			return false;
		}

		private boolean isTainted() {
			if (length.isTainted() || ((content instanceof UserValues && ((UserValues) content).isTainted()) || (content instanceof ReferenceValue && ((ReferenceValue) content).isTainted())))
				return true;
			return false;
		}

		public MultiDArray newMultiArr() {
			MultiDArray copy = new MultiDArray(type, dim, null);
			MultiDArray.setEntireContent(copy, 1, content);
			if (this.isField) {
				MultiDArray.setEntireContent(copy, 2, null);
			} else if (constructorDesc != null) {
				MultiDArray.setAllInitDescAndPos(copy, actualType, constructorDesc, initControlledPos);
			}
			MultiDArray currentD = copy;
			MultiDArray oldcurrD = this;
			for (int i = 0; i < dim; i++) {
				currentD.indivLength = oldcurrD.indivLength;
				MultiDArray.setEntireParentLength(currentD, oldcurrD.indivLength);
				currentD = currentD.nested;
				oldcurrD = oldcurrD.nested;
			}
			return copy;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((actualType == null) ? 0 : actualType.hashCode());
			result = prime * result + ((constructorDesc == null) ? 0 : constructorDesc.hashCode());
			result = prime * result + ((content == null) ? 0 : content.hashCode());
			result = prime * result + dim;
			result = prime * result + (isField ? 1231 : 1237);
			result = prime * result + ((indivLength == null) ? 0 : indivLength.hashCode());
			result = prime * result + ((initControlledPos == null) ? 0 : initControlledPos.hashCode());
			result = prime * result + ((length == null) ? 0 : length.hashCode());
			result = prime * result + ((nested == null) ? 0 : nested.hashCode());
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
			MultiDArray other = (MultiDArray) obj;
			if (isField != other.isField)
				return false;
			if (dim != other.dim)
				return false;
			if (actualType == null) {
				if (other.actualType != null)
					return false;
			} else if (!actualType.equals(other.actualType))
				return false;
			if (constructorDesc == null) {
				if (other.constructorDesc != null)
					return false;
			} else if (!constructorDesc.equals(other.constructorDesc))
				return false;
			if (content == null) {
				if (other.content != null)
					return false;
			} else if (!content.equals(other.content))
				return false;
			if (indivLength == null) {
				if (other.indivLength != null)
					return false;
			} else if (!indivLength.equals(other.indivLength))
				return false;
			if (initControlledPos == null) {
				if (other.initControlledPos != null)
					return false;
			} else if (!initControlledPos.equals(other.initControlledPos))
				return false;
			if (length == null) {
				if (other.length != null)
					return false;
			} else if (!length.equals(other.length))
				return false;
			if (nested == null) {
				if (other.nested != null)
					return false;
			} else if (!nested.equals(other.nested))
				return false;
			return true;
		}
		
		@Override
		public String toString() {
			return "MultiDArray [dim=" + dim + ", type=" + type + ", content=" + content + ", length=" + length + ", indivLength="
					+ indivLength + ", isField=" + isField + ", actualType=" + actualType + ", constructorDesc=" + constructorDesc
					+ ", initControlledPos=" + initControlledPos + "]";
		}

		private void writeObject(ObjectOutputStream oos) throws IOException {
			oos.defaultWriteObject();
			int typecode;
			if (this.type == null) {
				typecode = 0;
			} else if (this.type == Type.INT_TYPE) {
				typecode = 1;
			} else if (this.type == Type.FLOAT_TYPE) {
				typecode = 2;
			} else if (this.type == Type.LONG_TYPE) {
				typecode = 3;
			} else if (this.type == Type.DOUBLE_TYPE) {
				typecode = 4;
			} else if (this.type == Type.VOID_TYPE) {
				typecode = 6;
			} else
				typecode = 5;
			oos.writeInt(typecode);
		}

		private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
			ois.defaultReadObject();
			int typecode = ois.readInt();
			switch (typecode) {
			case 0:
				this.type = null;
				break;
			case 1:
				this.type = Type.INT_TYPE;
				break;
			case 2:
				this.type = Type.FLOAT_TYPE;
				break;
			case 3:
				this.type = Type.LONG_TYPE;
				break;
			case 4:
				this.type = Type.DOUBLE_TYPE;
				break;
			case 5: 
				this.type = Type.getObjectType("java/lang/Object");
				break;
			case 6:
				this.type = Type.VOID_TYPE;
				break;
			default:
				throw new AssertionError("Invalid int typecode");
			}
		}
	}
	/*
	 * All UserValues.*Reference will be packaged inside this class to persist during the analysis 
	 * isField Definition:
	 * If ReferenceValue is UserInfluenced isField must be true,
	 * however, the opposite does not apply ReferenceValue can be UserDerived and has its isField property be true (eg. setter invoked on an originally UserInfluenced object)
	 */
	public static class ReferenceValue extends ObjectVal {
		private static final long serialVersionUID = 1L;
		private UserValues value; 

		public ReferenceValue(UserValues val) {
			super();
			value = val;
		}

		private boolean isTainted() {
			if (value.isTainted()) {
				return true;
			}
			return false;
		}

		// for jump insn when merging frames in analyzer
		public ReferenceValue newRef() {
			ReferenceValue copy = new ReferenceValue(value);
			if (isField) {
				copy.isField = isField;
			} else {
				copy.actualType = actualType;
				copy.constructorDesc = constructorDesc;
				copy.initControlledPos = initControlledPos;
			}
			return copy;
		}
		
		public UserValues getValue() {
			return value;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((actualType == null) ? 0 : actualType.hashCode());
			result = prime * result + ((constructorDesc == null) ? 0 : constructorDesc.hashCode());
			result = prime * result + ((initControlledPos == null) ? 0 : initControlledPos.keySet().hashCode());
			result = prime * result + (isField ? 1231 : 1237);
			result = prime * result + ((value == null) ? 0 : value.hashCode());
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
			ReferenceValue other = (ReferenceValue) obj;
			if (isField != other.isField)
				return false;
			if (actualType == null) {
				if (other.actualType != null)
					return false;
			} else if (!actualType.equals(other.actualType))
				return false;
			if (constructorDesc == null) {
				if (other.constructorDesc != null)
					return false;
			} else if (!constructorDesc.equals(other.constructorDesc))
				return false;
			if (initControlledPos == null) {
				if (other.initControlledPos != null)
					return false;
			} else if (!initControlledPos.keySet().equals(other.initControlledPos.keySet()))
				return false;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "ReferenceValue [value=" + value + ", isField=" + isField + ", actualType=" + actualType + ", constructorDesc=" + constructorDesc
					+ ", initControlledPos=" + initControlledPos + "]";
		}
	}
	
	public static class ObjectVal implements Value, Serializable {
		protected String actualType;
		protected String constructorDesc; // used for <init> method matching later not hundred percent acc;
		protected Map<Integer, Value> initControlledPos; // for methods invoked on reference values that are not fields their position will be noted down to determine the fields controlled by user 
		protected boolean isField;
		private static final long serialVersionUID = 1L;
		
		public ObjectVal() {
			isField = false;
		}
		
		public void setField() {
			isField = true;
		}
		
		public boolean getIsField() {
			return isField;
		}

		public String getInitDesc() {
			return constructorDesc;
		}
		
		public Map<Integer, Value> getinitControlledArgPos() {
			return initControlledPos;
		}
		
		@Override
		public int getSize() {
			return 1;
		}
	}
	
	public Value newException(final TryCatchBlockNode tryCatchBlockNode, final CustomFrame handlerFrame, final Type exceptionType) {
		return newValue(exceptionType);
	}
}