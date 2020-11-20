package hara.lang.data;

import hara.lang.base.*;

public class Pointer extends C.NamespacedType.PT implements C.StringType {

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