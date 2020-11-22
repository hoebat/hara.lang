package hara.test;

import static org.junit.jupiter.api.Assertions.*;
import hara.lang.data.*;
//import hara.lang.interpreter.RT.L;

import org.junit.jupiter.api.Test;

import hara.lang.base.I;

@SuppressWarnings({"unchecked"})
class EqTest {

	@Test
	void consConjEquals() {
		var x = L.cons().cons(1).cons(2);
		var y = Vector.Standard.EMPTY.conj(2).conj(1);
		assertEquals(x, y);
	}
	
	void consConsEq() {

		var x = L.cons().cons(1).cons(2);
		var y = Vector.Standard.EMPTY.conj(2).conj(1);
		
		assert(((I.Equality) x).equality(y));
	}

}
