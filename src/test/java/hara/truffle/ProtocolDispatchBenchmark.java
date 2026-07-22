package hara.truffle;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * Manual protocol-dispatch benchmark.
 *
 * <p>Run this class with a GraalVM/JVMCI-enabled Java runtime after test compilation. It is kept
 * outside the JUnit naming convention because timing assertions are not stable build tests.
 */
public final class ProtocolDispatchBenchmark {
  private static final int DEFAULT_WARMUP = 5;
  private static final int DEFAULT_ITERATIONS = 100_000;

  private ProtocolDispatchBenchmark() {}

  public static void main(String[] args) {
    int warmup = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_WARMUP;
    int iterations = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_ITERATIONS;

    long monomorphic = run(MONOMORPHIC_PROGRAM, new String[] {"(Cell 1)"}, warmup, iterations);
    long polymorphic =
        run(POLYMORPHIC_PROGRAM, new String[] {"(CellA 1)", "(CellB 1)"}, warmup, iterations);

    System.out.printf(
        "monomorphic: %d calls in %.3f ms (%.1f M calls/s)%n",
        iterations,
        monomorphic / 1_000_000.0,
        iterations / (monomorphic / 1_000_000_000.0) / 1_000_000.0);
    System.out.printf(
        "polymorphic: %d calls in %.3f ms (%.1f M calls/s)%n",
        iterations,
        polymorphic / 1_000_000.0,
        iterations / (polymorphic / 1_000_000_000.0) / 1_000_000.0);
  }

  private static long run(
      String program, String[] receiverExpressions, int warmup, int iterations) {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      Value benchmark = context.eval(HaraLanguage.ID, program);
      Value[] receivers = new Value[receiverExpressions.length];
      for (int i = 0; i < receiverExpressions.length; i++) {
        receivers[i] = context.eval(HaraLanguage.ID, receiverExpressions[i]);
      }
      for (int i = 0; i < warmup; i++) {
        benchmark.execute(receivers[i % receivers.length]);
      }

      long start = System.nanoTime();
      Value result = null;
      for (int i = 0; i < iterations; i++) {
        result = benchmark.execute(receivers[i % receivers.length]);
      }
      long elapsed = System.nanoTime() - start;
      if (result == null || result.asLong() != 1) {
        throw new AssertionError("benchmark result mismatch: " + result);
      }
      return elapsed;
    }
  }

  private static final String COMMON_PROGRAM =
      "(defprotocol Value (value [self])) "
          + "(defstruct Cell [value]) "
          + "(extend-type Cell Value (value [self] (field self :value))) ";

  private static final String MONOMORPHIC_PROGRAM =
      COMMON_PROGRAM + "(fn [value] (protocol-call Value value value))";

  private static final String POLYMORPHIC_PROGRAM =
      "(defprotocol Value (value [self])) "
          + "(defstruct CellA [value]) "
          + "(defstruct CellB [value]) "
          + "(extend-type CellA Value (value [self] (field self :value))) "
          + "(extend-type CellB Value (value [self] (field self :value))) "
          + "(fn [value] (protocol-call Value value value))";
}
