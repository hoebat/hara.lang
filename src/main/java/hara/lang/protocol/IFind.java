package hara.lang.protocol;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Map;
import hara.lang.base.*;
import java.util.function.*;
import java.util.regex.Pattern;

public interface IFind<K, V> {
	V find(K key);

	default boolean has(K key) {
		return find(key) != null;
	}
}