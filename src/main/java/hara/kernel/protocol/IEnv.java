package hara.kernel.protocol;

import java.util.Iterator;
import java.util.Map.Entry;
import hara.lang.base.Ex;
import hara.lang.base.I;

public interface IEnv<K, V> extends I.Lookup<K, V> {
	IEnv<K, V> getParent();

	I.Lookup<K, V> getMap();

	@SuppressWarnings("rawtypes")
	IRuntime getRuntime();

	@Override
	default Entry<K, V> find(K k) {
		Entry<K, V> e = getMap().find(k);
		if(e == null) {
			IEnv<K, V> env = getParent();
			if(env != null) {
				return env.find(k);
			}
		}
		return e;
	}

	@Override
	default Iterator<K> keys() {
		throw new Ex.Unsupported();
	}

	@Override
	default Iterator<V> vals() {
		throw new Ex.Unsupported();
	}
}
