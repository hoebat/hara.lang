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

public interface IHashCached extends IHash {

  long hashCurrent();

  @Override
  default long hashGet() {
    long h = hashCurrent();
    if (h == 0) {
      h = hashCalc();
      hashPut(h);
    }
    return h;
  }

  @Override
  default long hashGet(Constant.HashType t) {
    return (hashType() == t) ? hashGet() : hashCalc(t);
  }

  void hashPut(long hash);
}
