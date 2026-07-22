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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
                Envelope secondCall = Envelope.parseDelimitedFrom(serverInput);
                Envelope.newBuilder()
                    .setRequestId(secondCall.getRequestId())
                    .setCallResponse(
                        CallResponse.newBuilder()
                            .setValue(
                                Value.newBuilder()
                                    .setStringValue(secondCall.getCall().getFunction())))
                    .build()
                    .writeDelimitedTo(serverOutput);
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
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<Object> first = executor.submit(() -> client.call("pod.test/first", new Object[0]));
    Future<Object> second = executor.submit(() -> client.call("pod.test/second", new Object[0]));
    assertEquals("pod.test/first", first.get());
    assertEquals("pod.test/second", second.get());
    executor.shutdownNow();
    client.close();
    server.join(1000);
    if (serverFailure.get() != null) {
      throw new AssertionError(serverFailure.get());
    }
  }
}
