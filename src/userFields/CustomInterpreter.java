package userFields;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;

import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;

import methodsEval.MethodInfo;
import userFields.UserFieldInterpreter.ArrayValue;
import userFields.UserFieldInterpreter.MultiDArray;
import userFields.UserFieldInterpreter.ReferenceValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

public class CustomInterpreter extends Interpreter<Value> implements Opcodes {
	public static final Type NULL_TYPE = Type.getObjectType("null");
	private Map<String, Value> UserControlledFields = new HashMap<>();
	private boolean isConstructor;
	private boolean isMethod;
	private Map<Integer, Value> userControlledArgPos;
	private int counter = 0;
	private int numOfArgs;
	public Set<MethodInfo> nextInvokedMethod = new HashSet<>();
	
	public CustomInterpreter() {
		super(ASM9);
	    if (getClass() != CustomInterpreter.class) {
	      throw new IllegalStateException();
	    }
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
	
	public Map<String, Value> getUserControlledFields() {
		Iterator<Map.Entry<String, Value>> it = UserControlledFields.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Value> field = (Map.Entry<String, Value>) it.next();
			Value value = field.getValue();
			if (value instanceof UserValues || (value instanceof ArrayValue && ((ArrayValue) value).isTainted()) || (value instanceof MultiDArray && ((MultiDArray) value).isTainted()) || (value instanceof ReferenceValue && ((ReferenceValue) value).isTainted())) {			
			} else {
				it.remove();
			}
		} 
		return UserControlledFields;
	}
	
	public Set<MethodInfo> getNextInvokedMethods() {
		return nextInvokedMethod;
	}
	
	public void setUserControlledFields(Map<String, Value> controlledFields) {
		this.UserControlledFields = controlledFields;
	}
	
	@Override
	public Value newEmptyValue(final int local) {
		return BasicValue.UNINITIALIZED_VALUE;
	}
	
