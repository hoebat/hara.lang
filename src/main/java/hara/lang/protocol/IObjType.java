package hara.lang.protocol;

import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.*;

import hara.lang.base.*;
import hara.lang.base.Ex;
import hara.lang.data.*;
import hara.data.types.*;
import hara.lang.base.Arr;
import hara.lang.base.It;
import hara.lang.base.Str;
import hara.lang.base.G;

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
