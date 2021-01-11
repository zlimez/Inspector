package chain;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Enumerate {
	/* 
	 * Convert Class X instance to bytecode 
	 * Feed the bytecode to ASM to convert to opscode
	 * check for control flow 
	 * verify conditions can be fulfilled using user provided fields
	 * Look into reachable invoked method regex methods call opscode
	 * return method name and target object 
	 * filter serializable classes for subclass of target object with identical method signature
	 * check method parameters contains constructor based user client instantiated field
	 */
	private static Map<Class<?>, byte[]> serialClazzes;
	
	public Enumerate() throws ClassNotFoundException, IOException {
		serialClazzes = hierarchy.SortClass.getSerialClasses();
	}
	
	private static class Component { 
		private Component parent;
		private Class<?> clazz;
		private Method method;
		private List<Component> children;
		private byte[] byteContent;
		
		public Component(Class<?> type, Method m, Component parent, byte[] b) {
			this.clazz = type;
			this.method = m;
			this.parent = parent;
			this.children = null;
			this.byteContent = b;
		}
		
		public void findChildrenComponents(Method $method, Class<?> $clazz) {
			Enumerate.serialClazzes.forEach((k, v) -> {
				if ($clazz.isAssignableFrom(k)) {
					Method[] availMethods = k.getDeclaredMethods();
					outerloop:
					for (Method m : availMethods) {
						if (m.getName().equals($method.getName()) && m.getReturnType().getCanonicalName().equals($method.getReturnType().getCanonicalName())) {
							Class<?>[] paramTypes = m.getParameterTypes();
							Class<?>[] $paramTypes = $method.getParameterTypes();
							if (paramTypes.length == $paramTypes.length) {
								for (int i = 0; i < paramTypes.length; i++) {
									if (!paramTypes[i].getCanonicalName().equals($paramTypes[i].getCanonicalName())) {
										break outerloop;
									}
								}
							}
						}
						Component nextComponent = new Component(k, $method, this, v);
						this.children.add(nextComponent);
					}
				}
			});
		}
	}
}
