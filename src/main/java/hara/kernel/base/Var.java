package hara.kernel.base;

import hara.lang.protocol.*;
import hara.lang.base.*;
import hara.lang.data.*;

import static hara.kernel.base.Builtin.Basic.*;

public class Var extends Data.NamespacedType.MT 
	implements Data.StringType, Data.VarType, IReset<Object> {

	Object _val;

	public Var(String nsname, Object v) {
		super(Map.Standard.EMPTY, nsname);
		_val = v;
	}

	@Override
	public Object deref() {
		return _val;
	}

	@Override
	public String display(){
		return "#'" + pathString();
	}

	@Override
	public Constant.ObjType getObjType() {
		return Constant.ObjType.POINTER;
	}
	

	@Override
	public Boolean isControl() {
		return (Boolean) keyword("control").invoke(_meta, false);
	}

	@Override
	public Boolean isDynamic() {
		return (Boolean) keyword("dynamic").invoke(_meta, false);
	}

	@Override
	public Boolean isMacro() {
		return (Boolean) keyword("macro").invoke(_meta, false);
	}

	@Override
	public Object reset(Object v) {
		return _val = v;
	}
}