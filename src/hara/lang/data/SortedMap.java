package hara.lang.data;

import java.util.Map;
import java.util.Map.Entry;

import java.util.Comparator;
import java.util.Iterator;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;

import hara.lang.base.*;
import static hara.lang.data.SortedMap.Color.*;

public interface SortedMap<K, V> extends
	Coll.MapType<K, V>,
	I.Nth<Map.Entry<K, V>>, 
	I.IndexedKV<K, V> {


	public enum Color {
		RED, BLACK, DOUBLE_BLACK
	}

	public final class Node<K, V> implements Map.Entry<K, V> {

		@SuppressWarnings("rawtypes")
		public static final Node EMPTY_NODE = new Node<>(BLACK, null, null, null, null);

		@SuppressWarnings("rawtypes")
		public static final Node DOUBLE_EMPTY_NODE = new Node<>(DOUBLE_BLACK, null, null, null, null);

		public final Color c;
		public final K k;
		public final V v;
		public final Node<K, V> l, r;
		public final long size;

		public Node(Color c, Node<K, V> l, K k, V v, Node<K, V> r) {
			this.c = c;
			this.k = k;
			this.v = v;
			this.l = l;
			this.r = r;

			this.size = l == null ? 0 : l.size + r.size + 1;
		}

		public Node<K, V> redden() {
			return c == BLACK && size > 0 && l.c == BLACK && r.c == BLACK ? Fn.node(RED, l, k, v, r) : this;
		}

		public Node<K, V> blacken() {
			return c == RED ? Fn.node(BLACK, l, k, v, r) : this;
		}

		public Node<K, V> unblacken() {
			return c == DOUBLE_BLACK ? Fn.node(BLACK, l, k, v, r) : this;
		}

		public Node<K, V> remove(K key, Comparator<K> comparator) {
			return redden()._remove(key, comparator);
		}

		@SuppressWarnings("unchecked")
		private Node<K, V> _remove(K key, Comparator<K> comparator) {
			if (size == 0) {
				return this;
			} else {
				int cmp = comparator.compare(key, k);
				if (cmp < 0) {
					return Fn.node(c, l._remove(key, comparator), k, v, r).rotate();
				} else if (cmp > 0) {
					return Fn.node(c, l, k, v, r._remove(key, comparator)).rotate();
				} else if (size == 1) {
					return c == BLACK ? DOUBLE_EMPTY_NODE : EMPTY_NODE;
				} else if (r.size == 0) {
					return l.blacken();
				} else {
					Node<K, V> min = Fn.min(r);
					return Fn.node(c, l, min.k, min.v, r.removeMin()).rotate();
				}
			}
		}

		public Node<K, V> put(K key, V value, BinaryOperator<V> merge, Comparator<K> comparator) {
			return _put(key, value, merge, comparator).blacken();
		}

		@SuppressWarnings("unchecked")
		private Node<K, V> _put(K key, V value, BinaryOperator<V> merge, Comparator<K> comparator) {
			if (size == 0) {
				return Fn.node(c == DOUBLE_BLACK ? BLACK : RED, EMPTY_NODE, key, value, EMPTY_NODE);
			} else {
				int cmp = comparator.compare(key, this.k);
				if (cmp < 0) {
					return Fn.node(c, l._put(key, value, merge, comparator), k, v, r).balance();
				} else if (cmp > 0) {
					return Fn.node(c, l, k, v, r._put(key, value, merge, comparator)).balance();
				} else {
					return Fn.node(c, l, key, merge.apply(v, value), r);
				}
			}
		}
		/*
		 * public void split(int targetSize, IList<Node<K, V>> acc) { if (size >=
		 * targetSize * 2) { long offset = acc.size(); l.split(targetSize, acc); if
		 * (acc.size() > offset) { acc.set(offset, node(c, acc.nth(offset), k, v,
		 * EMPTY_NODE)); r.split(targetSize, acc); } else { r.split(targetSize, acc);
		 * acc.set(offset, node(c, EMPTY_NODE, k, v, acc.nth(offset))); } } else if
		 * (size > 0) { acc.addLast(this); } }
		 */

		public Node<K, V> balance() {
			if (size == 0) {
				return this;
			} else if (c == BLACK) {
				return balanceBlack();
			} else if (c == DOUBLE_BLACK) {
				return balanceDoubleBlack();
			} else {
				return this;
			}
		}

		public Node<K, V> rotate() {
			if (size == 0) {
				return this;
			}

			if (c == RED) {
				// (R (BB? a-x-b) y (B czd))
				// (balance (B (R (-B a-x-b) y c) z d))
				if (l.c == DOUBLE_BLACK && r.c == BLACK) {
					return Fn.black(Fn.red(l.unblacken(), k, v, r.l), r.k, r.v, r.r).balance();
				}

				// (R (B axb) y (BB? c-z-d))
				// (balance (B a x (R b y (-B c-z-d))))
				if (r.c == DOUBLE_BLACK && l.c == BLACK) {
					return Fn.black(l.l, l.k, l.v, Fn.red(l.r, k, v, r.unblacken())).balance();
				}
			} else if (c == BLACK) {

				// (B (BB? a-x-b) y (B czd))
				// (balance (BB (R (-B a-x-b) y c) z d))
				if (l.c == DOUBLE_BLACK && r.c == BLACK) {
					return Fn.node(DOUBLE_BLACK, Fn.red(l.unblacken(), k, v, r.l), r.k, r.v, r.r).balance();
				}

				// (B (B axb) y (BB? c-z-d))
				// (balance (BB a x (R b y (-B c-z-d))))
				if (l.c == BLACK && r.c == DOUBLE_BLACK) {
					return Fn.node(DOUBLE_BLACK, l.l, l.k, l.v, Fn.red(l.r, k, v, r.unblacken())).balance();
				}

				// (B (BB? a-w-b) x (R (B cyd) z e))
				// (B (balance (B (R (-B a-w-b) x c) y d)) z e)
				if (l.c == DOUBLE_BLACK && r.c == RED && r.l.c == BLACK) {
					Node<K, V> rl = r.l;
					return Fn.black(Fn.black(Fn.red(l.unblacken(), k, v, rl.l), rl.k, rl.v, rl.r).balance(), r.k, r.v, r.r);
				}

				// (B (R a w (B bxc)) y (BB? d-z-e))
				// (B a w (balance (B b x (R c y (-B d-z-e)))))
				if (l.c == RED && l.r.c == BLACK && r.c == DOUBLE_BLACK) {
					Node<K, V> lr = l.r;
					return Fn.black(l.l, l.k, l.v, Fn.black(lr.l, lr.k, lr.v, Fn.red(lr.r, k, v, r.unblacken())).balance());
				}
			}

			return this;
		}

		///

		private Node<K, V> balanceBlack() {
			if (l.c == RED) {
				// (B (R (R a x b) y c) z d)
				// (R (B a x b) y (B c z d))
				if (l.l.c == RED) {
					return Fn.red(l.l.blacken(), l.k, l.v, Fn.black(l.r, k, v, r));
				}

				// (B (R a x (R b y c)) z d)
				// (R (B a x b) y (B c z d))
				if (l.r.c == RED) {
					Node<K, V> lr = l.r;
					return Fn.red(Fn.black(l.l, l.k, l.v, lr.l), lr.k, lr.v, Fn.black(lr.r, k, v, r));
				}
			}

			if (r.c == RED) {
				// (B a x (R (R b y c) z d))
				// (R (B a x b) y (B c z d))
				if (r.l.c == RED) {
					Node<K, V> rl = r.l;
					return Fn.red(Fn.black(l, k, v, rl.l), rl.k, rl.v, Fn.black(rl.r, r.k, r.v, r.r));
				}

				// (B a x (R b y (R c z d))
				// (R (B a x b) y (B c z d))
				if (r.r.c == RED) {
					return Fn.red(Fn.black(l, k, v, r.l), r.k, r.v, r.r.blacken());
				}
			}

			return this;
		}

		private Node<K, V> balanceDoubleBlack() {
			// (BB (R a x (R b y c)) z d)
			// (B (B a x b) y (B c z d))
			if (l.c == RED && l.r.c == RED) {
				Node<K, V> lr = l.r;
				return Fn.black(Fn.black(l.l, l.k, l.v, lr.l), lr.k, lr.v, Fn.black(lr.r, k, v, r));
			}

			// (BB a x (R (R b y c) z d))
			// (B (B a x b) y (B c z d))
			if (r.c == RED && r.l.c == RED) {
				Node<K, V> rl = r.l;
				return Fn.black(Fn.black(l, k, v, rl.l), rl.k, rl.v, Fn.black(rl.r, r.k, r.v, r.r));
			}

			return this;
		}

		@SuppressWarnings("unchecked")
		private Node<K, V> removeMin() {
			if (l.size == 0) {
				if (c == RED) {
					return EMPTY_NODE;
				} else if (r.size == 0) {
					return DOUBLE_EMPTY_NODE;
				} else {
					return r.blacken();
				}
			}

			return Fn.node(c, l.removeMin(), k, v, r).rotate();
		}

		public long floorIndex(K key, Comparator<K> comparator, long offset) {
			if (size == 0) {
				return -1;
			}

			int cmp = comparator.compare(key, k);
			if (cmp > 0) {
				long idx = r.floorIndex(key, comparator, offset + l.size + 1);
				return idx < 0 ? offset + l.size : idx;
			} else if (cmp < 0) {
				return l.floorIndex(key, comparator, offset);
			} else {
				return offset + l.size;
			}
		}

		public long ceilIndex(K key, Comparator<K> comparator, long offset) {
			if (size == 0) {
				return -1;
			}

			int cmp = comparator.compare(key, k);
			if (cmp > 0) {
				return r.ceilIndex(key, comparator, offset + l.size + 1);
			} else if (cmp < 0) {
				long idx = l.ceilIndex(key, comparator, offset);
				return idx < 0 ? offset + l.size : idx;
			} else {
				return offset + l.size;
			}
		}

		public Node<K, V> slice(K min, K max, Comparator<K> comparator) {
			if (size == 0) {
				return this;
			}

			if (comparator.compare(k, min) < 0) {
				return r.slice(min, max, comparator);
			}

			if (comparator.compare(k, max) > 0) {
				return l.slice(min, max, comparator);
			}

			return Fn.node(c, l.slice(min, max, comparator), k, v, r.slice(min, max, comparator)).rotate();
		}

		@SuppressWarnings({ "unchecked", "hiding" })
		public <U> Node<K, U> mapValues(BiFunction<K, V, U> f) {
			return size == 0 ? (Node<K, U>) this : new Node<>(c, l.mapValues(f), k, f.apply(k, v), r.mapValues(f));
		}

		public int checkInvariant() {
			if (c == DOUBLE_BLACK) {
				throw new IllegalStateException();
			}

			if (size == 0) {
				return 1;
			}

			if (c == RED && (l.c == RED || r.c == RED)) {
				throw new IllegalStateException();
			}

			int ld = l.checkInvariant();
			int rd = r.checkInvariant();

			if (ld != rd) {
				throw new IllegalStateException();
			}

			int n = ld;
			if (c == BLACK) {
				n++;
			}

			return n;
		}

		@Override
		public K getKey() {
			return k;
		}

		@Override
		public V getValue() {
			return v;
		}

		@Override
		public V setValue(V value) {
			throw new UnsupportedOperationException("Not supported");
		}

		public int compare(K key, K k2) {
			// TODO Auto-generated method stub
			return 0;
		}
	}

	public interface Fn {

		public static <K, V> Node<K, V> min(Node<K, V> n) {
			for (;;) {
				if (n.l.size == 0) {
					return n;
				} else {
					n = n.l;
				}
			}
		}

		public static <K, V> Node<K, V> red(Node<K, V> l, K k, V v, Node<K, V> r) {
			return new Node<>(RED, l, k, v, r);
		}

		public static <K, V> Node<K, V> black(Node<K, V> l, K k, V v, Node<K, V> r) {
			return new Node<>(BLACK, l, k, v, r);
		}

		public static <K, V> Node<K, V> node(Color c, Node<K, V> l, K k, V v, Node<K, V> r) {
			return new Node<>(c, l, k, v, r);
		}

		public static <K, V> Node<K, V> slice(Node<K, V> n, K min, K max, Comparator<K> comparator) {
			return null;
		}

		public static <K, V> Node<K, V> find(Node<K, V> n, K key, Comparator<K> comparator) {
			for (;;) {
				if (n.size == 0) {
					return null;
				}

				int cmp = comparator.compare(key, n.k);
				if (cmp < 0) {
					n = n.l;
				} else if (cmp > 0) {
					n = n.r;
				} else {
					return n;
				}
			}
		}

		public static <K, V> long indexOf(Node<K, V> n, K key, Comparator<K> comparator) {
			long idx = 0;
			for (;;) {
				if (n.size == 0) {
					return -1;
				}

				int cmp = comparator.compare(key, n.k);
				if (cmp < 0) {
					n = n.l;
				} else if (cmp > 0) {
					idx += n.l.size + 1;
					n = n.r;
				} else {
					return idx + n.l.size;
				}
			}
		}

		public static <K, V> Node<K, V> nth(Node<K, V> n, int idx) {
			for (;;) {
				if (idx >= n.l.size) {
					idx -= n.l.size + 1;
					if (idx == -1) {
						return n;
					} else {
						n = n.r;
					}
				} else {
					n = n.l;
				}
			}
		}

		@SuppressWarnings("unchecked")
		public static <K, V> Iterator<Map.Entry<K, V>> iterator(Node<K, V> root) {

			if (root.size == 0) {
				return (Iterator<Entry<K, V>>) It.emptyIterator();
			}

			return new Iterator<Map.Entry<K, V>>() {
				final Node<K, V>[] stack = new Node[64];
				final byte[] cursor = new byte[64];
				int depth = 0;

				{
					stack[0] = root;
					nextValue();
				}

				private void nextValue() {
					while (depth >= 0) {
						Node<K, V> n = stack[depth];
						switch (cursor[depth]) {
						case 0:
							if (n.l.size == 0) {
								cursor[depth]++;
								return;
							} else {
								stack[++depth] = n.l;
								cursor[depth] = 0;
							}
							break;
						case 1:
							return;
						case 2:
							if (n.r.size == 0) {
								if (--depth >= 0) {
									cursor[depth]++;
								}
							} else {
								stack[++depth] = n.r;
								cursor[depth] = 0;
							}
							break;
						case 3:
							if (--depth >= 0) {
								cursor[depth]++;
							}
						}
					}
				}

				@Override
				public boolean hasNext() {
					return depth >= 0;
				}

				@Override
				public Map.Entry<K, V> next() {
					Node<K, V> n = stack[depth];
					cursor[depth]++;
					nextValue();
					return n;
				}
			};
		}
	}

	public interface Base<K, V> extends SortedMap<K, V> {
		
		Node<K, V> _root();
		Comparator<K> _comparator();
		
		
		@Override
		default Node<K, V> find(K key) {
			return Fn.find(_root(), key, _comparator());
		}

		@Override
		default Node<K, V> nth(long idx) {
			if (idx < 0 || idx >= count()) {
				throw new IndexOutOfBoundsException(String.format("%d must be within [0,%d)", idx, count()));
			}
			return Fn.nth(_root(), (int) idx);
		}

		@Override
		default long count() {
			return _root().size;
		}

		@Override
		default boolean equality(Object other) {
			return false;
		}

		@Override
		default Iterator<Entry<K, V>> iterator() {
			return Fn.iterator(_root());
		}

		@Override
		default Iterator<K> keys() {
			return It.map(iterator(), (n) -> n.getKey());
		}

		@Override
		default Iterator<V> vals() {
			return It.map(iterator(), (n) -> n.getValue());
		}
		
		@Override
		default long indexOfKey(K key) {
			return Fn.indexOf(_root(), key, _comparator());
		}

		@Override
		default long indexOfVal(V val) {
			throw new UnsupportedOperationException("Not Supported");
		}
		
		default long inclusiveFloorIndex(K key) {
			return _root().floorIndex(key, _comparator(), 0);
		}

		default long ceilIndex(K key) {
			return _root().ceilIndex(key, _comparator(), 0);
		}
	}

	public final class Mutable<K, V> extends Coll.RefType.MT 
		implements Base<K, V>,
				   I.ToPersistent{

		private final Comparator<K> _comparator;
		public Node<K, V> _root;

		public Mutable(I.Metadata meta, Node<K, V> root, Comparator<K> comparator) {
			super(meta);
			_root = root;
			_comparator = comparator;
		}

		@SuppressWarnings("unchecked")
		public Mutable() {
			this(null, Node.EMPTY_NODE, (Comparator<K>) Comparator.naturalOrder());
		}
		
		public Mutable<K, V> assocWith(K key, V value, BinaryOperator<V> merge) {
			Node<K, V> rootPrime = _root.put(key, value, merge, _comparator);
			_root = rootPrime;
			return this;
		}

		@Override
		public Mutable<K, V> assoc(K key, V value) {
			Node<K, V> rootPrime = _root.put(key, value, (o, n) -> n, _comparator);
			_root = rootPrime;
			return this;
		}
		
		@Override
		public Mutable<K, V> dissoc(K key) {
			_root = _root.remove(key, _comparator);
			return this;
		}

		@Override
		public Mutable<K, V> withMeta(I.Metadata meta) {
			throw new UnsupportedOperationException("Not Supported");
		}

		@Override
		public Standard<K, V> toPersistent() {
			return new Standard<K, V>(_meta, _root, _comparator);
		}

		@SuppressWarnings("unchecked")
		@Override
		public Mutable<K, V> empty() {
			_root = Node.EMPTY_NODE;
			return this;
		}

		@Override
		public Node<K, V> _root() {
			return _root;
		}

		@Override
		public Comparator<K> _comparator() {
			return _comparator;
		}
	}
	
	public final class Standard<K, V> extends Coll.RefType.PT 
		implements Base<K, V>, SortedMap<K, V>, I.ToMutable {

		// STATIC
		@SuppressWarnings({ "rawtypes", "unchecked" })
		public final static Standard EMPTY = new Standard(null, Node.EMPTY_NODE, Comparator.naturalOrder());
		public final Comparator<K> _comparator;
		public final Node<K, V> _root;

		public Standard(I.Metadata meta, Node<K, V> root, Comparator<K> comparator) {
			super(meta);
			_root = root;
			_comparator = comparator;
		}

		@SuppressWarnings("unchecked")
		public static <K, V> Standard<K, V> empty(I.Metadata meta) {
			Standard<K, V> ret = EMPTY;
			return (meta == null)
					? ret
					: ret.withMeta(meta);
		}
		
		public Standard<K, V> assocWith(K key, V value, BinaryOperator<V> merge) {
			Node<K, V> rootPrime = _root.put(key, value, merge, _comparator);
			return new Standard<K, V>(_meta, rootPrime, _comparator);
		}

		@Override
		public Standard<K, V> assoc(K key, V value) {
			Node<K, V> rootPrime = _root.put(key, value, (o, n) -> n, _comparator);
			return new Standard<K, V>(_meta, rootPrime, _comparator);
		}
		
		@Override
		public Standard<K, V> dissoc(K key) {
			Node<K, V> rootPrime = _root.remove(key, _comparator);
			return new Standard<K, V>(_meta, rootPrime, _comparator);
		}

		@Override
		public Standard<K, V> withMeta(I.Metadata meta) {
			return new Standard<K, V>(meta, _root, _comparator);
		}

		@Override
		public Mutable<K, V> toMutable() {
			return new Mutable<K, V>(meta(), _root, _comparator);
		}

		@Override
		public Standard<K, V> empty() {
			return empty(_meta);
		}

		@Override
		public Node<K, V> _root() {
			return _root;
		}

		@Override
		public Comparator<K> _comparator() {
			return _comparator;
		}
	}
}

