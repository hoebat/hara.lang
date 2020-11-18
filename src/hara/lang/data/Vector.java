package hara.lang.data;

import java.util.Iterator;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;

import hara.lang.base.*;
import hara.lang.base.G.HashType;

public interface Vector<E> extends T.VectorType<E> {

	public interface Fn {

		// STATIC METHODS
		public static Node editableRoot(Node node) {
			return new Node(new AtomicReference<>(Thread.currentThread()), node.array.clone());
		}

		public static Node editableNode(Node node, AtomicReference<Thread> edit) {
			return (node.edit == edit) 
					? node
					: new Node(edit, node.array.clone());
		}

		public static int tailoff(int size) {
			return (size < Node.NODE_MAXLEN) 
					? 0 
					: ((size - 1) >>> Node.NODE_SHIFT) << Node.NODE_SHIFT;
		}
		
		@SuppressWarnings("unchecked")
		public static <E> E[] getNodeArrayFor(
				Node root, int size, int shift, 
				E[] tail, int i, boolean editable) {
			if (i >= 0 && i < size) {
				if (i >= Fn.tailoff(size)) {
					return tail;
				}
				Node node = root;
				for (int level = shift; level > 0; level -= Node.NODE_SHIFT) {
					node = (editable == false) 
							? (Node) node.array[(i >>> level) & Node.NODE_MASK]
							: editableNode((Node) node.array[(i >>> level) & Node.NODE_MASK], root.edit);
				}
				return (E[]) node.array;
			}
			throw new IndexOutOfBoundsException();
		}

		public static Node newPath(AtomicReference<Thread> edit, int level, Node node) {
			if (level == 0) return node;
			var ret = new Node(edit);
			ret.array[0] = newPath(edit, level - Node.NODE_SHIFT, node);
			return ret;
		}

		public static Object[] makeTail(Object[] tail) {
			var ret = new Object[32];
			System.arraycopy(tail, 0, ret, 0, tail.length);
			return ret;
		}
		
		public static Object[] newTail(Object[] tail, int i, Object val){
			var newTail = new Object[tail.length];
			System.arraycopy(tail, 0, newTail, 0, tail.length);
			newTail[i & Node.NODE_MASK] = val;
			return newTail;
		}
		
		public static Object[] newTailAppend(Object[] tail, Object val) {
			var newTail = new Object[tail.length + 1];
			System.arraycopy(tail, 0, newTail, 0, tail.length);
			newTail[tail.length] = val;
			return newTail;
		}

		
		public static Object[] trimTail(Object[] tail, int size) { 
			var trimmed = new Object[size - tailoff(size)];
			System.arraycopy(tail, 0, trimmed, 0, trimmed.length);
			return trimmed;
		}
		
		public static Node pushTail(
				Node root, int size, int level, 
				Node parent, Node _tailnode, boolean editable) {
			if (editable == true) parent = Fn.editableNode(parent, root.edit);
			int subidx = ((size - 1) >>> level) & Node.NODE_MASK;
			var ret = new Node(parent.edit, parent.array.clone());
			Node nodeToInsert;
			if (level == Node.NODE_SHIFT) {
				nodeToInsert = _tailnode;
			} else {
				Node child = (Node) parent.array[subidx];
				nodeToInsert = (child == null) 
						? Fn.newPath(root.edit, level - Node.NODE_SHIFT, _tailnode)
						: pushTail(root, size, level - Node.NODE_SHIFT, child, _tailnode, editable);
			}
			ret.array[subidx] = nodeToInsert;
			return ret;
		}

		public static Node popTail(
				Node root, int size, int level, 
				Node node, boolean editable) {
			if (editable == true) node = Fn.editableNode(node, root.edit);
			int subidx = ((size - 2) >>> level) & Node.NODE_MASK;
			if (level > Node.NODE_SHIFT) {
				Node newchild = popTail(root, size, level - Node.NODE_SHIFT, (Node) node.array[subidx], editable);
				if (newchild == null && subidx == 0)
					return null;
				else {
					node.array[subidx] = newchild;
					return node;
				}
			} else if (subidx == 0)
				return null;
			else {
				node.array[subidx] = null;
				return node;
			}
		}
		
		public static Node assoc(Node node, int level, int i, Object val) {
			var ret = new Node(node.edit, node.array.clone());
			if (level == 0) {
				ret.array[i & Node.NODE_MASK] = val;
			} else {
				int subidx = (i >>> level) & Node.NODE_MASK;
				ret.array[subidx] = assoc((Node) node.array[subidx], level - Node.NODE_SHIFT, i, val);
			}
			return ret;
		}
	}

	public class Node {
		public static final int NODE_SHIFT = 5;
		public static final int NODE_MAXLEN = 1 << NODE_SHIFT;
		public static final int NODE_MASK = NODE_MAXLEN - 1;

		public static final AtomicReference<Thread> NOEDIT = new AtomicReference<>(null);
		public static final Node EMPTY = new Node(NOEDIT, new Object[NODE_MAXLEN]);

