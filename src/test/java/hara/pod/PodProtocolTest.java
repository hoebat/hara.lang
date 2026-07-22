package hara.pod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.protobuf.ByteString;
import hara.pod.v1.CallRequest;
import hara.pod.v1.Envelope;
import hara.pod.v1.Handle;
import hara.pod.v1.Manifest;
import hara.pod.v1.Namespace;
import hara.pod.v1.Value;
import org.junit.Test;

public class PodProtocolTest {
  @Test
  public void roundTripsManifestAndTypedCallArguments() throws Exception {
    Manifest manifest =
        Manifest.newBuilder()
            .setAbiMajor(1)
            .setAbiMinor(0)
            .setPodName("hara.test")
            .addCapabilities("tensor")
            .addNamespaces(
                Namespace.newBuilder()
                    .setName("pod.hara.test")
                    .addFunctions(
                        hara.pod.v1.Function.newBuilder()
                            .setName("echo")
                            .setMinimumArity(1)
                            .setMaximumArity(1)))
            .build();

    CallRequest call =
        CallRequest.newBuilder()
            .setFunction("pod.hara.test/echo")
            .addArguments(Value.newBuilder().setStringValue("hello"))
            .addArguments(Value.newBuilder().setBytesValue(ByteString.copyFromUtf8("payload")))
            .addArguments(
                Value.newBuilder()
                    .setHandle(
                        Handle.newBuilder().setId(42).setType("tensor").setOwner("hara.test")))
            .build();

    Envelope envelope = Envelope.newBuilder().setRequestId(7).setCall(call).build();
    Envelope decoded = Envelope.parseFrom(envelope.toByteArray());

    assertEquals(7, decoded.getRequestId());
    assertTrue(decoded.hasCall());
    assertEquals("pod.hara.test/echo", decoded.getCall().getFunction());
    assertEquals("hello", decoded.getCall().getArguments(0).getStringValue());
    assertEquals("payload", decoded.getCall().getArguments(1).getBytesValue().toStringUtf8());
    assertEquals(42, decoded.getCall().getArguments(2).getHandle().getId());
  }
}
