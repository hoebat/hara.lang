package hara.lang.base;

import java.util.Iterator;

import hara.lang.base.G.HashType;
import hara.lang.data.Keyword;

public interface Obj {

	abstract class MT implements I.ObjType, I.Mutable, I.Hash {
		protected I.Metadata _meta;

		public MT() {
			_meta = null;
		}

		public MT(I.Metadata meta) {
			_meta = meta;
		}

		@Override
		public I.Metadata meta() {
			return _meta;
		}

		@Override
		public MT withMeta(I.Metadata meta) {
			_meta = meta;
			return this;
		}
		
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public String toString() {
			if(this instanceof I.Coll) {
				return getClass().getName() + "<" + It.display(
						((I.Coll)this).iterator(), "", "", ",") + ">";
			} else {
				return getClass().getName() + "<" + display() + ">";
			}
		}
	}

	abstract class PT implements I.ObjType, I.Persistent, I.HashCached {
		protected final I.Metadata _meta;
		private long _hash;

		public PT() {
			_meta = null;
		}

		public PT(I.Metadata meta) {
			_meta = meta;
		}

		@Override
		public final I.Metadata meta() {
			return _meta;
		}

		@Override
		public long hashCurrent() {
			return _hash;
		}

		@Override
		public void hashPut(long hash) {
			_hash = hash;
		}
		
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public String toString() {
			if(this instanceof I.Coll) {
				return getClass().getName() + "<" + It.display(
						((I.Coll)this).iterator(), "", "", ",") + ">";
			} else {
				return getClass().getName() + "<" + display() + ">";
			}
		}
	}
	/*
	abstract class SEQ<V> extends PT 
		implements 
		I.Coll<V>, 
		I.ObjType,
		Data.SequentialType<V>{

		public SEQ() {
			super(null);
		}
	
		public SEQ(I.Metadata meta) {
			super(meta);
		}
		
		@Override 
		public ObjType getObjType() {
			return G.ObjType.SEQUENTIAL;
		}
		
		@Override
		public SEQ<V> cons(V e) {
			return new Std.T.Cons<V>(_meta, e, this);
		}
		
		@Override
		public SEQ<V> conj(V e) {
			return new Std.T.Cons<V>(_meta, e, this);
		}

		@Override
		public String hashSeed() {
			return null;
		}

		@SuppressWarnings("rawtypes")
		@Override
		public I.Coll empty() {
			return Std.T.Cons.EMPTY.withMeta(_meta);
		}
	}*/
	
	abstract class FN extends PT {
		
		public FN() {
			super(null);
		}

		public FN(I.Metadata meta) {
			super(meta);
		}

		@Override
		public G.ObjType getObjType() {
			return G.ObjType.FUNCTION;
		}

		@Override
		public FN withMeta(I.Metadata meta) {
			throw new Ex.Unsupported();
		}

		@Override
		public long hashCalc(HashType t) {
			return G.hashFn(t).apply(hashSeed()) * 31 + ((I.Hash)_meta).hashCalc(t);
		}

		@Override
		public String display() {
			var name = Keyword.create("name").invoke(_meta);
			 return "#<" + name + ">";
		}
		
	}
	
	abstract class EMPTY<E> extends PT implements I.Coll<E>, I.Nth<E> {
	
		public EMPTY() {
			super(null);
		}
	
		public EMPTY(I.Metadata meta) {
			super(meta);
		}
	
		@Override
		public long count() {
			return 0;
		}
	
		@Override
		public EMPTY<E> empty() {
			return this;
		}
	
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Iterator iterator() {
			return It.emptyIterator();
		}
	
		@Override
		public E nth(long i) {
			return null;
		}
	}
}