		// DATA
		public transient final AtomicReference<Thread> edit;
		public final Object[] array;

		// CONSTRUCTORS
		public Node(AtomicReference<Thread> edit, Object[] array) {
			this.edit = edit;
			this.array = array;
		}

		public Node(AtomicReference<Thread> edit) {
			this.edit = edit;
			this.array = new Object[NODE_MAXLEN];
		}
	}

	// CONSTANTS
	public interface Base<E> extends Vector<E>, I.Assoc<Integer, E>, I.ObjType {

		public Node _root();

		public int _size();

		public int _shift();

		public E[] _tail();

		@Override
		default Base<E> conj(E e) {
			return (Base<E>) pushLast(e);
		}

		@Override
		default long count() {
			return _size();
		}
		
		default Iterator<E> rangedIterator(final int start, final int end) {
			return new Iterator<E>() {
				int i = start;
				int base = i - (i % 32);
				E[] array = (start < count()) 
						? Fn.getNodeArrayFor(_root(), _size(), _shift(), _tail(), i, false)
						: null;

				public boolean hasNext() {
					return i < end;
				}

				public E next() {
					if (i < end) {
						if (i - base == 32) {
							array = Fn.getNodeArrayFor(_root(), _size(), _shift(), _tail(), i, false);
							base += 32;
						}
						return array[i++ & 0x01f];
					} else {
						throw new NoSuchElementException();
					}
				}

				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
		}

		@Override
		default Iterator<E> iterator() {
			return rangedIterator(0, _size());
		}
	}

	public class Mutable<E> extends T.RefType.MT implements Base<E>, I.ToPersistent {

		private int _size;
		private int _shift;
		private transient Node _root;
		private E[] _tail;

		@SuppressWarnings("unchecked")
		public Mutable(Base<E> v) {
			this(v.meta(), v._size(), v._shift(), Fn.editableRoot(v._root()), (E[]) Fn.makeTail(v._tail()));
		}

		public Mutable(I.Metadata meta, int size, int shift, Node root, E[] tail) {
			super(meta);
			_size = size;
			_shift = shift;
			_root = root;
			_tail = tail;
		}

		private void checkEditable() {
			if (_root.edit.get() == null) {
				throw new IllegalStateException("Mutable used after immutable! call");
			}
		}

		@Override
		public int _size() {
			return _size;
		}

		@Override
		public int _shift() {
			return _shift;
		}

		@Override
		public Node _root() {
			checkEditable();
			return _root;
		}

		@Override
		public E[] _tail() {
			checkEditable();
			return _tail;
		}

		@SuppressWarnings("unchecked")
		public Mutable<E> pushLast(E val) {
			checkEditable();
			int i = _size;
			// room in _tail?
			if (i - Fn.tailoff(_size) < Node.NODE_MAXLEN) {
				_tail[i & Node.NODE_MASK] = val;
				++_size;
				return this;
			}
			// full _tail, push into tree
			Node new_root;
			Node _tailnode = new Node(_root.edit, _tail);
			_tail = (E[]) new Object[Node.NODE_MAXLEN];
			_tail[0] = val;
			int new_shift = _shift;
			// overflow _root?
			if ((_size >>> Node.NODE_SHIFT) > (1 << _shift)) {
				new_root = new Node(_root.edit);
				new_root.array[0] = _root;
				new_root.array[1] = Fn.newPath(_root.edit, _shift, _tailnode);
				new_shift += Node.NODE_SHIFT;
			} else
				new_root = Fn.pushTail(_root, _size, _shift, _root, _tailnode, true);
			_root = new_root;
			_shift = new_shift;
			++_size;
			return this;
		}

		@Override
		public E nth(long i) {
			checkEditable();
			E[] node = Fn.getNodeArrayFor(_root, _size, _shift, _tail, (int) i, true);
			return node[(int) i & Node.NODE_MASK];
		}

		@Override
		public Mutable<E> assoc(Integer idx, E e) {
			checkEditable();
			E[] node = Fn.getNodeArrayFor(_root, _size, _shift, _tail, idx, true);
			node[idx & Node.NODE_MASK] = e;
			return this;
		}

		@Override
		public Mutable<E> popLast() {
			checkEditable();
			if (_size == 0)
				throw new IllegalStateException("Can't pop empty vector");
			if (_size == 1) {
				_size = 0;
				return this;
			}
			int i = _size - 1;
			if ((i & Node.NODE_MASK) > 0) {
				_size--;
				return this;
			}

			E[] newtail = Fn.getNodeArrayFor(_root, _size, _shift, _tail, _size - 2, true);

			Node newroot = Fn.popTail(_root, _size, _shift, _root, true);
			int newshift = _shift;
			if (newroot == null) {
				newroot = new Node(_root.edit);
			}
			if (_shift > Node.NODE_SHIFT && newroot.array[1] == null) {
				newroot = Fn.editableNode((Node) newroot.array[0], _root.edit);
				newshift -= Node.NODE_SHIFT;
			}
			_root = newroot;
			_shift = newshift;
			_size--;
			_tail = newtail;
			return this;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Standard<E> toPersistent() {
			_root.edit.set(null);
			return new Standard<>(_meta, _size, _shift, _root, (E[]) Fn.trimTail((Object[])_tail, _size));
		}

		@SuppressWarnings("unchecked")
		@Override
		public Mutable<E> empty() {
			return new Mutable<E>(null, 0, Node.NODE_SHIFT, Node.EMPTY, (E[]) new Object[] {});
		}
	}

