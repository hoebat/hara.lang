package hara.lang.protocol;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Map;
import hara.lang.base.*;
import java.util.function.*;
import java.util.regex.Pattern;

public interface IValidate<V> {
	default Predicate<V> getValidator() {
		return null;
	}

	default boolean validate(V newVal) {
		var f = getValidator();
		if (f == null)
			return true;

		boolean result = f.test(newVal);
		if(!result) {
			throw new IllegalStateException("Validator rejected value: " + newVal);
		}
		return result;
	}
}