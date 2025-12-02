package hara.kernel;

import hara.kernel.base.RT;
import hara.kernel.base.completion.ClasspathScanner;
import hara.kernel.base.completion.PackageTree;
import hara.lang.protocol.IContext;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ReplCompleterTest {

  @Test
  public void testExtractWord() {
    IContext dummyContext =
        new IContext() {
          public Object call(Object... args) {
            return null;
          }
        };
    RT.Instance rt = new RT.Instance(dummyContext, "test");
    PackageTree packageTree = new PackageTree();
    ClasspathScanner scanner = new ClasspathScanner(packageTree);
    ReplCompleter completer = new ReplCompleter(rt, scanner, packageTree);

    // Test cases
    assertEquals("ca", completer.extractWord("(ca", 3));
    assertEquals("hara.", completer.extractWord("(hara.", 6));
    assertEquals("java.lang.", completer.extractWord("java.lang.", 10));
    assertEquals("concat", completer.extractWord("(concat", 7));
    assertEquals("foo", completer.extractWord("foo", 3));
    assertEquals("bar", completer.extractWord("(foo bar", 8));
  }

  @Test
  public void testComplete() {
    // Setup RT with some symbols
    IContext dummyContext =
        new IContext() {
          public Object call(Object... args) {
            return null;
          }
        };
    RT.Instance rt = new RT.Instance(dummyContext, "test");

    PackageTree packageTree = new PackageTree();
    packageTree.add("java.lang.String");
    packageTree.add("hara.test.Foo");

    ClasspathScanner scanner = new ClasspathScanner(packageTree);
    ReplCompleter completer = new ReplCompleter(rt, scanner, packageTree);

    // Mock LineReader (not used by completer logic much, just passed)
    LineReader reader = null;

    // Test 1: Symbol completion "con" -> "concat"
    ParsedLine line1 =
        new ParsedLine() {
          public String word() {
            return "con";
          }

          public int wordCursor() {
            return 3;
          }

          public int wordIndex() {
            return 0;
          }

          public List<String> words() {
            return java.util.Collections.singletonList("con");
          }

          public String line() {
            return "(con";
          }

          public int cursor() {
            return 4;
          } // after "con"
        };
    List<Candidate> candidates1 = new ArrayList<>();
    completer.complete(reader, line1, candidates1);

    // Check if "concat" is in candidates
    boolean foundConcat = false;
    for (Candidate c : candidates1) {
      if (c.value().equals("concat")) {
        foundConcat = true;
        break;
      }
    }
    assertTrue("Should find 'concat'", foundConcat);

    // Test 2: Package completion "java.lan" -> "java.lang."
    ParsedLine line2 =
        new ParsedLine() {
          public String word() {
            return "java.lan";
          }

          public int wordCursor() {
            return 8;
          }

          public int wordIndex() {
            return 0;
          }

          public List<String> words() {
            return java.util.Collections.singletonList("java.lan");
          }

          public String line() {
            return "java.lan";
          }

          public int cursor() {
            return 8;
          }
        };
    List<Candidate> candidates2 = new ArrayList<>();
    completer.complete(reader, line2, candidates2);

    boolean foundJavaLang = false;
    for (Candidate c : candidates2) {
      if (c.value().equals("java.lang.")) {
        foundJavaLang = true;
        break;
      }
    }
    assertTrue("Should find 'java.lang.'", foundJavaLang);

    // Test 3: Class completion "java.lang.Str" -> "java.lang.String"
    ParsedLine line3 =
        new ParsedLine() {
          public String word() {
            return "java.lang.Str";
          }

          public int wordCursor() {
            return 13;
          }

          public int wordIndex() {
            return 0;
          }

          public List<String> words() {
            return java.util.Collections.singletonList("java.lang.Str");
          }

          public String line() {
            return "java.lang.Str";
          }

          public int cursor() {
            return 13;
          }
        };
    List<Candidate> candidates3 = new ArrayList<>();
    completer.complete(reader, line3, candidates3);

    boolean foundString = false;
    for (Candidate c : candidates3) {
      if (c.value().equals("java.lang.String")) {
        foundString = true;
        break;
      }
    }
    assertTrue("Should find 'java.lang.String'", foundString);
  }

  @Test
  public void testClasspathScanner() throws Exception {
    PackageTree packageTree = new PackageTree();
    ClasspathScanner scanner = new ClasspathScanner(packageTree);

    // Scan system classpath (synchronously)
    scanner.scan();

    // Check if hara.kernel.Main is found
    List<String> suggestions = packageTree.suggest("hara.kernel.Mai");
    boolean foundMain = false;
    for (String s : suggestions) {
      if (s.equals("hara.kernel.Main")) {
        foundMain = true;
        break;
      }
    }
    assertTrue("Should find 'hara.kernel.Main'", foundMain);
  }

  // Helper class to expose extractWord
  static class TestableReplCompleter extends ReplCompleter {
    public TestableReplCompleter(
        RT.Instance<?> rt, ClasspathScanner scanner, PackageTree packageTree) {
      super(rt, scanner, packageTree);
    }

    // We need to change visibility of extractWord in ReplCompleter.java first to be
    // protected or package-private.
    // For now, let's assume we will modify ReplCompleter.java to make it
    // package-private.
  }
}
