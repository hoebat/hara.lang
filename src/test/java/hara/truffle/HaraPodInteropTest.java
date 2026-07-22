package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.ByteString;
import com.oracle.truffle.api.interop.InteropLibrary;
import hara.pod.v1.Function;
import hara.pod.v1.Manifest;
import hara.pod.v1.Namespace;
import org.junit.Test;

public class HaraPodInteropTest {
  @Test
  public void exposesManifestFunctionsAsExecutableMembers() throws Exception {
    TestClient client = new TestClient();
    HaraPodNamespace namespace = HaraPodNamespace.from(client, "pod.test");
    InteropLibrary interop = InteropLibrary.getUncached();

    assertTrue(interop.isMemberReadable(namespace, "echo"));
    Object function = interop.readMember(namespace, "echo");
    assertTrue(interop.isExecutable(function));
    assertEquals("hello", interop.execute(function, "hello"));
    Object members = interop.getMembers(namespace, false);
    assertTrue(interop.hasArrayElements(members));
    assertEquals("echo", interop.readArrayElement(members, 0));
  }

  @Test(expected = com.oracle.truffle.api.interop.ArityException.class)
  public void enforcesManifestArity() throws Exception {
    HaraPodFunction function = new HaraPodFunction(new TestClient(), "pod.test/echo", 1, 1, false);
    InteropLibrary.getUncached().execute(function);
  }

  private static final class TestClient implements HaraPodClient {
    private final Manifest manifest =
        Manifest.newBuilder()
            .setPodName("pod.test")
            .addNamespaces(
                Namespace.newBuilder()
                    .setName("pod.test")
                    .addFunctions(
                        Function.newBuilder()
                            .setName("echo")
                            .setMinimumArity(1)
                            .setMaximumArity(1)))
            .build();

    @Override
    public Manifest manifest() {
      return manifest;
    }

    @Override
    public Object call(String function, Object[] arguments) {
      return arguments[0] instanceof ByteString
          ? ((ByteString) arguments[0]).toStringUtf8()
          : arguments[0];
    }
  }
}
