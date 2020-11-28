package hara.lang.base;

import java.util.Iterator;
import java.util.Map.Entry;
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
	public static String displayList(java.util.List l) {
		return "#j " + It.display(l.iterator());
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static String displayMap(java.util.Map m) {
		return "#j " + It.toString(m.entrySet().iterator(), "{", "}", ",", 
					(entry) -> display(((Entry)entry).getKey()) + " " + display(((Entry)entry).getValue()));
	}
	
	@SuppressWarnings("rawtypes")
	public static String displayMapEntry(Entry e) {
		return "[" + display(e.getKey()) + " " + display(e.getValue()) +  "]";
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static String display(Object e) {
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
			return displayList((java.util.List)e);
		} else if (e instanceof java.util.Map) {
			return displayMap((java.util.Map)e);
		} else if (e instanceof Entry) {
			return displayMapEntry((Entry)e);
		} else if (e instanceof Iterator){
			return "#i " + It.display((Iterator)e);
		} else if (e.getClass().isArray()){
			return "#arr " + It.display(Arr.toIter(e));
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
