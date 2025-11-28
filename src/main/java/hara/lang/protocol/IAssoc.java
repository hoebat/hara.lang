package hara.lang.protocol;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Map;
import hara.lang.base.*;
import java.util.function.*;
import java.util.regex.Pattern;

public interface IAssoc<K, V> {
	IAssoc<K, V> assoc(K k, V v);
}