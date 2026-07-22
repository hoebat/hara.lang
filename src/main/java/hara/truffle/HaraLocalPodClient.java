package hara.truffle;

import hara.pod.v1.CallRequest;
import hara.pod.v1.CallResponse;
import hara.pod.v1.CancelRequest;
import hara.pod.v1.Envelope;
import hara.pod.v1.HandshakeRequest;
import hara.pod.v1.HandshakeResponse;
import hara.pod.v1.Manifest;
import hara.pod.v1.ReleaseRequest;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/** Protobuf transport for a local pod subprocess. Calls may be in flight concurrently. */
public final class HaraLocalPodClient implements HaraPodClient {
  private final InputStream input;
  private final OutputStream output;
  private final Process process;
  private final AtomicLong requestIds = new AtomicLong();
  private final Object writeLock = new Object();
  private final Map<Long, CompletableFuture<Envelope>> pending = new ConcurrentHashMap<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Thread reader;
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
    this.reader = new Thread(this::readResponses, "hara-pod-reader");
    this.reader.setDaemon(true);
    this.reader.start();
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
    Envelope response =
        request(
            Envelope.newBuilder()
                .setRelease(
                    ReleaseRequest.newBuilder().setHandle(((HaraPodHandle) handle).handle()))
                .build());
    if (!response.hasReleaseResponse()) {
      throw new HaraPodException("Expected release response, got " + response.getBodyCase());
    }
    if (response.getReleaseResponse().hasError()) {
      throw new HaraPodException(
          response.getReleaseResponse().getError().getCode()
              + ": "
              + response.getReleaseResponse().getError().getMessage());
    }
  }

  public void cancel(long requestId) {
    Envelope response =
        request(
            Envelope.newBuilder()
                .setCancel(CancelRequest.newBuilder().setTargetRequestId(requestId))
                .build());
    if (!response.hasCancelResponse()) {
      throw new HaraPodException("Expected cancel response, got " + response.getBodyCase());
    }
    if (response.getCancelResponse().hasError()) {
      throw new HaraPodException(
          response.getCancelResponse().getError().getCode()
              + ": "
              + response.getCancelResponse().getError().getMessage());
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    HaraPodException failure = new HaraPodException("Pod client closed");
    pending.values().forEach(result -> result.completeExceptionally(failure));
    pending.clear();
    reader.interrupt();
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
    if (closed.get()) {
      throw new HaraPodException("Pod client is closed");
    }
    long requestId = requestIds.incrementAndGet();
    CompletableFuture<Envelope> result = new CompletableFuture<>();
    pending.put(requestId, result);
    try {
      Envelope numbered = request.toBuilder().setRequestId(requestId).build();
      synchronized (writeLock) {
        numbered.writeDelimitedTo(output);
        output.flush();
      }
      return result.get();
    } catch (IOException error) {
      throw new HaraPodException("Pod transport failure", error);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new HaraPodException("Interrupted waiting for pod response", error);
    } catch (java.util.concurrent.ExecutionException error) {
      Throwable cause = error.getCause();
      if (cause instanceof HaraPodException) {
        throw (HaraPodException) cause;
      }
      throw new HaraPodException("Pod request failed", cause);
    } finally {
      pending.remove(requestId);
    }
  }

  private void readResponses() {
    try {
      while (!closed.get()) {
        Envelope response = Envelope.parseDelimitedFrom(input);
        if (response == null) {
          throw new HaraPodException("Pod closed its output");
        }
        CompletableFuture<Envelope> result = pending.get(response.getRequestId());
        if (result == null) {
          throw new HaraPodException(
              "Mismatched pod response request id: " + response.getRequestId());
        }
        result.complete(response);
      }
    } catch (IOException | HaraPodException error) {
      if (!closed.get()) {
        HaraPodException failure =
            error instanceof HaraPodException
                ? (HaraPodException) error
                : new HaraPodException("Pod transport failure", error);
        pending.values().forEach(result -> result.completeExceptionally(failure));
      }
    }
  }
}