	public Value newValue(final Type type) {
		if (isConstructor) {
			if (counter == 0) {
				counter++;
			} else if (counter <= numOfArgs) { // first numOfArgs + 1 calls to this method are for this and the method arguments 
				counter++;
				switch (type.getSort()) {
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
			    	String arrSig = "//[//]";
			    	Pattern pattern = Pattern.compile(arrSig);
			    	Matcher matcher = pattern.matcher(type.getClassName());
			    	int dim = 0;
			    	while (matcher.find()) {
			    		dim++;
			    	}
			    	Value element = null;
			    	switch (type.getElementType().getSort()) {
				      case Type.BOOLEAN:
				      case Type.CHAR:
				      case Type.BYTE:
				      case Type.SHORT:
				      case Type.INT:
				        element = UserValues.USERINFLUENCED_INT;
				        break;
				      case Type.FLOAT:
				        element = UserValues.USERINFLUENCED_FLOAT;
				        break;
				      case Type.LONG:
				        element = UserValues.USERINFLUENCED_LONG;
				        break;
				      case Type.DOUBLE:
				        element = UserValues.USERINFLUENCED_DOUBLE;
				        break;
				      case Type.OBJECT:
				    	element = UserValues.USERINFLUENCED_REFERENCE;
				    	break;
				      default:
				    	throw new AssertionError("Unknown element type");
	    			}
			    	if (dim == 1) {
		    			ArrayValue arr = new ArrayValue(UserValues.USERINFLUENCED_INT);
		    			arr.setContents(element);
		    			return arr;
		    		} else {
		    			MultiDArray multiArr = new MultiDArray(dim, null);
		    			MultiDArray currentD = multiArr;
		    			for (int i = 0; i < dim; i++) {
		    				currentD.indivLength = UserValues.USERINFLUENCED_INT;
		    				currentD = currentD.nested;
		    			}
		    			MultiDArray.setEntireNest(multiArr, 1, UserValues.USERINFLUENCED_INT);
		    			MultiDArray.setEntireNest(multiArr, 2, element);
		    			return multiArr;
		    		}
			      case Type.OBJECT:
			        return new ReferenceValue(UserValues.USERINFLUENCED_REFERENCE);
			      default:
			        throw new AssertionError("Initialization error");
				}
			}
		} else if (isMethod) {
			if (userControlledArgPos.containsKey(counter)) {
				counter++;
				return userControlledArgPos.get(counter - 1);
			}
			counter++;
		}
		if (type == null) {
			return BasicValue.UNINITIALIZED_VALUE;
	    }
	    switch (type.getSort()) {
	      case Type.VOID:
	        return null;
	      case Type.BOOLEAN:
	      case Type.CHAR:
	      case Type.BYTE:
	      case Type.SHORT:
	      case Type.INT:
	        return BasicValue.INT_VALUE;
	      case Type.FLOAT:
	        return BasicValue.FLOAT_VALUE;
	      case Type.LONG:
	        return BasicValue.LONG_VALUE;
	      case Type.DOUBLE:
	        return BasicValue.DOUBLE_VALUE;
	      case Type.ARRAY:
	      case Type.OBJECT:
	        return new ReferenceValue(BasicValue.REFERENCE_VALUE);
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
	        return BasicValue.INT_VALUE;
	      case LCONST_0:
	      case LCONST_1:
	        return BasicValue.LONG_VALUE;
	      case FCONST_0:
	      case FCONST_1:
	      case FCONST_2:
	        return BasicValue.FLOAT_VALUE;
	      case DCONST_0:
	      case DCONST_1:
	        return BasicValue.DOUBLE_VALUE;
	      case BIPUSH:
	      case SIPUSH:
	        return BasicValue.INT_VALUE;
	      case LDC:
	        Object value = ((LdcInsnNode) insn).cst;
	        if (value instanceof Integer) {
	          return BasicValue.INT_VALUE;
	        } else if (value instanceof Float) {
	          return BasicValue.FLOAT_VALUE;
	        } else if (value instanceof Long) {
	          return BasicValue.LONG_VALUE;
	        } else if (value instanceof Double) {
	          return BasicValue.DOUBLE_VALUE;
	        } else if (value instanceof String) {
	          return newValue(Type.getObjectType("java/lang/String"));
	        } else if (value instanceof Type) {
	          int sort = ((Type) value).getSort();
	          if (sort == Type.OBJECT || sort == Type.ARRAY) {
	            return newValue(Type.getObjectType("java/lang/Class"));
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
	        return BasicValue.RETURNADDRESS_VALUE;
	      case GETSTATIC:
	    	String fieldName = ((FieldInsnNode) insn).name;
	    	if (UserControlledFields.containsKey(fieldName)) {
	    		return UserControlledFields.get(fieldName);
		   	} else if (Type.getType(((FieldInsnNode) insn).desc).getSort() == Type.OBJECT) { // retro add the field to potentially user controlled list
		   		ReferenceValue ref = new ReferenceValue(BasicValue.REFERENCE_VALUE);
		   		ref.setIsField();
		   		return ref;
	    	} else if (Type.getType(((FieldInsnNode) insn).desc).getSort() == Type.ARRAY) {
	    		String desc = ((FieldInsnNode) insn).desc;
	    		String arrsig = "\\[";
	    		Pattern pattern = Pattern.compile(arrsig);
	    		Matcher matcher = pattern.matcher(desc);
	    		int dim = 0;
	    		while (matcher.find()) {
	    			dim++;
	    		}
	    		if (dim == 1) {
	    			ArrayValue arr = new ArrayValue(BasicValue.INT_VALUE);
	    			return arr;
	    		} else {
	    			MultiDArray multiArr = new MultiDArray(dim, null);
	    			MultiDArray currentD = multiArr;
	    			for (int i = 0; i < dim; i++) {
	    				currentD.indivLength = BasicValue.INT_VALUE;
	    				currentD = currentD.nested;
	    				MultiDArray.setEntireNest(multiArr, 1, BasicValue.INT_VALUE);
	    			}
	    			return multiArr;
	    		}
	    	}
	        return newValue(Type.getType(((FieldInsnNode) insn).desc));
	      case NEW:
	        return new ReferenceValue(BasicValue.REFERENCE_VALUE);
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
	    	if (value instanceof UserValues) {
	    		if (((UserValues) value).isInfluenced())
	    			return UserValues.USERINFLUENCED_INT;
	    		return UserValues.USERDERIVED_INT;
	    	}
	        return BasicValue.INT_VALUE;
	      case FNEG:
	      case I2F:
	      case L2F:
	      case D2F:
	    	if (value instanceof UserValues) {
		   		if (((UserValues) value).isInfluenced())
		   			return UserValues.USERINFLUENCED_FLOAT;
		   		return UserValues.USERDERIVED_FLOAT;
		   	}
	        return BasicValue.FLOAT_VALUE;
	      case LNEG:
	      case I2L:
	      case F2L:
	      case D2L:
	    	if (value instanceof UserValues) {
		   		if (((UserValues) value).isInfluenced())
		   			return UserValues.USERINFLUENCED_LONG;
		   		return UserValues.USERDERIVED_LONG;
		   	}
	        return BasicValue.LONG_VALUE;
	      case DNEG:
	      case I2D:
	      case L2D:
	      case F2D:
	        if (value instanceof UserValues) {
		   		if (((UserValues) value).isInfluenced())
		   			return UserValues.USERINFLUENCED_DOUBLE;
		   		return UserValues.USERDERIVED_DOUBLE;
		    }
	        return BasicValue.DOUBLE_VALUE;
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
	      case PUTSTATIC:
	    	String StaticFieldName = ((FieldInsnNode) insn).name;
	    	if (value instanceof UserValues || value instanceof ArrayValue || value instanceof MultiDArray || value instanceof ReferenceValue) {
	    		if (value instanceof ReferenceValue)
	    			((ReferenceValue) value).setIsField();
	   		 	UserControlledFields.put(StaticFieldName, value);
	    	}
	        return null;
	      case GETFIELD:
	    	String fieldName = ((FieldInsnNode) insn).name;
	    	if (UserControlledFields.containsKey(fieldName)) {
	    		return UserControlledFields.get(fieldName);
	    	} else if (Type.getType(((FieldInsnNode) insn).desc).getSort() == Type.OBJECT) { // retro add the field to potentially user controlled list
	    		ReferenceValue fv = new ReferenceValue(BasicValue.REFERENCE_VALUE);
	    		UserControlledFields.put(fieldName, fv);
	    		return fv;
	    	} else if (Type.getType(((FieldInsnNode) insn).desc).getSort() == Type.ARRAY) {
	    		String desc = ((FieldInsnNode) insn).desc;
	    		String arrsig = "\\[";
	    		Pattern pattern = Pattern.compile(arrsig);
	    		Matcher matcher = pattern.matcher(desc);
	    		int dim = 0;
	    		while (matcher.find()) {
	    			dim++;
	    		}
	    		if (dim == 1) {
	    			ArrayValue arr = new ArrayValue(BasicValue.INT_VALUE);
	    			return arr;
	    		} else {
	    			MultiDArray multiArr = new MultiDArray(dim, null);
	    			MultiDArray currentD = multiArr;
	    			for (int i = 0; i < dim; i++) {
	    				currentD.indivLength = BasicValue.INT_VALUE;
	    				currentD = currentD.nested;
	    				MultiDArray.setEntireNest(multiArr, 1, BasicValue.INT_VALUE);
	    			}
	    			return multiArr;
	    		}
	    	}
	        return newValue(Type.getType(((FieldInsnNode) insn).desc));
	      case NEWARRAY:
	      case ANEWARRAY:
	    	ArrayValue arr = new ArrayValue(value);
	        return arr;
	      case ARRAYLENGTH:
	    	if (value instanceof ArrayValue) {
	    		ArrayValue array = (ArrayValue) value;
	    		return array.length;
	    	}    	  
	    	if (value instanceof MultiDArray) {
	    		MultiDArray multiArr = (MultiDArray) value;
	    		return multiArr.indivLength;
	    	}
	   
	        return BasicValue.INT_VALUE;
	      case ATHROW:
	        return null;
	      case CHECKCAST:
	   	    if (value instanceof ReferenceValue && ((ReferenceValue) value).isTainted()) {
	   	    	return value;
	    	}
	        return newValue(Type.getObjectType(((TypeInsnNode) insn).desc));
	      case INSTANCEOF:
	        return BasicValue.INT_VALUE;
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
    	  case AALOAD:
    		if (value1 instanceof ArrayValue) {
    			ArrayValue arr = (ArrayValue) value1;
    			Value contents = arr.contents;
    			if (contents instanceof UserValues) {
    				UserValues con = (UserValues) contents;
    				if (value2 instanceof UserValues) {
    					UserValues index = (UserValues) value2;
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
    				} else if (contents == UserValues.USERINFLUENCED_DOUBLE || contents == UserValues.USERDERIVED_DOUBLE) {
    					return UserValues.USERDERIVED_DOUBLE;
	        	 	} else {
	        		  return UserValues.USERDERIVED_REFERENCE;
	        	 	}
        	  } 
        	  if (value2 instanceof UserValues) {
        		  if (contents == BasicValue.INT_VALUE) {
        			  return UserValues.USERDERIVED_INT;
        		  } else if (contents == BasicValue.FLOAT_VALUE) {
        			  return UserValues.USERDERIVED_FLOAT;
        		  } else if (contents == BasicValue.DOUBLE_VALUE) {
        			  return UserValues.USERDERIVED_DOUBLE;
        		  } else if (contents == BasicValue.LONG_VALUE) {
        			  return UserValues.USERDERIVED_LONG;
        		  } else {
        			  return UserValues.USERDERIVED_REFERENCE;
        		  }
        	  }
          } else if (value1 instanceof MultiDArray) {
        	  	MultiDArray multiArr = (MultiDArray) value1;
        	  	if (multiArr.dim == 1) {
        	  		Value contents = multiArr.content;
        	  		if (contents instanceof UserValues) {
        	  			UserValues con = (UserValues) contents;
        	  			if (value2 instanceof UserValues) {
        	  				UserValues index = (UserValues) value2;
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
        	  			} else if (contents == UserValues.USERINFLUENCED_DOUBLE || contents == UserValues.USERDERIVED_DOUBLE) {
        	  				return UserValues.USERDERIVED_DOUBLE;
        	  			} else {
        	  				return UserValues.USERDERIVED_REFERENCE;
        	  			}
        	  		} 
        	  		if (value2 instanceof UserValues) {
        	  			if (contents == BasicValue.INT_VALUE) {
        	  				return UserValues.USERDERIVED_INT;
        	  			} else if (contents == BasicValue.FLOAT_VALUE) {
        	  				return UserValues.USERDERIVED_FLOAT;
        	  			} else if (contents == BasicValue.DOUBLE_VALUE) {
        	  				return UserValues.USERDERIVED_DOUBLE;
        	  			} else if (contents == BasicValue.LONG_VALUE) {
        	  				return UserValues.USERDERIVED_LONG;
        	  			} else {
        	  				return UserValues.USERDERIVED_REFERENCE;
        	  			}
        	  		}
	   		  } else {
	   			  return multiArr.nested;
	   		  }	  
    	  }
        case IADD:
        case ISUB:
        case IXOR:
          if (value1 == UserValues.USERINFLUENCED_INT || value2 == UserValues.USERINFLUENCED_INT) {
	    	  return UserValues.USERINFLUENCED_INT;
	   	  } else if (value1 == UserValues.USERDERIVED_INT || value2 == UserValues.USERDERIVED_INT) {
	   		  return UserValues.USERDERIVED_INT;
	   	  }
          return BasicValue.INT_VALUE;
        case FADD:
        case FSUB:
        case FMUL:
        case FDIV:
          if (value1 == UserValues.USERINFLUENCED_FLOAT || value2 == UserValues.USERINFLUENCED_FLOAT) {
  	    	  return UserValues.USERINFLUENCED_FLOAT;
  	   	  } else if (value1 == UserValues.USERDERIVED_FLOAT || value2 == UserValues.USERDERIVED_FLOAT) {
  	   		  return UserValues.USERDERIVED_FLOAT;
  	   	  }
          return BasicValue.FLOAT_VALUE;
        case LADD:
        case LSUB:
        case LMUL:
        case LDIV:
        case LXOR:
          if (value1 == UserValues.USERINFLUENCED_LONG || value2 == UserValues.USERINFLUENCED_LONG) {
        	  return UserValues.USERINFLUENCED_LONG;
    	  } else if (value1 == UserValues.USERDERIVED_LONG || value2 == UserValues.USERDERIVED_LONG) {
    	   	  return UserValues.USERDERIVED_LONG;
     	  }
          return BasicValue.LONG_VALUE;
        case DADD:
        case DSUB:      
        case DMUL:
        case DDIV:
          if (value1 == UserValues.USERINFLUENCED_DOUBLE || value2 == UserValues.USERINFLUENCED_DOUBLE) {
    	   	  return UserValues.USERINFLUENCED_DOUBLE;
      	  } else if (value1 == UserValues.USERDERIVED_DOUBLE || value2 == UserValues.USERDERIVED_DOUBLE) {
   	   		  return UserValues.USERDERIVED_DOUBLE;
   	   	  }
          return BasicValue.DOUBLE_VALUE;
        case IMUL:
        case IDIV:
        case IREM:
        case ISHL:
        case ISHR:
        case IUSHR:
        case IAND:
        case IOR:
          if (value1 == UserValues.USERINFLUENCED_INT && value2 == UserValues.USERINFLUENCED_INT) {
	    	  return UserValues.USERINFLUENCED_INT;
	   	  } else if (value1 == UserValues.USERDERIVED_INT || value2 == UserValues.USERDERIVED_INT || value1 == UserValues.USERINFLUENCED_INT || value2 == UserValues.USERINFLUENCED_INT) { // can be made more specific for taint analysis
	  		  return UserValues.USERDERIVED_INT;
	   	  }
          return BasicValue.INT_VALUE;
        case FREM:
          if (value1 == UserValues.USERINFLUENCED_FLOAT && value2 == UserValues.USERINFLUENCED_FLOAT) {
  	    	  return UserValues.USERINFLUENCED_FLOAT;
  	   	  } else if (value1 == UserValues.USERDERIVED_FLOAT || value2 == UserValues.USERDERIVED_FLOAT || value1 == UserValues.USERINFLUENCED_FLOAT || value2 == UserValues.USERINFLUENCED_FLOAT) { // can be made more specific for taint analysis
  	  		  return UserValues.USERDERIVED_FLOAT;
  	   	  }
          return BasicValue.FLOAT_VALUE; 
        case LREM:
        case LSHL:
        case LSHR:
        case LUSHR:
        case LAND:
        case LOR:
          if (value1 == UserValues.USERINFLUENCED_LONG && value2 == UserValues.USERINFLUENCED_LONG) {
  	    	  return UserValues.USERINFLUENCED_LONG;
  	   	  } else if (value1 == UserValues.USERDERIVED_LONG || value2 == UserValues.USERDERIVED_LONG || value1 == UserValues.USERINFLUENCED_LONG || value2 == UserValues.USERINFLUENCED_LONG) { // can be made more specific for taint analysis
  	  		  return UserValues.USERDERIVED_LONG;
  	   	  }
          return BasicValue.LONG_VALUE;
        case DREM:
          if (value1 == UserValues.USERINFLUENCED_DOUBLE && value2 == UserValues.USERINFLUENCED_DOUBLE) {
  	    	  return UserValues.USERINFLUENCED_DOUBLE;
  	   	  } else if (value1 == UserValues.USERDERIVED_DOUBLE || value2 == UserValues.USERDERIVED_DOUBLE || value1 == UserValues.USERINFLUENCED_DOUBLE || value2 == UserValues.USERINFLUENCED_DOUBLE) { // can be made more specific for taint analysis
  	  		  return UserValues.USERDERIVED_DOUBLE;
  	   	  }
          return BasicValue.DOUBLE_VALUE;
        case LCMP:
        case FCMPL:
        case FCMPG:
        case DCMPL:
        case DCMPG:
          boolean minDerived = false;
          if (value1 instanceof UserValues) {
        	  if (((UserValues) value1).isInfluenced()) {
        		  return UserValues.USERINFLUENCED_INT;
        	  }
        	  minDerived = true;
          }
          if (value2 instanceof UserValues) {
        	  if (((UserValues) value2).isInfluenced()) {
        		  return UserValues.USERINFLUENCED_INT;
        	  }
        	  return UserValues.USERDERIVED_INT;
          }
          if (minDerived) {
        	  return UserValues.USERDERIVED_INT;
          }
          return BasicValue.INT_VALUE;
        case IF_ICMPEQ:
        case IF_ICMPNE:
        case IF_ICMPLT:
        case IF_ICMPGE:
        case IF_ICMPGT:
        case IF_ICMPLE:
        case IF_ACMPEQ:
        case IF_ACMPNE:
        case PUTFIELD:
          String fieldName = ((FieldInsnNode) insn).name;
          if (value2 instanceof UserValues || value2 instanceof ArrayValue || value2 instanceof MultiDArray || value2 instanceof ReferenceValue) {
        	  if (value2 instanceof ReferenceValue)
        		  ((ReferenceValue) value2).setIsField();
	   		  UserControlledFields.put(fieldName, value2);
          }
          return null;
        default:
          throw new AssertionError();
      }
    }
    
	@Override
	public Value ternaryOperation(final AbstractInsnNode insn, final Value value1, final Value value2, final Value value3) throws AnalyzerException {
	    if (value1 instanceof ArrayValue) {
			ArrayValue arr = (ArrayValue) value1;
			arr.setContents(value3);
		} else if (value1 instanceof MultiDArray) {
			MultiDArray multiArr = (MultiDArray) value1;
			MultiDArray.setEntireNest(multiArr, 2, value3);
		}
	    return null;
	}
	
	@Override
	public BasicValue naryOperation(final AbstractInsnNode insn, final List<? extends Value> values) throws AnalyzerException {
		if (insn instanceof MethodInsnNode) {
			MethodInsnNode method = (MethodInsnNode) insn;
			String methodName = method.name;
			String methodDesc = method.desc;
			Map<Integer, Value> map = new Hashtable<>(); 
			boolean isStatic = false;
			boolean isField = false;
			MethodInfo mf;
			
			if (method.getOpcode() == INVOKESTATIC) {
				isStatic = true;
			}
			
			int i = 0;
			while (i < values.size()) {
				Value value = values.get(i);
				if (value instanceof UserValues) {					
					map.put(i, value);
				} else if (value instanceof ArrayValue && ((ArrayValue) value).isTainted()) {
					ArrayValue original = (ArrayValue) value;
					map.put(i, original.newArr()); // need to copy the vals and store in object to prevent same reference across several branch of analysis interfereing with one another
				} else if (value instanceof MultiDArray && ((MultiDArray) value).isTainted()) {
					MultiDArray original = (MultiDArray) value;
					map.put(i, original.newMultiArr());
				} else if (value instanceof ReferenceValue) {
					ReferenceValue original = (ReferenceValue) value;
					if (((ReferenceValue) value).isTainted())
						map.put(i, original.newRef());

				}
				i++;
			}
			
			if (values.get(0) instanceof ReferenceValue && methodName.equals("<init>")) {
				ReferenceValue ref = (ReferenceValue) values.get(0);
				ref.arglength = values.size() - 1;
				ref.initControlledPos = map;
			}

			if (!map.isEmpty()) {
				 if (!isStatic) {
					if (map.containsKey(0) && map.get(0) instanceof ReferenceValue) {
						ReferenceValue ref = (ReferenceValue) map.get(0);
						if (ref.isField)
							isField = true;
					} // assume the object being a field which the method is invoked on will be tainted normal objects ignored to simplify
				}

				String owner = method.owner;
				mf = new MethodInfo(owner, method.name, isStatic, map, method.desc, isField);
				if (!MethodInfo.checkIsInputStream(mf)) 
					nextInvokedMethod.add(mf);
			}
		} else if (insn instanceof InvokeDynamicInsnNode) {
			for (BasicValue value : values) {
				if (value == USER_INFLUENCED || value == USER_DERIVED || (value instanceof ArrayValue && ((ArrayValue) value).isTainted()) || (value instanceof MultiDArray && ((MultiDArray) value).isTainted()) || (value instanceof ReferenceValue && ((ReferenceValue) value).isTainted())) {
					return USER_DERIVED; // needs to be improved rendering of the actual method owner
				}
			}
		} else if (insn instanceof MultiANewArrayInsnNode) {
			MultiDArray multiArr = new MultiDArray(null, values.size(), null);
			MultiDArray currentD = multiArr;
			for (int i = 0; i < values.size(); i++) {
				BasicValue value = values.get(i);
				currentD.indivLength = value;
				currentD = currentD.nested;
				MultiDArray.setEntireNest(multiArr, 1, value);
			}
			return multiArr;
		}
		return super.naryOperation(insn, values);
	}

	
	public static class ArrayValue implements Value {
		private Value length;
		private Value contents;
		
		public ArrayValue(Value length) {
			this.length = length;
		}
		
		public void setContents(Value val) {
			Value newVal = UserValues.valueOverriding(contents, val);
			contents = newVal;
		}
		
		private boolean isTainted() {
			if (length instanceof UserValues || contents instanceof UserValues) 
				return true;
			return false;
		}
		
		@Override
		public String toString() { // used to reinitialization when analysis is continued
			StringBuffer sb = new StringBuffer("ArrayValue,");
			sb.append(convertVal(length) + ",");
			sb.append(convertVal(contents));
			return sb.toString();
		}
		
		@Override
		public int getSize() {
			return 1;
		}
		
		public ArrayValue newArr() {
			ArrayValue copy = new ArrayValue(length);
			copy.contents = contents;
			return copy;
		}
	}
	
	public static class MultiDArray implements Value {
		private MultiDArray nested;
		private MultiDArray parent;
		private int dim;
		private Value content;
		private Value length;
		private Value indivLength;
		
		public MultiDArray(int dim, MultiDArray parent) {
			this.parent = parent;
			this.dim = dim; //inversed
			if (dim > 1) {
				nested = new MultiDArray(dim - 1, this);
			}
		}
		
		public MultiDArray getNested() {
			return nested;
		}
		
		//for external reinit use
		public void setLength(Value val) {
			length = val;
		}
		
		public void setAllLengths(Value val) {
			Value newVal = UserValues.valueOverriding(length, val);
			length = newVal;
		}
		
		public static void setEntireNest(MultiDArray multiArr, int operation, Value val) {
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
				links.forEach(e -> e.setAllLengths(val));
			} else if (operation == 2) 
				links.forEach(e -> e.setAllContents(val));
		}
		
		public void setAllContents(Value val) {
			Value newVal = UserValues.valueOverriding(content, val);
			content = newVal;
		}
		
		private boolean isTainted() {
			if (length instanceof UserValues || content instanceof UserValues)
				return true;
			return false;
		}
		
		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer("MultiDArray," + dim + "," + convertVal(length) + "," + convertVal(content) + "/");
			LinkedList<MultiDArray> links = new LinkedList<>();
			links.addFirst(this);
			MultiDArray currentD = this;
			while (currentD.parent != null) {
				currentD = currentD.parent;
				links.addFirst(currentD);
			}
			currentD = this;
			while (currentD.nested != null) {
				currentD = currentD.nested;
				links.addLast(currentD);
			}
			links.forEach(a -> {
				sb.append(convertVal(a.indivLength) + "/");
			});
			sb.append(links.size());
			return sb.toString();
		}

		@Override
		public int getSize() {
			return 1;
		}
		
		public MultiDArray newMultiArr() {
			MultiDArray copy = new MultiDArray(dim, null);
			MultiDArray.setEntireNest(copy, 2, content);
			MultiDArray currentD = copy;
			MultiDArray oldcurrD = this;
			for (int i = 0; i < dim; i++) {
				currentD.indivLength = oldcurrD.indivLength;
				MultiDArray.setEntireNest(copy, 1, oldcurrD.indivLength);
				currentD = currentD.nested;
				oldcurrD = oldcurrD.nested;
			}
			return copy;
		}
	}
	/*
	 * for fields that are of reference type which when initiated is not user controlled 
	 * but can become so due to methods called on it
	 */
	public static class ReferenceValue implements Value { 
		private Value value;
		private int arglength; // used for <init> method matching later not hundrede percent acc;
		private Map<Integer, Value> initControlledPos; // for methods invoked on reference values that are not fields their position will be noted down to determine the fields controlled by user 
		private boolean isField = false;
		
		private ReferenceValue(Value val) {
			value = val;
		}
		
		private boolean isTainted() {
			if (value instanceof UserValues) {
				return true;
			}
			return false;
		}
		
		private void setVal() {
			value = UserValues.USERDERIVED_REFERENCE;
		}
		
		private void setIsField() {
			isField = true;
		}

		@Override
		public int getSize() {
			return 1;
		}
		
		public ReferenceValue newRef() {
			ReferenceValue copy = new ReferenceValue(value);
			return copy;
		}
	}
}
