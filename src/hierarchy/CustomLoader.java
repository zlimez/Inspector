package hierarchy;

public class CustomLoader extends ClassLoader{
	private byte[] clazz;

	public CustomLoader(byte[] clazzBytes) {
		this.clazz = clazzBytes;
	}
	
	public Class<?> findClass(String name) {
		return defineClass(name, clazz, 0, clazz.length);
	}
}
