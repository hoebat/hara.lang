package hara.lang.data;

import java.lang.ref.WeakReference;

import hara.lang.base.*;

public class Symbol extends Data.NamespacedType.PT 
	implements Data.StringType {

	public static Ut.RefCache<String, Symbol> GLOBAL = new Ut.RefCache<String, Symbol>();
	
	public static Symbol create(String ns, String name) {
		String full = (ns == null) ? name : ns + "/" + name;
		
		return GLOBAL.getOrCreate(full, () -> {
			var k = new Symbol(null, ns, name);
			return new WeakReference<Symbol>(k, GLOBAL.getQueue());
		});	
	}

	public static Symbol create(String nsname){
		int i = nsname.indexOf('/');
		if(i == -1 || nsname.equals("/"))
			return create(null, nsname);
		else
			return create(nsname.substring(0, i), nsname.substring(i + 1));
	}
	
	public Symbol(I.Metadata meta, String nsname) {
		super(meta, nsname);
	}

	public Symbol(I.Metadata meta, String ns, String name) {
		super(meta, ns, name);
	}

	/*
	@Override
	public String toString(){
		return pathString();
	}
	*/
	
	@Override
	public Symbol withMeta(I.Metadata meta) {
		return (_meta == meta) ? this : new Symbol(meta, getNamespace(), getName());
	}
	
	@Override
	public G.ObjType getObjType() {
		return G.ObjType.SYMBOL;
	}

	@Override
	public String display() {
		return pathString();
	}
}