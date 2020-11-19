package hara.lang.base;

import java.util.Iterator;

import hara.lang.base.G.ObjType;
import hara.lang.base.I.Empty;
import hara.lang.base.T.Coll;

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
	}
	
	abstract class SEQ<V> extends PT 
		implements 
		Coll<V>, 
		I.Seq<V>, 
		I.ObjType,
		T.SeqType<V>,
		I.SequentialType<V>{

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
			return new Cons.Standard<V>(_meta, e, this);
		}
		
		@Override
		public SEQ<V> conj(V e) {
			return new Cons.Standard<V>(_meta, e, this);
		}

		@Override
		public String hashSeed() {
			return null;
		}

		@SuppressWarnings("rawtypes")
		@Override
		public Coll empty() {
			return Cons.EMPTY.withMeta(_meta);
		}
	}
	
	abstract class EMPTY<V> extends PT implements Coll<V>, I.Seq<V> {
	
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
		public EMPTY<V> empty() {
			return this;
		}
	
		@Override
		public V first() {
			return null;
		}
	
		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Iterator iterator() {
			return Iter.emptyIterator();
		}
	
		@Override
		public I.Seq<V> next() {
			return null;
		}
	
		@Override
		public V nth(long i) {
			return null;
		}
	
		@Override
		public boolean restEnd() {
			return true;
		}
	
		@Override
		public I.Seq<V> restMore() {
			return this;
		}
	
		@Override
		public I.Seq<V> toSeq() {
			return null;
		}
	}
}
