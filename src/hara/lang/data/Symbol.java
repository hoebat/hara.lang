package hara.lang.data;

import hara.lang.base.*;

public class Symbol extends Coll.NamespacedType.PT implements Coll.StringType {
	
	public Symbol(I.Metadata meta, String nsname) {
		super(meta, nsname);
	}

	public Symbol(I.Metadata meta, String ns, String name) {
		super(meta, ns, name);
	}

	@Override
	public String toString(){
		return pathString();
	}
	
	@Override
	public Symbol withMeta(I.Metadata meta) {
		return (_meta == meta) ? this : new Symbol(meta, getNamespace(), getName());
	}
	
	@Override
	public G.ObjType getObjType() {
		return G.ObjType.SYMBOL;
	}
}