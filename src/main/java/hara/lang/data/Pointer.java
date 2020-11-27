package hara.lang.data;

import hara.lang.base.*;

public class Pointer extends Data.NamespacedType.PT implements Data.StringType {

	public Pointer(I.Metadata meta, String ns, String name) {
		super(meta, ns, name);
	}

	@Override
	public String display(){
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