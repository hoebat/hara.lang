package hara.lang.protocol;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Map;
import hara.lang.base.*;
import java.util.function.*;
import java.util.regex.Pattern;

public interface IHash {

	default long hashCalc() {
		return hashCalc(hashType());
	}

	long hashCalc(Constant.HashType t);

	default long hashGet() {
		return hashCalc(hashType());
	}

	default long hashGet(Constant.HashType t) {
		return hashCalc(t);
	}

	String hashSeed();;

	default Constant.HashType hashType() {
		return G.DEFAULT_HASH;
	}
}