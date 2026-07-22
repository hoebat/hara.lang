package hara.truffle;

import static org.junit.Assert.assertEquals;

import hara.pod.v1.CallResponse;
import hara.pod.v1.Envelope;
import hara.pod.v1.Function;
import hara.pod.v1.HandshakeResponse;
import hara.pod.v1.Manifest;
import hara.pod.v1.Namespace;
import hara.pod.v1.Value;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class HaraLocalPodClientTest {
  @Test
  public void performsHandshakeAndDelimitedCall() throws Exception {
    PipedInputStream clientInput = new PipedInputStream();
    PipedOutputStream serverOutput = new PipedOutputStream(clientInput);
    PipedInputStream serverInput = new PipedInputStream();
    PipedOutputStream clientOutput = new PipedOutputStream(serverInput);
    AtomicReference<Throwable> serverFailure = new AtomicReference<>();
    Thread server =
        new Thread(
            () -> {
              try {
                Envelope handshake = Envelope.parseDelimitedFrom(serverInput);
                Envelope.newBuilder()
                    .setRequestId(handshake.getRequestId())
                    .setHandshakeResponse(
                        HandshakeResponse.newBuilder()
                            .setManifest(
                                Manifest.newBuilder()
                                    .setAbiMajor(1)
                                    .setPodName("pod.test")
                                    .addNamespaces(
                                        Namespace.newBuilder()
                                            .setName("pod.test")
                                            .addFunctions(
                                                Function.newBuilder()
                                                    .setName("echo")
                                                    .setMinimumArity(1)
                                                    .setMaximumArity(1)))))
                    .build()
                    .writeDelimitedTo(serverOutput);
                serverOutput.flush();
                Envelope call = Envelope.parseDelimitedFrom(serverInput);
                Envelope.newBuilder()
                    .setRequestId(call.getRequestId())
                    .setCallResponse(
                        CallResponse.newBuilder()
                            .setValue(
                                Value.newBuilder().setStringValue(call.getCall().getFunction())))
                    .build()
                    .writeDelimitedTo(serverOutput);
                serverOutput.flush();
              } catch (Throwable error) {
                serverFailure.set(error);
              }
            });
    server.start();

    HaraLocalPodClient client = new HaraLocalPodClient(null, clientInput, clientOutput, "test");
    assertEquals("pod.test", client.manifest().getPodName());
    assertEquals("pod.test/echo", client.call("pod.test/echo", new Object[] {"value"}));
    client.close();
    server.join(1000);
    if (serverFailure.get() != null) {
      throw new AssertionError(serverFailure.get());
    }
  }
}
