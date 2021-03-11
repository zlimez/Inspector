package methodsEval;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.Value;

import org.objectweb.asm.FieldVisitor;
import userFields.UserValues;
import userFields.CustomInterpreter.ArrayValue;
import userFields.CustomInterpreter.MultiDArray;
import userFields.CustomInterpreter.ReferenceValue;

// classes without readObject magic method
public class RenderClass extends ClassVisitor {
	String owner;
	String methodName;
	String descriptor;
	Map<Integer, Value> userControlledArgPos;
	MethodTracer mt;
	Map<String, Value> userControlledFields;
	ReferenceValue ownerInstance;
	boolean isField;
	boolean isMagic;
	
	// ownerInstance != null and isField = true cannot happen at the same time
	public RenderClass(ClassVisitor cv, String owner, String methodName, String descriptor, Map<Integer, Value> userControlledArgPos, boolean isField, ReferenceValue ownerObject) {
		super(Opcodes.ASM9, cv);
		this.owner = owner;
		this.methodName = methodName;
		this.descriptor = descriptor;
		this.userControlledArgPos = userControlledArgPos;
		this.userControlledFields = new HashMap<>();
		this.isField = isField;
		this.ownerInstance = ownerObject;
	}
	
	@Override
	public FieldVisitor visitField(int acc, String name, String desc, String signature, Object value) {
		if (isField && acc % 256 < Opcodes.ACC_TRANSIENT && acc % 16 < Opcodes.ACC_STATIC) {
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
			        arrType = Type.LONG_TYPE;
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
		if (ownerInstance != null && name.equals("<init>") && desc.equals(ownerInstance.getInitDesc())) { 
			mv = new MethodTracer(owner, acc, name, desc, mv, ownerInstance.getinitControlledArgPos(), userControlledFields); // userControlledFields is empty in this case
			mt = (MethodTracer) mv;
		} else if (name.equals(methodName) && desc.equals(descriptor)) {
			if (mt != null) {
				userControlledFields = mt.getUserControlledFields();
			}
			// USER_INFLUENCED should take precedence over USER_DERIVED	
			if (isField && (methodName.equals("readObject") || methodName.equals("readResolve") || methodName.equals("validateObject") || methodName.equals("readObjectNoData") || methodName.equals("readExternal"))) {
				mv = new MethodTracer(owner, acc, name, desc, mv, userControlledArgPos, userControlledFields, true);
			} else
				mv = new MethodTracer(owner, acc, name, desc, mv, userControlledArgPos, userControlledFields);
			mt = (MethodTracer) mv;
		} // assume constructor comes before any methods
		return mv;
	}
	
	public Collection<MethodInfo> getNextInvokedMethods() {
		return mt.getNextInvokedMethods();
	}
}
