package hara.lang.protocol;

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
