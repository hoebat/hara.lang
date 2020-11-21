package hara.lang.data;
import java.lang.ref.WeakReference;

import hara.lang.base.*;

public final class Keyword implements
	Comparable<Keyword>,
	Coll.StringType,
	I.Namespaced, 
	I.Display,
	I.ObjType,
	Fn.OFn,
	I.Metadata {
	public static Ut.RefCache<String, Keyword> GLOBAL = new Ut.RefCache<String, Keyword>();
	
	private final String _ns;
	private final String _name;
	private final String _full;

	private Keyword(String ns, String name, String full) {
		_ns = ns;
		_name = name;
		_full = full;
	}

	public static Keyword create(String ns, String name) {
		String full = (ns == null) ? name : ns + "/" + name;
		
		return GLOBAL.getOrCreate(full, () -> {
			var k = new Keyword(ns, name, full);
			k.hashGet();
			return new WeakReference<Keyword>(k, GLOBAL.getQueue());
		});	
	}
	
	public static Keyword create(String nsname){
		int i = nsname.indexOf('/');
		if(i == -1 || nsname.equals("/"))
			return create(null, nsname);
		else
			return create(nsname.substring(0, i), nsname.substring(i + 1));
	}
	
	@Override
	public int compareTo(Keyword o) {
		return this._full.compareTo(o._full);
	}

	@Override
	public String toString(){
		return ":" + _full;
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

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	final public Object invoke(Object obj) {
		if (obj instanceof I.Lookup) {
			return ((I.Lookup) obj).lookup(this); 
		} else if (obj instanceof java.util.Map) {
			return ((java.util.Map) obj).get(this);
		} else if (obj instanceof I.Context) {
			return ((I.Context) obj).call(this);
		} else {
			throw new Ex.Unsupported();
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	final public Object invoke(Object obj, Object arg) {
		if (obj instanceof I.Lookup) {
			return ((I.Lookup) obj).lookup(this, arg); 
		} else if (obj instanceof java.util.Map) {
			return ((java.util.Map) obj).getOrDefault(this, arg);
		} else if (obj instanceof I.Context) {
			return ((I.Context) obj).call(this, arg);
		} else {
			throw new Ex.Unsupported();
		}
	}

	@Override
	final public Object invoke(Object... args) {
		if(args.length > 0) {
			if (args[0] instanceof I.Context) {
				I.Context ctx = (I.Context)args[0];
				args[0] = this;
				return ctx.call(args);
			} 
		}
		throw new Ex.Unsupported();
	}
}