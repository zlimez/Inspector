package userFields;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.Value;

import methodsEval.MethodInfo;
import methodsEval.MethodTracer;
import userFields.CustomInterpreter.ArrayValue;
import userFields.CustomInterpreter.MultiDArray;
import userFields.CustomInterpreter.ReferenceValue;

/*
 * When an object is a field and is serializable more branching can happen due to read* being invoked however this result is no different from starting an analysis using the
 * object's class: magic method as entry point hence rather than being integral to the gadget chain it is an independent chain itself. Therefore these further invoked methods 
 * will be ignore only their influence on field tainting will be considered.
 */
public class RenderStream extends ClassVisitor{
	String owner;
	MethodTracer mt;
	Map<String, Value> userControlledFields;
	Set<String> magicMethods;
	
	public RenderStream(ClassVisitor cv, String owner, Set<String> methodID) {
		super(Opcodes.ASM9, cv);
		this.owner = owner;
		this.magicMethods = methodID;
		this.userControlledFields = new HashMap<>();
	}
	
	@Override //check transient fields remove from userControlled list
	public FieldVisitor visitField(int acc, String name, String desc, String signature, Object value) {
		if (acc % 256 < Opcodes.ACC_TRANSIENT && acc % 16 < Opcodes.ACC_STATIC) {
			Type type = Type.getType(desc);
			Value fieldValue = null;
			switch (type.getSort()) {
			  case Type.VOID:
		        return null;
		      case Type.BOOLEAN:
		      case Type.CHAR:
		      case Type.BYTE:
		      case Type.SHORT:
		      case Type.INT:
		        fieldValue = UserValues.USERINFLUENCED_INT;
		        break;
		      case Type.FLOAT:
		        fieldValue = UserValues.USERINFLUENCED_FLOAT;
		        break;
		      case Type.LONG:
		        fieldValue = UserValues.USERINFLUENCED_LONG;
		        break;
		      case Type.DOUBLE:
		        fieldValue = UserValues.USERINFLUENCED_DOUBLE;
		        break;
		      case Type.ARRAY:
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
			        element = UserValues.USERINFLUENCED_INT;
			        arrType = Type.INT_TYPE;
			        break;
			      case Type.FLOAT:
			        element = UserValues.USERINFLUENCED_FLOAT;
			        arrType = Type.FLOAT_TYPE;
			        break;
			      case Type.LONG:
			        element = UserValues.USERINFLUENCED_LONG;
			        arrType =Type.LONG_TYPE;
			        break;
			      case Type.DOUBLE:
			        element = UserValues.USERINFLUENCED_DOUBLE;
			        arrType = Type.DOUBLE_TYPE;
			        break;
			      case Type.OBJECT:
			    	element = new ReferenceValue(UserValues.USERINFLUENCED_REFERENCE);
			    	arrType = Type.getObjectType("java/lang/Object");
			    	break;
			      default:
			    	throw new AssertionError("Unknown element type");
  			}
		    	if (dim == 1) {
	    			ArrayValue arr = new ArrayValue(arrType, UserValues.USERINFLUENCED_INT);
	    			arr.setContents(element);
	    			if (element instanceof ReferenceValue)
	    				arr.setField();
	    			fieldValue = arr;
	    		} else {
	    			MultiDArray multiArr = new MultiDArray(arrType, dim, null);
	    			MultiDArray currentD = multiArr;
	    			for (int i = 0; i < dim; i++) {
	    				currentD.setIndivLength(UserValues.USERINFLUENCED_INT);
	    				currentD = currentD.getNested();
	    			}
	    			MultiDArray.setEntireContent(multiArr, 3, UserValues.USERINFLUENCED_INT);
	    			MultiDArray.setEntireContent(multiArr, 1, element);
	    			if (element instanceof ReferenceValue)
	    				multiArr.setField();
	    			fieldValue = multiArr;
	    		}
		    	break;
		      case Type.OBJECT:
		        fieldValue = new ReferenceValue(UserValues.USERINFLUENCED_REFERENCE);
		        ((ReferenceValue) fieldValue).setField(); 
		        break;
		      default:
		        throw new AssertionError("Initialization error");
			}
			userControlledFields.put(owner + "%" + name, fieldValue);
		}
		return cv.visitField(acc, name, desc, signature, value);
	}
	
	@Override
	public MethodVisitor visitMethod(int acc, String name, String desc, String signature, String[] exceptions) {		
		MethodVisitor mv;
		mv = cv.visitMethod(acc, name, desc, signature, exceptions);

		if (magicMethods.contains(name + desc)) {
			if (mt != null) {
				userControlledFields.putAll(mt.getUserControlledFields());
			}
			Map<Integer, Value> userControlledArgPos = new HashMap<>();
			ReferenceValue thisObject = new ReferenceValue(UserValues.USERINFLUENCED_REFERENCE);
			thisObject.setField();
			userControlledArgPos.put(0, thisObject);
			for (int i = 1; i <= MethodInfo.countArgs(desc); i++) {
				userControlledArgPos.put(i, new ReferenceValue(UserValues.USERINFLUENCED_REFERENCE)); // need check if all magic methods arg are reference type
			}
			mv = new MethodTracer(owner, acc, name, desc, mv, userControlledArgPos, userControlledFields, true);
			mt = (MethodTracer) mv;
		}
		return mv;
	}
	
	public Map<String, Value> getUserControlledFields() {
		if (mt != null) 
			userControlledFields.putAll(mt.getUserControlledFields());
		return userControlledFields;
	}
}
