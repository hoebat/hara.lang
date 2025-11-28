package hara.lang.protocol;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Map;
import hara.lang.base.*;
import java.util.function.*;
import java.util.regex.Pattern;

public interface IObjType extends IHash, IDisplay {

	default Constant.ObjType getObjType() {
		return Constant.ObjType.CLASS;
	}

	default String getObjName() {
		return getObjType().toString();
	}

	@Override
	default String hashSeed() {
		return "::" + getObjName() + "";
	}

	IMetadata meta();

	IObjType withMeta(IMetadata meta);
}
