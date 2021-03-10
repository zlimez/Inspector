package userFields;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.Value;


public class UserValues implements Value {
	private final Type type;
	private final String descriptor;
	
//	public static final UserValues UNINITIALIZED_VALUE = new UserValues(null);
//	public static final UserValues INT_VALUE = new UserValues(Type.INT_TYPE);
//	public static final UserValues FLOAT_VALUE = new UserValues(Type.FLOAT_TYPE);
//	public static final UserValues LONG_VALUE = new UserValues(Type.LONG_TYPE);
//	public static final UserValues DOUBLE_VALUE = new UserValues(Type.DOUBLE_TYPE);
//	public static final UserValues REFERENCE_VALUE = new UserValues(Type.getObjectType("java/lang/Object"));
	
	public static final UserValues USERINFLUENCED_INT = new UserValues(Type.INT_TYPE, "Influenced");
	public static final UserValues USERINFLUENCED_FLOAT = new UserValues(Type.FLOAT_TYPE, "Influenced");
	public static final UserValues USERINFLUENCED_LONG = new UserValues(Type.LONG_TYPE, "Influenced");
	public static final UserValues USERINFLUENCED_DOUBLE = new UserValues(Type.DOUBLE_TYPE, "Influenced");
	public static final UserValues USERINFLUENCED_REFERENCE = new UserValues(Type.getObjectType("java/lang/Object"), "Influenced");
	
	public static final UserValues USERDERIVED_INT = new UserValues(Type.INT_TYPE, "Derived");
	public static final UserValues USERDERIVED_FLOAT = new UserValues(Type.FLOAT_TYPE, "Derived");
	public static final UserValues USERDERIVED_LONG = new UserValues(Type.LONG_TYPE, "Derived");
	public static final UserValues USERDERIVED_DOUBLE = new UserValues(Type.DOUBLE_TYPE, "Derived");
	public static final UserValues USERDERIVED_REFERENCE = new UserValues(Type.getObjectType("java/lang/Object"), "Derived");
	
//	public UserValues(final Type type) {
//		this.type = type;
//		descriptor = "Independent";
//	}
	
	public UserValues(final Type type, final String desc) {
		this.type = type;
		this.descriptor = desc;
	}

	@Override
	public int getSize() {
		return type == Type.LONG_TYPE || type == Type.DOUBLE_TYPE ? 2 : 1;
	}
	
	public boolean isInfluenced() {
		if (descriptor.equals("Influenced")) {
			return true;
		}
		return false;
	}
	
	public static Value valueOverriding(Value oldVal, Value newVal) {
		if (oldVal == USERDERIVED_INT || oldVal == USERDERIVED_FLOAT || oldVal == USERDERIVED_DOUBLE || oldVal == USERDERIVED_LONG || oldVal == USERDERIVED_REFERENCE) {
			return oldVal;
		} else {
			if (oldVal != null && (oldVal == USERINFLUENCED_INT || oldVal == USERINFLUENCED_FLOAT || oldVal == USERINFLUENCED_DOUBLE || oldVal == USERINFLUENCED_LONG || oldVal == USERINFLUENCED_REFERENCE)) {
				if (oldVal == USERINFLUENCED_INT) {
					return USERDERIVED_INT;
				} else if (oldVal == USERINFLUENCED_FLOAT) {
					return USERDERIVED_FLOAT;
				} else if (oldVal == USERINFLUENCED_DOUBLE) {
					return USERDERIVED_DOUBLE;
				} else if (oldVal == USERINFLUENCED_LONG) {
					return USERDERIVED_LONG;
				} else {
					return USERDERIVED_REFERENCE;
				}
			} else {
				return newVal;
			}
		}
	}
}
