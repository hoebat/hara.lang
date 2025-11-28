package hara.lang.protocol;

import java.util.function.Predicate;

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
