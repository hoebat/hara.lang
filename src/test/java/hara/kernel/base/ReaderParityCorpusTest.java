package hara.kernel.base;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import hara.lang.base.G;
import hara.lang.data.Keyword;
import hara.lang.data.types.ILinearType;
import hara.lang.data.types.IMapType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Executes the canonical reader corpus shared with the Rust runtime. */
public class ReaderParityCorpusTest {
  private static final Path CORPUS = Path.of("spec/hara/reader-parity.edn");

  private static Keyword key(String name) {
    return Keyword.create(null, name);
  }

  @SuppressWarnings("rawtypes")
  @Test
  public void sharedReaderCorpusMatchesCanonicalFormsAndErrors() throws Exception {
    IMapType manifest =
        (IMapType) Parser.LispReader.readString(Files.readString(CORPUS), null);
    Object rawCases = manifest.lookup(key("cases"));
    assertTrue(rawCases instanceof ILinearType);
    ILinearType<?> cases = (ILinearType<?>) rawCases;
    assertTrue("reader corpus must be substantial", cases.count() >= 20);

    for (Object rawCase : cases) {
      IMapType testCase = (IMapType) rawCase;
      Object id = testCase.lookup(key("id"));
      String source = (String) testCase.lookup(key("source"));
      String readable = (String) testCase.lookup(key("readable"));
      String expectedError = (String) testCase.lookup(key("error"));
      assertNotNull("case must contain :id", id);
      assertNotNull(id + " must contain :source", source);
      if ((readable == null) == (expectedError == null))
        fail(id + " must contain exactly one of :readable or :error");

      if (readable != null) {
        Object value = Parser.LispReader.readString(source, null);
        String actual = G.display(value);
        assertEquals(id.toString(), readable, actual);
        assertEquals(
            id + " canonical output must round-trip",
            actual,
            G.display(Parser.LispReader.readString(actual, null)));
      } else {
        RuntimeException error =
            assertThrows(
                id.toString(),
                RuntimeException.class,
                () -> Parser.LispReader.readString(source, null));
        Throwable cause = error.getCause() == null ? error : error.getCause();
        assertTrue(
            id + ": expected <" + expectedError + "> in <" + cause.getMessage() + ">",
            cause.getMessage().contains(expectedError));
      }
    }
  }
}
