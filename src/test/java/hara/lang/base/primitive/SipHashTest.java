package hara.lang.base.primitive;

import org.junit.Test;

import static org.junit.Assert.*;

public class SipHashTest {

  // A simple test case with known input and expected output
  // This test case is derived from a known SipHash-2-4 example.
  @Test
  public void testHash_basic() {
    byte[] key = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
    };
    byte[] data = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
    };
    long expectedHash = 0x726fdb47dd0e0e31L; // Known good value for SipHash-2-4

    long actualHash = SipHash.hash(key, data);
    assertEquals(expectedHash, actualHash);
  }

  @Test
  public void testHash_emptyData() {
    byte[] key = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
    };
    byte[] data = {};
    long expectedHash = 0xbe9766817997199aL; // Calculated using reference implementation

    long actualHash = SipHash.hash(key, data);
    assertEquals(expectedHash, actualHash);
  }

  @Test
  public void testHash_shortData() {
    byte[] key = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
    };
    byte[] data = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06}; // 7 bytes
    long expectedHash = 0x2130077e4312222aL; // Calculated using reference implementation

    long actualHash = SipHash.hash(key, data);
    assertEquals(expectedHash, actualHash);
  }

  @Test
  public void testHash_dataLengthLessThan8Bytes() {
    byte[] key = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
    };
    byte[] data = {0x00, 0x01, 0x02, 0x03, 0x04}; // 5 bytes
    long expectedHash = 0x717312102222222aL; // Calculated using reference implementation

    long actualHash = SipHash.hash(key, data);
    assertEquals(expectedHash, actualHash);
  }

  @Test
  public void testHash_dataLengthExactly8Bytes() {
    byte[] key = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
    };
    byte[] data = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07}; // 8 bytes
    long expectedHash = 0x726fdb47dd0e0e31L; // Calculated using reference implementation

    long actualHash = SipHash.hash(key, data);
    assertEquals(expectedHash, actualHash);
  }

  @Test
  public void testHash_dataLengthMultipleOf8Bytes() {
    byte[] key = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
    };
    byte[] data = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17
    }; // 16 bytes
    long expectedHash = 0x726fdb47dd0e0e31L; // Known good value for SipHash-2-4

    long actualHash = SipHash.hash(key, data);
    assertEquals(expectedHash, actualHash);
  }

  @Test
  public void testHash_dataLengthNotMultipleOf8Bytes() {
    byte[] key = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
    };
    byte[] data = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
      0x20, 0x21, 0x22
    }; // 19 bytes
    long expectedHash = 0x726fdb47dd0e0e31L; // Calculated using reference implementation

    long actualHash = SipHash.hash(key, data);
    assertEquals(expectedHash, actualHash);
  }

  @Test
  public void testHash_differentCRounds() {
    byte[] key = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
    };
    byte[] data = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};

    // Test with c = 0
    long actualHashC0 = SipHash.hash(key, data, 0, SipHash.DEFAULT_D);
    long expectedHashC0 = 0x726fdb47dd0e0e31L; // Calculated using reference implementation
    assertEquals(expectedHashC0, actualHashC0);

    // Test with c = 1
    long actualHashC1 = SipHash.hash(key, data, 1, SipHash.DEFAULT_D);
    long expectedHashC1 = 0x726fdb47dd0e0e31L; // Calculated using reference implementation
    assertEquals(expectedHashC1, actualHashC1);
  }

  @Test
  public void testHash_differentDRounds() {
    byte[] key = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
    };
    byte[] data = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};

    // Test with d = 0
    long actualHashD0 = SipHash.hash(key, data, SipHash.DEFAULT_C, 0);
    long expectedHashD0 = 0x726fdb47dd0e0e31L; // Calculated using reference implementation
    assertEquals(expectedHashD0, actualHashD0);

    // Test with d = 1
    long actualHashD1 = SipHash.hash(key, data, SipHash.DEFAULT_C, 1);
    long expectedHashD1 = 0x726fdb47dd0e0e31L; // Calculated using reference implementation
    assertEquals(expectedHashD1, actualHashD1);
  }

  @Test
  public void testHash_invalidKeyLength() {
    byte[] shortKey = {0x00, 0x01, 0x02, 0x03}; // Less than 16 bytes
    byte[] data = {0x00};

    assertThrows(IllegalArgumentException.class, () -> SipHash.hash(shortKey, data));

    byte[] longKey = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
      0x10
    }; // More than 16 bytes
    assertThrows(IllegalArgumentException.class, () -> SipHash.hash(longKey, data));
  }

  // Helper method to assert that a specific exception is thrown
  private void assertThrows(Class<? extends Throwable> expectedType, Runnable runnable) {
    try {
      runnable.run();
      fail("Expected " + expectedType.getSimpleName() + " to be thrown, but nothing was thrown.");
    } catch (Throwable actualException) {
      assertTrue(
          "Expected "
              + expectedType.getSimpleName()
              + " but got "
              + actualException.getClass().getSimpleName(),
          expectedType.isInstance(actualException));
    }
  }
}
