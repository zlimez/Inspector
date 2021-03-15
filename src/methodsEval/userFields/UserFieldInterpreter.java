package methodsEval.userFields;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

import methodsEval.MethodInfo;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class UserFieldInterpreter extends BasicInterpreter {
	public final static BasicValue USER_INFLUENCED = new BasicValue(null);
	public final static BasicValue USER_DERIVED = new BasicValue(null);
	private Map<String, BasicValue> UserControlledFields = new HashMap<>();
	private boolean isConstructor;
	private boolean isMethod;
	private Map<Integer, BasicValue> userControlledArgPos;
	private int counter = 0;
	private int numOfArgs;
	public List<MethodInfo> nextInvokedMethod = new ArrayList<>();
	
	public UserFieldInterpreter(boolean isConstructor, boolean isMethod, int numOfArgs, Map<Integer, BasicValue> userControlledArgPos) {
		super(ASM9);
		this.isConstructor = isConstructor;
		this.isMethod = isMethod;
		this.numOfArgs = numOfArgs;
		this.userControlledArgPos = userControlledArgPos;
	}
	/* definition to evaluate USER_INFLUENCED | USERCONTROLLED
	 * let all possible expected results from an expression (bytecode instruction) be set R
	 * if restricting user's freedom to control the input into an expression reduces the number of outcomes to a subset of R, S
	 * I conclude the outcome to be user derived if not where the set of outcomes remains the same to be USER_INFLUENCED
	 */
	// arrays need special treatment doesn't work well for array operations now
	// assumptions made for method calls
	
	public Map<String, BasicValue> getUserControlledFieldsFromConstructor() {
		return UserControlledFields;
	}
	
	public List<MethodInfo> getNextInvokedMethods() {
		return nextInvokedMethod;
	}
 	
	public void setUserControlledFields(Map<String, BasicValue> controlledFields) {
		this.UserControlledFields = controlledFields;
	}
	
	@Override
	public BasicValue newEmptyValue(final int local) {
		return BasicValue.UNINITIALIZED_VALUE;
	}
	
	@Override // for constructors
	public BasicValue newValue(final Type type) {
		if (isConstructor) {
			if (counter == 0) {
				counter++;
				return super.newValue(type);
			} else if (counter <= numOfArgs) { // first numOfArgs + 1 calls to this method are for this and the method arguments 
				counter++;
				return USER_INFLUENCED;
			}
		} else if (isMethod) {
			if (userControlledArgPos.containsKey(counter)) {
				counter++;
				return userControlledArgPos.get(counter - 1);
			}
			counter++;
		}
		return super.newValue(type);
	}
	
	@Override 
	public BasicValue newOperation(final AbstractInsnNode insn) throws AnalyzerException {
		switch (insn.getOpcode()) {
			case GETSTATIC:
				String fieldName = ((FieldInsnNode) insn).name;
		    	if (UserControlledFields.containsKey(fieldName)) {
		    		  return UserControlledFields.get(fieldName);
		    	}
		    	return super.newOperation(insn);
			default:
				return super.newOperation(insn);
		}
	}
		
	@Override 
	public BasicValue unaryOperation(final AbstractInsnNode insn, final BasicValue value) throws AnalyzerException {
		 switch (insn.getOpcode()) {
	      case INEG:
	      case IINC:
	      case L2I:
	      case F2I: 
	      case D2I:
	      case I2B:
	      case I2C:
	      case I2S:
	      case FNEG:
	      case I2F:
	      case L2F:
	      case D2F:
	      case LNEG:
	      case I2L:
	      case F2L:
	      case D2L:
	      case DNEG:
	      case I2D:
	      case L2D:
	      case F2D:
	      case CHECKCAST:
	      case ARRAYLENGTH:
	      case INSTANCEOF:
	      case NEWARRAY:
	      case ANEWARRAY: // for length comparisons should enable element based evaluation
	    	  if (value == USER_INFLUENCED || value == USER_DERIVED) {
	    		  return value;
	    	  }
	    	  return super.unaryOperation(insn, value);
	      case GETFIELD:
	    	  String fieldName = ((FieldInsnNode) insn).name;
	    	  if (UserControlledFields.containsKey(fieldName)) {
	    		  return UserControlledFields.get(fieldName);
	    	  }
	    	  return super.unaryOperation(insn, value);
	      case PUTSTATIC:
	    	  String StaticFieldName = ((FieldInsnNode) insn).name;
	    	  if (value == USER_INFLUENCED || value == USER_DERIVED) {
	    		  UserControlledFields.put(StaticFieldName, value);
	    	  }
	    	  return null;
	      default:
	    	  return super.unaryOperation(insn, value);
	 	}
	}
	
	@Override 
	public BasicValue binaryOperation(final AbstractInsnNode insn, final BasicValue value1, final BasicValue value2) throws AnalyzerException {
		switch (insn.getOpcode()) {
	      case IALOAD:
	      case BALOAD:
	      case CALOAD:
	      case SALOAD:
	      case FALOAD:
	      case DALOAD:
	      case AALOAD:
	      case LALOAD:
	      case IMUL:
	      case IDIV:
	      case IREM:
	      case FREM:
	      case LMUL:
	      case LDIV:
	      case LREM:
	      case DREM:
	      case IAND:
	      case IOR:
	      case LAND:
	      case LOR:
	      case ISHL:
	      case ISHR:
	      case LSHL:
	      case LSHR:
	      case IUSHR:
	      case LUSHR:
	    	  if (value1 == USER_INFLUENCED && value2 == USER_INFLUENCED) {
	    		  return USER_INFLUENCED;
	    	  } else if (value1 == USER_DERIVED || value2 == USER_DERIVED || value1 == USER_INFLUENCED || value2 == USER_DERIVED) { // can be made more specific for taint analysis
	    		  return USER_DERIVED;
	    	  }
	    	  return super.binaryOperation(insn, value1, value2);
	      case IADD:
	      case ISUB:	    	  
	      case FADD:
	      case FSUB:
	      case FMUL:
	      case FDIV:
	      case LADD:
	      case LSUB:
	      case DADD:
	      case DSUB:
	      case DMUL:
	      case DDIV:
	      case IXOR:
	      case LXOR:
	      case LCMP:
	      case FCMPL:
	      case FCMPG:
	      case DCMPL:
	      case DCMPG:
	    	  if (value1 == USER_INFLUENCED || value2 == USER_INFLUENCED) {
	    		  return USER_INFLUENCED;
	    	  } else if (value1 == USER_DERIVED || value2 == USER_DERIVED) {
	    		  return USER_DERIVED;
	    	  }
	    	  return super.binaryOperation(insn, value1, value2);	
	      case PUTFIELD:
	    	  String fieldName = ((FieldInsnNode) insn).name;
	    	  if (value2 == USER_INFLUENCED || value2 == USER_DERIVED) {
	    		  UserControlledFields.put(fieldName, value2);
	    	  }
	    	  return null;
	      default: 
	    	  return super.binaryOperation(insn, value1, value2);
		}
	}

	@Override
	 public BasicValue naryOperation(final AbstractInsnNode insn, final List<? extends BasicValue> values) throws AnalyzerException {
		if (insn instanceof MethodInsnNode) {
			MethodInsnNode method = (MethodInsnNode) insn;
			Map<Integer, BasicValue> map = new Hashtable<>(); 
			boolean isStatic = false;
			MethodInfo mf;
			
			int i = 0;
			while (i < values.size()) {
				BasicValue value = values.get(i);
				if (value == USER_INFLUENCED || value == USER_DERIVED) {					
					map.put(i, value);
				}
				i++;
			}

			if (!map.isEmpty()) {
				if (method.getOpcode() == INVOKESTATIC) {
					isStatic = true;
				}
//				if (UserControlledFields.containsKey(method.owner)) {
//					mf = new MethodInfo(method.owner, method.name, isStatic, map, method.desc, true);
//				} else {
//					mf = new MethodInfo(method.owner, method.name, isStatic, map, method.desc, false);
//				} // need to be fixed so the owner corresponds to field name
				String owner = method.owner;
				mf = new MethodInfo(owner, method.name, isStatic, map, method.desc, false);
				if (!nextInvokedMethod.contains(mf)) {
					if (!MethodInfo.checkIsInputStream(mf)) 
						nextInvokedMethod.add(mf);
				}
				return USER_DERIVED;
			}
		} else if (insn instanceof InvokeDynamicInsnNode) {
			for (BasicValue value : values) {
				if (value == USER_INFLUENCED || value == USER_DERIVED) {
					return USER_DERIVED; // needs to be improved rendering of the actual method owner
				}
			}
		} 	// tackle multi-d arrays later
		return super.naryOperation(insn, values);
	}
	
	@Override 
	public BasicValue merge(final BasicValue value1, final BasicValue value2) {
		if (value1 == USER_INFLUENCED && value2 == USER_INFLUENCED) {
			return USER_INFLUENCED;
		} else if (value1 == USER_DERIVED || value2 == USER_DERIVED || value1 == USER_INFLUENCED || value2 == USER_DERIVED) {
			return USER_DERIVED;
		}
		return super.merge(value1, value2);
	}
}
