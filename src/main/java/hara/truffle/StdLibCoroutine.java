package hara.truffle;

import hara.lang.data.Keyword;
import java.util.concurrent.SynchronousQueue;
import org.graalvm.nativeimage.ImageInfo;

/** Static Java implementation exported exclusively as std.lib.coroutine/*. */
public final class StdLibCoroutine {
  private StdLibCoroutine() {}

  static final Keyword STATUS_SUSPENDED = Keyword.create("suspended");
  static final Keyword STATUS_RUNNING = Keyword.create("running");
  static final Keyword STATUS_DEAD = Keyword.create("dead");

  /** Thrown on the coroutine thread when a parked coroutine is closed. Unwinds the body. */
  static final class CoroutineClosed extends RuntimeException {
    CoroutineClosed() {
      super(null, null, false, false);
    }
  }

  /** One handoff across the resume/yield boundary. */
  static final class Transfer {
    final Object value;
    final Throwable error;

    Transfer(Object value, Throwable error) {
      this.value = value;
      this.error = error;
    }
  }

  /** A coroutine backed by a parked (virtual) thread. */
  static final class HaraCoroutine {
    private final HaraContext context;
    private final Object function;
    private final SynchronousQueue<Object> input = new SynchronousQueue<>();
    private final SynchronousQueue<Transfer> output = new SynchronousQueue<>();
    private volatile Keyword status = STATUS_SUSPENDED;
    private volatile Thread thread;

    HaraCoroutine(HaraContext context, Object function) {
      this.context = context;
      this.function = function;
    }

    Keyword status() {
      return status;
    }

    Object resume(Object[] args) {
      synchronized (this) {
        if (status == STATUS_DEAD) {
          throw new HaraException("coroutine/resume: cannot resume a dead coroutine");
        }
        if (status == STATUS_RUNNING) {
          throw new HaraException("coroutine/resume: cannot resume a running coroutine");
        }
        status = STATUS_RUNNING;
      }
      if (thread == null) {
        start(args);
      } else {
        putInput(pack(args));
      }
      Transfer transfer = takeOutput();
      if (transfer.error != null) {
        if (transfer.error instanceof RuntimeException) throw (RuntimeException) transfer.error;
        throw new HaraException("coroutine failed: " + transfer.error);
      }
      return transfer.value;
    }

    private void start(Object[] args) {
      Runnable body = () -> runBody(args);
      Thread started =
          ImageInfo.inImageRuntimeCode()
              ? new Thread(body, "hara-coroutine")
              : Thread.ofVirtual().name("hara-coroutine").unstarted(body);
      thread = started;
      started.start();
    }

    private void runBody(Object[] args) {
      try {
        Object result = context.invokeInContext(() -> context.invokeCallable(function, args));
        status = STATUS_DEAD;
        putOutput(new Transfer(result, null));
      } catch (CoroutineClosed closed) {
        // Closed while parked: the unwinding already ran the body's finally clauses.
        Thread.currentThread().interrupt();
      } catch (Throwable error) {
        status = STATUS_DEAD;
        putOutput(new Transfer(null, error));
      }
    }

    private void putInput(Object value) {
      try {
        input.put(value);
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new HaraException("coroutine/resume: interrupted while resuming");
      }
    }

    private void putOutput(Transfer transfer) {
      try {
        output.put(transfer);
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new CoroutineClosed();
      }
    }

    Object takeInput() {
      try {
        return input.take();
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new CoroutineClosed();
      }
    }

    private Transfer takeOutput() {
      try {
        return output.take();
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new HaraException("coroutine/resume: interrupted while waiting for the coroutine");
      }
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

  private static Object pack(Object[] values) {
    if (values.length == 0) return null;
    if (values.length == 1) return values[0];
    return hara.lang.data.Vector.Standard.from(null, values.clone());
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

  @HaraExport(
      name = "resume",
      doc =
          "Starts or continues a coroutine. First resume passes args to the body; later resumes"
              + " deliver args as the yield return. Returns the yielded or final value.",
      arglists = {"[co & args]"})
  public static Object resume(HaraContext context, Object[] values) {
    if (values.length == 0) {
      throw new HaraException("coroutine/resume expects a coroutine");
    }
    HaraCoroutine coroutine = requireCoroutine("coroutine/resume", values[0]);
    Object[] args = new Object[values.length - 1];
    System.arraycopy(values, 1, args, 0, args.length);
    return coroutine.resume(args);
  }
}