	public class Standard<E> extends T.RefType.PT implements Base<E>, I.ToMutable {

		// STATIC
		public final static Standard<Object> EMPTY = new Standard<>(null, 0, Node.NODE_SHIFT, Node.EMPTY, new Object[] {});

		// INSTANCE
		private final int _size;
		private final int _shift;
		private final transient Node _root;
		private final E[] _tail;

		public Standard(Base<E> v) {
			this(v.meta(), v._size(), v._shift(), v._root(), v._tail());
		}

		protected Standard(I.Metadata meta, int size, int shift, Node root, E[] tail) {
			super(meta);
			_size = size;
			_shift = shift;
			_root = root;
			_tail = tail;
		}

		@SuppressWarnings("unchecked")
		public static <E> Standard<E> empty(I.Metadata meta) {
			Standard<E> ret = (Standard<E>) EMPTY;
			return (meta == null) ? ret : ret.withMeta(meta);
		}

		@Override
		public int _size() {
			return _size;
		}

		@Override
		public int _shift() {
			return _shift;
		}

		@Override
		public Node _root() {
			return _root;
		}

		@Override
		public E[] _tail() {
			return _tail;
		}

		@Override
		public Standard<E> withMeta(I.Metadata meta) {
			return (meta() == meta) ? this : new Standard<E>(meta, _size, _shift, _root, _tail);
		}

		@Override
		public E nth(long i) {
			E[] node = Fn.getNodeArrayFor(_root, _size, _shift, _tail, (int) i, false);
			return node[(int) i & Node.NODE_MASK];
		}

		@SuppressWarnings("unchecked")
		@Override
		public Standard<E> assoc(Integer i, E val) {
			if (i >= 0 && i < _size) {
				return (i >= Fn.tailoff(_size))
					? new Standard<>(_meta, _size, _shift, _root, (E[]) Fn.newTail(_tail, i, val))
					: new Standard<>(_meta, _size, _shift, Fn.assoc(_root, _shift, i, val), _tail);
			}
			if (i == _size) {
				return pushLast(val);
			}
			throw new IndexOutOfBoundsException();
		}

		@SuppressWarnings("unchecked")
		@Override
		public Standard<E> pushLast(E val) {
			if (_size - Fn.tailoff(_size) < Node.NODE_MAXLEN) {
				return new Standard<>(_meta, _size + 1, _shift, _root, 
										(E[]) Fn.newTailAppend((Object[])_tail, val));
			}

			Node new_root;
			Node _tailnode = new Node(_root.edit, _tail);
			int new_shift = _shift;
			if ((_size >>> Node.NODE_SHIFT) > (1 << _shift)) {
				new_root = new Node(_root.edit);
				new_root.array[0] = _root;
				new_root.array[1] = Fn.newPath(_root.edit, _shift, _tailnode);
				new_shift += Node.NODE_SHIFT;
			} else {
				new_root = Fn.pushTail(_root, _size, _shift, _root, _tailnode, false);
			}
			return new Standard<>(_meta, _size + 1, new_shift, new_root, (E[]) new Object[] { val });
		}

		@SuppressWarnings("unchecked")
		@Override
		public Standard<E> popLast() {
			if (_size == 0)
				throw new IllegalStateException("Can't pop empty vector");
			if (_size == 1)
				return empty(_meta);
			if (_size - Fn.tailoff(_size) > 1) {
				E[] newTail = (E[]) new Object[_tail.length - 1];
				System.arraycopy(_tail, 0, newTail, 0, newTail.length);
				return new Standard<E>(_meta, _size - 1, _shift, _root, newTail);
			}
			E[] newtail = Fn.getNodeArrayFor(_root, _size, _shift, _tail, _size - 2, false);

			Node new_root = Fn.popTail(_root, _size, _shift, _root, false);
			int new_shift = _shift;
			if (new_root == null) {
				new_root = Node.EMPTY;
			}
			if (_shift > Node.NODE_SHIFT && new_root.array[1] == null) {
				new_root = (Node) new_root.array[0];
				new_shift -= Node.NODE_SHIFT;
			}
			return new Standard<E>(_meta, _size - 1, new_shift, new_root, newtail);
		}

		@Override
		public Standard<E> empty() {
			return empty(_meta);
		}

		@Override
		public Mutable<E> toMutable() {
			return new Mutable<E>(this);
		}
	}

}