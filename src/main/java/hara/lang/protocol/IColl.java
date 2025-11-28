package hara.lang.protocol;

import hara.lang.base.It;

public interface IColl<E>
    extends Iterable<E>, IEquality, IConj<E>, IEmpty, ICount, IHash, IDisplay {

  String startString();

  String endString();

  default String sepString() {
    return " ";
  }

  @Override
  default String display() {
    return It.display(iterator(), startString(), endString(), sepString());
  }
}
