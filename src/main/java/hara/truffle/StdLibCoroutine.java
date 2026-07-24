package hara.truffle;

import hara.lang.data.Keyword;

/** Static Java implementation exported exclusively as std.lib.coroutine/*. */
public final class StdLibCoroutine {
  private StdLibCoroutine() {}

  static final Keyword STATUS_SUSPENDED = Keyword.create("suspended");
  static final Keyword STATUS_RUNNING = Keyword.create("running");
  static final Keyword STATUS_DEAD = Keyword.create("dead");

  /** A coroutine backed by a parked (virtual) thread. Thread machinery arrives with resume. */
  static final class HaraCoroutine {
    private final HaraContext context;
    private final Object function;
    private volatile Keyword status = STATUS_SUSPENDED;

    HaraCoroutine(HaraContext context, Object function) {
      this.context = context;
      this.function = function;
    }

    Keyword status() {
      return status;
    }

    @Override
    public String toString() {
      return "#<coroutine " + status + ">";
    }
  }

  private static void requireArity(String name, Object[] values, int expected) {
    if (values.length != expected) {
      throw new HaraException(
          name + " expects " + expected + " argument(s), got " + values.length);
    }
  }

  private static HaraCoroutine requireCoroutine(String name, Object value) {
    Object unwrapped = HaraBox.unwrap(value);
    if (!(unwrapped instanceof HaraCoroutine)) {
      throw new HaraException(name + " expects a coroutine");
    }
    return (HaraCoroutine) unwrapped;
  }

  @HaraExport(
      name = "create",
      doc = "Creates a coroutine wrapping f. The body does not start until the first resume.",
      arglists = {"[f]"})
  public static Object create(HaraContext context, Object[] values) {
    requireArity("coroutine/create", values, 1);
    Object f = HaraBox.unwrap(values[0]);
    if (!(f instanceof HaraFunction)
        && !(f instanceof HaraMultiFunction)
        && !(f instanceof hara.lang.protocol.IFn)) {
      throw new HaraException("coroutine/create expects a function");
    }
    return new HaraCoroutine(context, f);
  }

  @HaraExport(
      name = "coroutine?",
      doc = "Returns true when value is a coroutine.",
      arglists = {"[value]"})
  public static Object coroutinePredicate(HaraContext context, Object[] values) {
    requireArity("coroutine/coroutine?", values, 1);
    return HaraBox.unwrap(values[0]) instanceof HaraCoroutine;
  }

  @HaraExport(
      name = "status",
      doc = "Returns the coroutine status: :suspended, :running, or :dead.",
      arglists = {"[co]"})
  public static Object status(HaraContext context, Object[] values) {
    requireArity("coroutine/status", values, 1);
    return requireCoroutine("coroutine/status", values[0]).status();
  }
}
