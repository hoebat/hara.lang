package hara.lang.base;

import java.util.Iterator;
import java.util.regex.Pattern;

public interface G {
	
	public static final Object[] EMPTY_ARRAY = new Object[] {};
	public static final Boolean F = Boolean.FALSE;
	public static final Boolean T = Boolean.TRUE;
	
	public enum MetaType { OBJECT, MAP, STRING }
	public enum HashType { SYSTEM, MURMUR3, SIP };
	public enum ObjType { KEYWORD, SYMBOL, POINTER, FUNCTION, MAP, SET, ITERATOR, SEQUENTIAL}

	public static final HashType DEFAULT_HASH = HashType.MURMUR3;
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static String displayItem(Object e) {
		if(e == null) {
			return "nil";
		} else if (e instanceof I.Display) {
			return ((I.Display)e).display();
		} else if (e instanceof String) {
			return "\"" + Str.escapeJava((String)e) +  "\"";
		} else if (e instanceof Character) {
			return "\\" + e.toString();
		} else if (e instanceof Pattern) {
			return "#\"" + Str.escapeJava(e.toString()) + "\"";
		} else if (e instanceof Class) {
			return ((Class)e).getName();
		} else if (e instanceof java.util.List) {
			return It.display(((java.util.List)e).iterator());
		} else if (e instanceof Iterator){
			return It.display((Iterator)e);
		} else {
			return e.toString();
		}
	}
	
	public static String getLineNumber() {
		var curr = Thread.currentThread().getStackTrace()[3];
		return curr.getClassName() + "/" + curr.getMethodName() + " - L" + curr.getLineNumber();
	}
	
	public static void prn(Object... arr) {
		System.out.println(getLineNumber() + ":\n" + Arr.display(arr));
	}
}
