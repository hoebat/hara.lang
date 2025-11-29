package hara.lang.protocol;

// import hara.lang.base.G;

public interface IHash {

  default long hashCalc() {
    // return hashCalc(hashType());
    return hashCalc(Constant.HashType.MURMUR3);
  }

  long hashCalc(Constant.HashType t);

  default long hashGet() {
    // return hashCalc(hashType());
    return hashCalc(Constant.HashType.MURMUR3);
  }

  default long hashGet(Constant.HashType t) {
    return hashCalc(t);
  }

  default String hashSeed() {
    return "";
  }

  default Constant.HashType hashType() {
    // return G.DEFAULT_HASH;
    return Constant.HashType.MURMUR3;
  }
}
