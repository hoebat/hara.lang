package hara.lang.base;

import hara.lang.base.primitive.Bits;

import hara.lang.data.types.ObjMutable;

import hara.lang.data.types.ILinearType;
import hara.lang.data.types.IMapType;
import hara.lang.data.types.ISequentialType;
import hara.lang.data.types.ISetType;
import hara.lang.data.Tuple;
import hara.lang.protocol.*;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public interface Ut {

  @SuppressWarnings("unchecked")
  public class Clock {

    private static final Clock _clock;

    static {
      _clock = new Clock();
    }

    public static final long currentTimeMicros() {
      return currentTimeNanos() / 1000;
    }

    public static final long currentTimeMillis() {
      return currentTimeNanos() / 1000000;
    }

    public static final long currentTimeNanos() {
      return _clock._tsys + (System.nanoTime() - _clock._toff);
    }

    private final long _toff;

    private final long _tsys;

    private Clock() {
      _tsys = System.currentTimeMillis() * 1000000;

      // typically 36 ns, between these two lines.
      _toff = System.nanoTime();
    }
  }

  public class Counter implements IDeref<Integer>, IReset<Integer>, IDisplay {

    private int _c;

    public Counter(int count) {
      _c = count;
    }

    public int dec() {
      return _c -= 1;
    }

    public int dec(int n) {
      return _c -= n;
    }

    @Override
    public Integer deref() {
      return _c;
    }

    @Override
    public String display() {
      return "#counter <" + _c + ">";
    }

    public int inc() {
      return _c += 1;
    }

    public int inc(int n) {
      return _c += n;
    }

    @Override
    public Integer reset(Integer count) {
      return _c = count;
    }
  }

  public class Delay<V> implements IDeref<V>, IRealize<V>, IDisplay {
    volatile Throwable _ex;
    volatile Supplier<V> _fn;
    volatile V _val;

    public Delay(Supplier<V> fn) {
      _fn = fn;
      _val = null;
      _ex = null;
    }

    @Override
    public V deref() {
      if (_fn != null) {
        synchronized (this) {
          // double check
          if (_fn != null) {
            try {
              _val = _fn.get();
            } catch (Throwable t) {
              _ex = t;
            }
            _fn = null;
          }
        }
      }
      if (_ex != null) throw Ex.Sneaky(_ex);
      return _val;
    }

    @Override
    public String display() {
      return isRealized() ? "#delay <" + G.display(_val) + ">" : "#delay.pending<>";
    }

    @Override
    public synchronized boolean isRealized() {
      return _fn == null;
    }

    @Override
    public V realize() {
      return deref();
    }
  }

  public class Flag implements IDeref<Boolean>, IReset<Boolean>, IDisplay {
    private boolean _val;

    public Flag(boolean val) {
      _val = val;
    }

    @Override
    public Boolean deref() {
      return _val;
    }

    @Override
    public String display() {
      return "#flag <" + _val + ">";
    }

    @Override
    public Boolean reset(Boolean val) {
      return _val = val;
    }
  }

  public interface Murmur3 {

    public static final int C1 = 0xcc9e2d51;

    public static final int C2 = 0x1b873593;

    public static final int seed = 0;

    public static int hashChars(CharSequence input) {
      int h1 = seed;

      // step through the CharSequence 2 chars at a time
      for (int i = 1; i < input.length(); i += 2) {
        int k1 = input.charAt(i - 1) | (input.charAt(i) << 16);
        k1 = mixK1(k1);
        h1 = mixH1(h1, k1);
      }

      // deal with any remaining characters
      if ((input.length() & 1) == 1) {
        int k1 = input.charAt(input.length() - 1);
        k1 = mixK1(k1);
        h1 ^= k1;
      }

      return mixFull(h1, 2 * input.length());
    }

    public static int hashInt(int input) {
      if (input == 0) return 0;
      int k1 = mixK1(input);
      int h1 = mixH1(seed, k1);

      return mixFull(h1, 4);
    }

    public static int hashLong(long input) {
      if (input == 0) return 0;
      int low = (int) input;
      int high = (int) (input >>> 32);

      int k1 = mixK1(low);
      int h1 = mixH1(seed, k1);

      k1 = mixK1(high);
      h1 = mixH1(h1, k1);

      return mixFull(h1, 8);
    }

    // Finalization mix - force all bits of a hash block to avalanche
    public static int mixFull(int h1, int length) {
      h1 ^= length;
      h1 ^= h1 >>> 16;
      h1 *= 0x85ebca6b;
      h1 ^= h1 >>> 13;
      h1 *= 0xc2b2ae35;
      h1 ^= h1 >>> 16;
      return h1;
    }

    public static int mixH1(int h1, int k1) {
      h1 ^= k1;
      h1 = Integer.rotateLeft(h1, 13);
      h1 = h1 * 5 + 0xe6546b64;
      return h1;
    }

    public static int mixHash(int hash, int count) {
      int h1 = seed;
      int k1 = mixK1(hash);
      h1 = mixH1(h1, k1);
      return mixFull(h1, count);
    }

    public static int mixK1(int k1) {
      k1 *= C1;
      k1 = Integer.rotateLeft(k1, 15);
      k1 *= C2;
      return k1;
    }
  }

  public final class RefCache<K, V> implements ILookup<K, Reference<V>>, ICount {

    final ConcurrentHashMap<K, Reference<V>> _lu;
    final ReferenceQueue<V> _rq;

    public RefCache() {
      _lu = new ConcurrentHashMap<K, Reference<V>>();
      _rq = new ReferenceQueue<V>();
    }

    public void clearCache() {
      if (_rq.poll() != null) {
        while (_rq.poll() != null) {}

        var it = _lu.entrySet().iterator();
        Iter.filter(it, (e) -> (e.getValue() == null) || (e.getValue().get() == null))
            .forEachRemaining((e) -> _lu.remove(e.getKey()));
      }
    }

    @Override
    public long count() {
      return _lu.size();
    }

    public void deregister(K key) {
      _lu.remove(key);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Entry<K, Reference<V>> find(K key) {
      var ret = _lu.getOrDefault(key, null);
      return (ret == null) ? null : new Tuple.Tup2.L(null, key, ret);
    }

    public V get(K key) {
      var ref = _lu.get(key);
      return (ref != null) ? ref.get() : null;
    }

    public ConcurrentHashMap<K, Reference<V>> getLookup() {
      return _lu;
    }

    public V getOrCreate(K key, Supplier<Reference<V>> f) {
      var ref = _lu.get(key);
      if (ref != null) {
        var v = ref.get();
        if (v != null) {
          return v;
        }
      }

      ref = f.get();
      _lu.put(key, ref);
      return ref.get();
    }

    public ReferenceQueue<V> getQueue() {
      return _rq;
    }

    @Override
    public Iterator<K> keys() {
      return _lu.keys().asIterator();
    }

    @Override
    public Reference<V> lookup(K key) {
      return _lu.get(key);
    }

    public void register(K key, V obj) {
      _lu.put(key, new WeakReference<V>(obj, _rq));
    }

    @Override
    public Iterator<Reference<V>> vals() {
      return _lu.values().iterator();
    }
  }

  public interface SipHash {

    /** Default value for the C rounds of compression. */
    public static final int DEFAULT_C = 2;

    /** Default value for the D rounds of compression. */
    public static final int DEFAULT_D = 4;

    public static final byte[] HARA = {
      55, 89, -112, -23, 121, 98, -37, 61, 24, 85, 109, -62, 47, -15, 32, 17
    };

    /** Initial value for the v0 magic number. */
    public static final long INITIAL_V0 = 0x736f6d6570736575L;

    /** Initial value for the v1 magic number. */
    public static final long INITIAL_V1 = 0x646f72616e646f6dL;

    /** Initial value for the v2 magic number. */
    public static final long INITIAL_V2 = 0x6c7967656e657261L;

    /** Initial value for the v3 magic number. */
    public static final long INITIAL_V3 = 0x7465646279746573L;

    /**
     * Converts a chunk of 8 bytes to a number in little endian.
     *
     * <p>Accepts an offset to determine where the chunk begins.
     *
     * @param bytes the byte array containing our bytes to convert.
     * @param offset the index to start at when chunking bytes.
     * @return a long representation, in little endian.
     */
    public static long bytesToLong(byte[] bytes, int offset) {
      long m = 0;
      for (int i = 0; i < 8; i++) {
        m |= ((((long) bytes[i + offset]) & 0xff) << (8 * i));
      }
      return m;
    }

    /**
     * Hashes a data input for a given key.
     *
     * <p>This will used the default values for C and D rounds.
     *
     * @param key the key to seed the hash with.
     * @param data the input data to hash.
     * @return a long value as the output of the hash.
     */
    public static long hash(byte[] key, byte[] data) {
      return hash(key, data, DEFAULT_C, DEFAULT_D);
    }

    /**
     * Hashes a data input for a given key, using the provided rounds of compression.
     *
     * @param key the key to seed the hash with.
     * @param data the input data to hash.
     * @param c the number of C rounds of compression
     * @param d the number of D rounds of compression.
     * @return a long value as the output of the hash.
     */
    public static long hash(byte[] key, byte[] data, int c, int d) {
      if (key.length != 16) {
        throw new IllegalArgumentException("Key must be exactly 16 bytes!");
      }

      long k0 = bytesToLong(key, 0);
      long k1 = bytesToLong(key, 8);

      return hash(c, d, INITIAL_V0 ^ k0, INITIAL_V1 ^ k1, INITIAL_V2 ^ k0, INITIAL_V3 ^ k1, data);
    }

    /**
     * Internal 0A hashing implementation.
     *
     * <p>Requires initial state being manually provided (to avoid allocation). The compression
     * rounds must also be provided, as nothing will be validated in this layer (such as defaults).
     *
     * @param c the rounds of C compression to apply.
     * @param d the rounds of D compression to apply.
     * @param v0 the seeded initial value of v0.
     * @param v1 the seeded initial value of v1.
     * @param v2 the seeded initial value of v2.
     * @param v3 the seeded initial value of v3.
     * @param data the input data to hash using the SipHash algorithm.
     * @return a long value as the output of the hash.
     */
    public static long hash(int c, int d, long v0, long v1, long v2, long v3, byte[] data) {
      long m;
      int last = data.length / 8 * 8;
      int i = 0;
      int r;

      while (i < last) {
        m = data[i++] & 0xffL;
        for (r = 1; r < 8; r++) {
          m |= (data[i++] & 0xffL) << (r * 8);
        }

        v3 ^= m;
        for (r = 0; r < c; r++) {
          v0 += v1;
          v2 += v3;
          v1 = rotateLeft(v1, 13);
          v3 = rotateLeft(v3, 16);

          v1 ^= v0;
          v3 ^= v2;
          v0 = rotateLeft(v0, 32);

          v2 += v1;
          v0 += v3;
          v1 = rotateLeft(v1, 17);
          v3 = rotateLeft(v3, 21);

          v1 ^= v2;
          v3 ^= v0;
          v2 = rotateLeft(v2, 32);
        }
        v0 ^= m;
      }

      m = 0;
      for (i = data.length - 1; i >= last; --i) {
        m <<= 8;
        m |= (data[i] & 0xffL);
      }
      m |= (long) data.length << 56;

      v3 ^= m;
      for (r = 0; r < c; r++) {
        v0 += v1;
        v2 += v3;
        v1 = rotateLeft(v1, 13);
        v3 = rotateLeft(v3, 16);

        v1 ^= v0;
        v3 ^= v2;
        v0 = rotateLeft(v0, 32);

        v2 += v1;
        v0 += v3;
        v1 = rotateLeft(v1, 17);
        v3 = rotateLeft(v3, 21);

        v1 ^= v2;
        v3 ^= v0;
        v2 = rotateLeft(v2, 32);
      }
      v0 ^= m;

      v2 ^= 0xff;
      for (r = 0; r < d; r++) {
        v0 += v1;
        v2 += v3;
        v1 = rotateLeft(v1, 13);
        v3 = rotateLeft(v3, 16);

        v1 ^= v0;
        v3 ^= v2;
        v0 = rotateLeft(v0, 32);

        v2 += v1;
        v0 += v3;
        v1 = rotateLeft(v1, 17);
        v3 = rotateLeft(v3, 21);

        v1 ^= v2;
        v3 ^= v0;
        v2 = rotateLeft(v2, 32);
      }

      return v0 ^ v1 ^ v2 ^ v3;
    }

    /**
     * Rotates an input number `val` left by `shift` number of bits.
     *
     * <p>Bits which are pushed off to the left are rotated back onto the right, making this a left
     * rotation (a circular shift).
     *
     * <p>This is very close to {@link Long#rotateLeft(long, int)} aside from the use of the 64 bit
     * masking.
     *
     * @param value the value to be shifted.
     * @param shift how far left to shift.
     * @return a long value after being shifted.
     */
    public static long rotateLeft(long value, int shift) {
      return (value << shift) | value >>> (64 - shift);
    }

    /**
     * Converts a hash to a hexidecimal representation.
     *
     * @param hash the finalized hash value to convert to hex.
     * @return a {@link String} representation of the hash.
     */
    public static String toHexString(long hash) {
      String hex = Long.toHexString(hash);

      if (hex.length() == 16) {
        return hex;
      }

      StringBuilder sb = new StringBuilder();
      for (int i = 0, j = 16 - hex.length(); i < j; i++) {
        sb.append('0');
      }

      return sb.append(hex).toString();
    }
  }

  public final class Volatile<V> implements IDeref<V>, IReset<V>, IDisplay {

    public volatile V _val;

    public Volatile(V val) {
      _val = val;
    }

    @Override
    public V deref() {
      return _val;
    }

    @Override
    public String display() {
      return "#vol <" + _val + ">";
    }

    @Override
    public V reset(V newval) {
      return _val = newval;
    }
  }
}
