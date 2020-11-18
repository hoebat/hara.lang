package hara.lang.data;

import hara.lang.base.*;

public class Pointer extends T.NamespacedType.PT implements T.StringType {

	public Pointer(I.Metadata meta, String ns, String name) {
		super(meta, ns, name);
	}

	public String toString(){
		return "#'" + pathString();
	}

	@Override
	public Pointer withMeta(I.Metadata meta) {
		return (_meta == meta) ? this : new Pointer(meta, getNamespace(), getName());
	}

	@Override
	public G.ObjType getObjType() {
		return G.ObjType.POINTER;
	}
}