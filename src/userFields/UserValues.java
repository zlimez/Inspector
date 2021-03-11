package userFields;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.Value;

import userFields.CustomInterpreter.ReferenceValue;


public class UserValues implements Value, Serializable {
	private static final long serialVersionUID = 1L;
	private transient Type type;
	protected final int typecode; // used to represent type when serialized
	private final String descriptor;
	
	public static final UserValues UNINITIALIZED_VALUE = new UserValues(null, 0);
	public static final UserValues INT_VALUE = new UserValues(Type.INT_TYPE, 1);
	public static final UserValues FLOAT_VALUE = new UserValues(Type.FLOAT_TYPE, 2);
	public static final UserValues LONG_VALUE = new UserValues(Type.LONG_TYPE, 3);
	public static final UserValues DOUBLE_VALUE = new UserValues(Type.DOUBLE_TYPE, 4);
	public static final UserValues REFERENCE_VALUE = new UserValues(Type.getObjectType("java/lang/Object"), 5);
	public static final UserValues RETURNADDRESS_VALUE = new UserValues(Type.VOID_TYPE, 6);
	
	public static final UserValues USERINFLUENCED_INT = new UserValues(Type.INT_TYPE, "Influenced", 1);
	public static final UserValues USERINFLUENCED_FLOAT = new UserValues(Type.FLOAT_TYPE, "Influenced", 2);
	public static final UserValues USERINFLUENCED_LONG = new UserValues(Type.LONG_TYPE, "Influenced", 3);
	public static final UserValues USERINFLUENCED_DOUBLE = new UserValues(Type.DOUBLE_TYPE, "Influenced", 4);
	public static final UserValues USERINFLUENCED_REFERENCE = new UserValues(Type.getObjectType("java/lang/Object"), "Influenced", 5);
	
	public static final UserValues USERDERIVED_INT = new UserValues(Type.INT_TYPE, "Derived", 1);
	public static final UserValues USERDERIVED_FLOAT = new UserValues(Type.FLOAT_TYPE, "Derived", 2);
	public static final UserValues USERDERIVED_LONG = new UserValues(Type.LONG_TYPE, "Derived", 3);
	public static final UserValues USERDERIVED_DOUBLE = new UserValues(Type.DOUBLE_TYPE, "Derived", 4);
	public static final UserValues USERDERIVED_REFERENCE = new UserValues(Type.getObjectType("java/lang/Object"), "Derived", 5);

	public UserValues(final Type type, int typecode) {
		this.type = type;
		descriptor = "Independent";
		this.typecode = typecode;
	}
	
	public UserValues(final Type type, final String desc, int typecode) {
		this.type = type;
		this.descriptor = desc;
		this.typecode = typecode;
	}
	
	public boolean isInfluenced() {
		if (descriptor.equals("Influenced")) {
			return true;
		}
		return false;
	}
	
	public boolean isTainted() {
		if (descriptor.equals("Independent"))
			return false;
		return true;
	}
	
	public Type getType() {
		return type;
	}
	
	@Override
	public int getSize() {
		return type == Type.LONG_TYPE || type == Type.DOUBLE_TYPE ? 2 : 1;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((descriptor == null) ? 0 : descriptor.hashCode());
		result = prime * result + typecode;
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
		UserValues other = (UserValues) obj;
		if (descriptor == null) {
			if (other.descriptor != null)
				return false;
		} else if (!descriptor.equals(other.descriptor))
			return false;
		if (typecode != other.typecode)
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "UserValues [type=" + type + ", descriptor=" + descriptor + "]";
	}
	
	public static Value valueOverriding(Value oldVal, Value newVal) {
		// used when array contains ref values
		if (oldVal == null)
			return newVal;
		if (oldVal instanceof ReferenceValue && newVal instanceof ReferenceValue) {
			UserValues oldContent = ((ReferenceValue) oldVal).getValue();
			UserValues newContent = ((ReferenceValue) newVal).getValue();
			if (oldContent == USERDERIVED_REFERENCE) 
				return oldVal;
			if (oldContent != USERINFLUENCED_REFERENCE) {
				if (newContent.isTainted()) 
					return new ReferenceValue(USERDERIVED_REFERENCE);
				return newVal;
			} else {
				if (newContent == USERINFLUENCED_REFERENCE) 
					return newVal;
				return new ReferenceValue(USERDERIVED_REFERENCE);
			}
		} 
		
		// used to tackle primitive values overriding
		UserValues oldContent = (UserValues) oldVal;
		UserValues newContent = (UserValues) newVal;
		if (oldContent.isTainted() && !oldContent.isInfluenced()) {
			return oldVal; // when content is derived
		} 
		if (!oldContent.isInfluenced()) {
			if (newContent.isTainted()) {
				if (newContent == USERINFLUENCED_INT) {
					return USERDERIVED_INT;
				} else if (newContent == USERINFLUENCED_FLOAT) {
					return USERDERIVED_FLOAT;
				} else if (newContent == USERINFLUENCED_DOUBLE) {
					return USERDERIVED_DOUBLE; 
				} else if (newContent == USERINFLUENCED_DOUBLE)
					return USERDERIVED_LONG;
			}
			return newVal; // both new and old are not tainted
		} else {
			if (newContent.isInfluenced()) 
				return newVal;
			if (oldContent == USERINFLUENCED_INT) 
				return USERDERIVED_INT;
			if (oldContent == USERINFLUENCED_FLOAT) 
				return USERDERIVED_FLOAT;
			if (oldContent == USERINFLUENCED_DOUBLE)
				return USERDERIVED_DOUBLE; 
			return USERDERIVED_LONG;
		}		
	}
	
	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		ois.defaultReadObject();
		switch (this.typecode) {
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
