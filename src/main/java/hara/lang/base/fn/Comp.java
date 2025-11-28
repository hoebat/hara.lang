package hara.lang.base.fn;

import hara.lang.base.Arr;
import hara.lang.base.Fn;
import hara.lang.base.It;
import hara.lang.protocol.IFn;
import hara.lang.protocol.IOFn;
import java.util.function.Function;

@SuppressWarnings({"rawtypes", "unchecked"})
public class Comp<FN, ITR> implements IOFn {

  final IFn[] _fns;

  public Comp(ITR fns) {
    _fns = (IFn[]) It.toArray(It.map(It.iter(fns), Fn::toFn), IFn.class);
  }

  @Override
  public Function getArg1() {
    return (x) -> It.reduce(Arr.toRevIter(_fns), x, (acc, f) -> ((IFn) f).invoke(acc));
  }
}
