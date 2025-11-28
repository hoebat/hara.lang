package hara.lang.protocol;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Map;
import hara.lang.base.*;
import java.util.function.*;
import java.util.regex.Pattern;

public interface IColl<E> extends Iterable<E>, IEquality, IConj<E>, IEmpty, ICount, IHash, IDisplay {

	String startString();

	String endString();

	default String sepString() {
		return " ";
	}

	@Override
	default String display() {
		return It.display(iterator(), startString(), endString(), sepString());
	}
}