package hara.lang.base;

import java.util.function.Function;

public interface G {
	
	public static final Object[] EMPTY_ARRAY = new Object[] {};
	public static final Boolean F = Boolean.FALSE;
	public static final Boolean T = Boolean.TRUE;
	
	public enum MetaType { OBJECT, MAP, STRING }
	public enum HashType { SYSTEM, MURMUR3, SIP };
	public enum ObjType { KEYWORD, SYMBOL, POINTER, FUNCTION, MAP, SET, ITERATOR, SEQUENTIAL}

	public static final HashType DEFAULT_HASH = HashType.MURMUR3;

	public static Function<Object, Long> hashFn(HashType t) {
	
		switch(t) {
		case MURMUR3: 
			return item -> Long.valueOf(Hash.hashMurmur(item));
		case SIP:
			return item -> Long.valueOf(Hash.hashSip(item));
		case SYSTEM:
			return item -> Long.valueOf(item.hashCode());
		default:
			throw new UnsupportedOperationException("Not Supported");
		}
	
		
	}
}
