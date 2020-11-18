package hara.lang.interpreter;

import hara.lang.base.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class L {
	
	public I.Assoc assoc(I.Assoc c, Object key, Object val) {
		return c.assoc(key, val);
	}

	public Object assoc(java.util.Map c, Object key, Object val) {
		return Java.assoc(c, key, val);
	}

}
