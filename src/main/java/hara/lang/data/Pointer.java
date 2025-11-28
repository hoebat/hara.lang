package hara.lang.data;

import hara.lang.base.*;
import hara.lang.protocol.Constant;
import hara.lang.protocol.*;

public class Pointer extends Data.NamespacedType.PT implements Data.StringType {

	public Pointer(IMetadata meta, String ns, String name) {
		super(meta, ns, name);
	}

	@Override
	public String display(){
		return "#'" + pathString();
	}

	@Override
	public Pointer withMeta(IMetadata meta) {
		return (_meta == meta) ? this : new Pointer(meta, getNamespace(), getName());
	}

	@Override
	public Constant.ObjType getObjType() {
		return Constant.ObjType.POINTER;
	}
}