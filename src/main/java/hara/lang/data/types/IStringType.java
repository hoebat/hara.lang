package hara.lang.data.types;

import hara.lang.protocol.Constant;
import hara.lang.protocol.IHash;

public interface IStringType extends IHash {

  @Override
  default long hashCalc(Constant.HashType t) {
    switch (t) {
      case SYSTEM:
        return (hashSeed() + "|" + toString()).hashCode();
      case RAPID:
        return hara.lang.base.primitive.RapidHash.hash(hashSeed() + "|" + toString());
      case SIP:
        break;
      default:
        throw new UnsupportedOperationException("Not Supported");
    }
    return -1;
  }
}
