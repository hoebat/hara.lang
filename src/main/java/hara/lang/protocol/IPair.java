package hara.lang.protocol;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Map;
import hara.lang.base.*;
import java.util.function.*;
import java.util.regex.Pattern;

public interface IPair<K, V> extends Map.Entry<K, V> {
	@Override
	default V setValue(V value) {
		throw new Ex.Unsupported();
	}
}