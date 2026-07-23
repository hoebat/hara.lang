package hara.lang.test;

import hara.truffle.HaraFunction;
import java.util.ArrayList;
import java.util.List;

public final class HaraTestRegistry {
  private final List<HaraTestCase> tests = new ArrayList<>();

  public synchronized HaraTestCase register(
      String namespace, String name, Object metadata, HaraFunction body) {
    HaraTestCase test = new HaraTestCase(namespace, name, metadata, body);
    tests.add(test);
    return test;
  }

  public synchronized List<HaraTestCase> tests() {
    return List.copyOf(tests);
  }

  public synchronized void clear() {
    tests.clear();
  }

  public HaraTestResult run(HaraTestCase test) {
    long start = System.nanoTime();
    try {
      Object value = test.body().callTarget().call(test.body().callArguments(new Object[0]));
      return new HaraTestResult(test, HaraTestResult.Status.PASS, value, null, elapsed(start));
    } catch (Throwable error) {
      return new HaraTestResult(test, HaraTestResult.Status.FAIL, null, error, elapsed(start));
    }
  }

  public List<HaraTestResult> runAll() {
    List<HaraTestResult> result = new ArrayList<>();
    for (HaraTestCase test : tests()) result.add(run(test));
    return result;
  }

  private static long elapsed(long start) {
    return (System.nanoTime() - start) / 1_000_000L;
  }
}
