package hara.truffle;

import hara.pod.v1.CallRequest;
import hara.pod.v1.CallResponse;
import hara.pod.v1.CancelRequest;
import hara.pod.v1.Envelope;
import hara.pod.v1.HandshakeRequest;
import hara.pod.v1.HandshakeResponse;
import hara.pod.v1.Manifest;
import hara.pod.v1.ReleaseRequest;
import hara.pod.v1.Value;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Synchronous protobuf transport for a local pod subprocess. */
public final class HaraLocalPodClient implements HaraPodClient {
  private final InputStream input;
  private final OutputStream output;
  private final Process process;
  private final AtomicLong requestIds = new AtomicLong();
  private final Object lock = new Object();
  private final Manifest manifest;

  public static HaraLocalPodClient start(List<String> command, String clientName) {
    try {
      Process process =
          new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
      return new HaraLocalPodClient(
          process, process.getInputStream(), process.getOutputStream(), clientName);
    } catch (IOException error) {
      throw new HaraPodException("Unable to start pod: " + command, error);
    }
  }

  HaraLocalPodClient(Process process, InputStream input, OutputStream output, String clientName) {
    this.process = process;
    this.input = new BufferedInputStream(input);
    this.output = new BufferedOutputStream(output);
    this.manifest = handshake(clientName);
  }

  @Override
  public Manifest manifest() {
    return manifest;
  }

  @Override
  public Object call(String function, Object[] arguments) {
    CallRequest.Builder call = CallRequest.newBuilder().setFunction(function);
    for (Object argument : arguments) {
      call.addArguments(HaraPodValueCodec.encode(argument));
    }
    Envelope response = request(Envelope.newBuilder().setCall(call).build());
    if (!response.hasCallResponse()) {
      throw new HaraPodException("Expected call response, got " + response.getBodyCase());
    }
    CallResponse result = response.getCallResponse();
    if (result.hasError()) {
      throw new HaraPodException(
          result.getError().getCode() + ": " + result.getError().getMessage());
    }
    if (!result.hasValue()) {
      throw new HaraPodException("Asynchronous pod results are not supported by this client");
    }
    return HaraPodValueCodec.decode(result.getValue());
  }

  @Override
  public void release(Object handle) {
    if (!(handle instanceof HaraPodHandle)) {
      throw new IllegalArgumentException("Not a pod handle: " + handle);
    }
    request(
        Envelope.newBuilder()
            .setRelease(ReleaseRequest.newBuilder().setHandle(((HaraPodHandle) handle).handle()))
            .build());
  }

  public void cancel(long requestId) {
    request(
        Envelope.newBuilder()
            .setCancel(CancelRequest.newBuilder().setTargetRequestId(requestId))
            .build());
  }

  @Override
  public void close() {
    try {
      input.close();
      output.close();
      if (process != null) {
        process.destroy();
      }
    } catch (IOException error) {
      throw new HaraPodException("Unable to close pod", error);
    }
  }

  private Manifest handshake(String clientName) {
    Envelope response =
        request(
            Envelope.newBuilder()
                .setHandshake(
                    HandshakeRequest.newBuilder()
                        .setAbiMajor(1)
                        .setAbiMinor(0)
                        .setClientName(clientName))
                .build());
    if (!response.hasHandshakeResponse()) {
      throw new HaraPodException("Expected handshake response, got " + response.getBodyCase());
    }
    HandshakeResponse handshake = response.getHandshakeResponse();
    if (handshake.hasError()) {
      throw new HaraPodException(
          handshake.getError().getCode() + ": " + handshake.getError().getMessage());
    }
    if (!handshake.hasManifest()) {
      throw new HaraPodException("Pod handshake did not return a manifest");
    }
    return handshake.getManifest();
  }

  private Envelope request(Envelope request) {
    synchronized (lock) {
      long requestId = requestIds.incrementAndGet();
      Envelope numbered = request.toBuilder().setRequestId(requestId).build();
      try {
        numbered.writeDelimitedTo(output);
        output.flush();
        Envelope response = Envelope.parseDelimitedFrom(input);
        if (response == null || response.getRequestId() != requestId) {
          throw new HaraPodException("Mismatched pod response request id");
        }
        return response;
      } catch (IOException error) {
        throw new HaraPodException("Pod transport failure", error);
      }
    }
  }
}
