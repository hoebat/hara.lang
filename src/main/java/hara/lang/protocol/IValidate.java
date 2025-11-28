package hara.lang.protocol;

import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.*;

import hara.lang.base.*;
import hara.lang.base.Ex;
import hara.lang.base.Std;
import hara.lang.base.Data;
import hara.lang.base.Arr;
import hara.lang.base.It;
import hara.lang.base.Str;
import hara.lang.base.G;

public interface IValidate<V> {
  default Predicate<V> getValidator() {
    return null;
  }

  default boolean validate(V newVal) {
    var f = getValidator();
    if (f == null) return true;

    boolean result = f.test(newVal);
    if (!result) {
      throw new IllegalStateException("Validator rejected value: " + newVal);
    }
    return result;
  }
}
