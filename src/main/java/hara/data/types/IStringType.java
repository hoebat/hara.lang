package hara.data.types;

import hara.lang.base.Ut;
import hara.lang.protocol.Constant;
import hara.lang.protocol.IHash;

public interface IStringType extends IHash {

  @Override
  default long hashCalc(Constant.HashType t) {
    switch (t) {
      case SYSTEM:
        return (hashSeed() + "|" + toString()).hashCode();
      case MURMUR3:
        return Ut.Murmur3.hashChars(hashSeed() + "|" + toString());
      case SIP:
        break;
      default:
        throw new UnsupportedOperationException("Not Supported");
    }
    return -1;
  }
}
