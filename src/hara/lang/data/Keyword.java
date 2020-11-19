package hara.lang.data;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

import hara.lang.base.*;

public final class Keyword implements 
	T.StringType,
	I.Namespaced, 
	I.Display,
	I.ObjType,
	CFn,
	I.Metadata {
	private static ConcurrentHashMap<String, Reference<Keyword>> LU = new ConcurrentHashMap<String, Reference<Keyword>>();
	private static ReferenceQueue<Keyword> RQ = new ReferenceQueue<Keyword>();
			
	private final long _hash;
	private final String _ns;
	private final String _name;
	private final String _full;

	private Keyword(String ns, String name, String full) {
		_ns = ns;
		_name = name;
		_full = full;
		_hash = hashCalc();
	}
	
	public static Keyword create(String ns, String name) {
		String full = (ns == null) ? name : ns + "/" + name;
		Reference<Keyword> ref = LU.get(full);
		Keyword k;
		if (ref != null) {
			k = ref.get();
			if (k != null) {
				return k;
			}
		}

		k = new Keyword(ns, name, full);
		LU.put(full, new WeakReference<Keyword>(k, RQ));
		return k;
	}
	
	public static Keyword create(String nsname){
		int i = nsname.indexOf('/');
		if(i == -1 || nsname.equals("/"))
			return create(null, nsname);
		else
			return create(nsname.substring(0, i), nsname.substring(i + 1));
	}
	
	@Override
	public String toString(){
		return ":" + _full;
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	final public Object invoke(Object obj) {
		return (obj instanceof I.Lookup)
			? ((I.Lookup) obj).lookup(this)
			: null;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	final public Object invoke(Object obj, Object notFound) {
		return (obj instanceof I.Lookup)
			? ((I.Lookup) obj).lookup(this, notFound)
		    : notFound;
	}

	@Override
	public String getName() {
		return _name;
	}

	@Override
	public String getNamespace() {
		return _ns;
	}

	@Override
	public G.ObjType getObjType() {
		return G.ObjType.KEYWORD;
	}

	@Override
	public I.Metadata meta() {
		return this;
	}

	@Override
	public Keyword withMeta(I.Metadata meta) {
		return this;
	}
	
	@Override
	public G.MetaType getMetatype() {
		return G.MetaType.STRING;
	}	
}