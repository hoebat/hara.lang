package hara.truffle;

/** A registered implementation together with its optional Hara function specialization. */
public final class HaraProtocolImplementation {
  private final HaraProtocolInvoker invoker;
  private final HaraFunction function;

  HaraProtocolImplementation(HaraProtocolInvoker invoker, HaraFunction function) {
    this.invoker = invoker;
    this.function = function;
  }

  public HaraProtocolInvoker invoker() {
    return invoker;
  }

  public HaraFunction function() {
    return function;
  }

  public Object invoke(Object receiver, Object[] arguments) {
    return invoker.invoke(receiver, arguments);
  }
}
