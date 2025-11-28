package hara.lang.protocol;

public class Constant {

	public static final Object[] EMPTY_ARRAY = new Object[] {};
	public static final Boolean F = Boolean.FALSE;
	public static final Boolean T = Boolean.TRUE;

	public enum MetaType { OBJECT, MAP, STRING }
	public enum HashType { SYSTEM, MURMUR3, SIP };
	public enum ObjType { CLASS, KEYWORD, SYMBOL, POINTER, FUNCTION, MAP, SET, ITERATOR, SEQUENTIAL}
}
