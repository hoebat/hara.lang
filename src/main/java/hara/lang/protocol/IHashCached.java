package hara.lang.protocol;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Map;
import hara.lang.base.*;
import java.util.function.*;
import java.util.regex.Pattern;

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