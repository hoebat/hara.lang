package hara.lang.interpreter;

import java.util.Iterator;

import hara.lang.base.*;
import hara.lang.data.*;


@SuppressWarnings("rawtypes")
public interface RT {

		/*
		public static Object first(Iterator it) {
			return it.next();
		}
		
		public static Object first(I.Seq seq) {
			return seq.first();
		}
		
		public static Object first(Session s, Object obj) {
			Class obj = obj.getClass();
			
			
		}
		
		
		
		public static Iterator rest(Iterator it) {
			it.next();
			return it; 
		}
		
		public static I.Seq rest(I.Seq it) {
			return it.restMore();
		}

		public static Iterator next(Iterator it) {
			if(!it.hasNext()) return null;
			
			it.next();
			return it;
		}
		
		public static I.Seq next(I.Seq it) {
			return it.next();
		}
		
		
		public static Cons cons() {
			return Cons.EMPTY;
		}
		
		public static I.Cons<Object> cons(I.Cons<Object> c, Object item) {
			return c.cons(item);
		}
		
		public static I.Conj<Object> conj(I.Conj<Object> c, Object item) {
			return c.conj(item);
		}
		*/
}
