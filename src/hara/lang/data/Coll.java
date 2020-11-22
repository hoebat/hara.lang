package hara.lang.data;

public interface Coll {
	
	public static <V> Atom.Standard<V> atom(V val) {
		return new Atom.Standard<V>(val);
	}
	
	public static <V> Atom.Basic<V> atomBasic(V val) {
		return new Atom.Basic<V>(val);
	}

}
