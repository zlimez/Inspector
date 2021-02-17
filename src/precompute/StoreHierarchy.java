package precompute;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

// outer class need to be serializable for nested class to be
public class StoreHierarchy implements Serializable {
	private static final long serialVersionUID = 1L;
	private Map<Class<?>, Map<Class<?>, byte[]>> hierarchy;
	private Map<Class<?>, List<Object>> entries;

	protected StoreHierarchy(Map<Class<?>, Map<Class<?>, byte[]>> hierarchy, Map<Class<?>, List<Object>> entries) {
		this.hierarchy = hierarchy;
		this.entries = entries;
	}
	
	public Map<Class<?>, Map<Class<?>, byte[]>> getHierarchy() {
		return hierarchy;
	}
	
	public Map<Class<?>, List<Object>> getEntryPoints() {
		return entries;
	}
}
