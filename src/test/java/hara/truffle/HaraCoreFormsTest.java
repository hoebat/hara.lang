package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;

public class HaraCoreFormsTest {
  @Test
  public void varReturnsTheNamespaceVarAndDerefReadsItsCurrentRoot() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(HaraLanguage.ID, "(def answer 41) (def answer 42) (deref (var answer))")
              .asLong());
      assertEquals(42, context.eval(HaraLanguage.ID, "@(var answer)").asLong());
      assertTrue(context.eval(HaraLanguage.ID, "(var answer)").toString().contains("user/answer"));
    }
  }

  @Test
  public void varSupportsQualifiedLookupsWithoutChangingTheCurrentNamespace() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(ns alpha) (def answer 42) (ns user) (deref (var alpha/answer))")
              .asLong());
    }
  }

  @Test
  public void derefUsesTheLanguageProtocol() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(defstruct Box [value]) "
                      + "(extend-type Box IDeref (deref [self] (field self :value))) "
                      + "@(Box 42)")
              .asLong());
    }
  }

  @Test
  public void varAndDerefFailuresAreDeterministic() {
    try (Context context = context()) {
      assertTrue(
          assertThrows(
                  PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(var missing)"))
              .getMessage()
              .contains("Unbound var: missing"));
      assertTrue(
          assertThrows(PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(var 1)"))
              .getMessage()
              .contains("var expects a symbol"));
      assertTrue(
          assertThrows(PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(deref 1)"))
              .getMessage()
              .contains("No IDeref/deref implementation"));
    }
  }

  private static Context context() {
    return Context.newBuilder(HaraLanguage.ID).build();
  }
}
