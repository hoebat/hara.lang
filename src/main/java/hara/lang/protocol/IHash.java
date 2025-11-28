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