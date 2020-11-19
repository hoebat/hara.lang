package hara.lang.base;

public interface Ex {

	@SuppressWarnings("serial")
	public class Arity extends IllegalArgumentException {

		final public int actual;

		final public String name;

		public Arity(int actual, String name) {
			this(actual, name, null);
		}

		public Arity(int actual, String name, Throwable cause) {
			super("Ex.Arity - Wrong number of args (" + actual + ") passed to: " + name, cause);
			this.actual = actual;
			this.name = name;
		}

	}	


	@SuppressWarnings("serial")
	public class Unsupported extends UnsupportedOperationException {
		
		public Unsupported() {
			super("Not Supported");
		}
		
		public Unsupported(String message) {
			super(message);
		}
	}

	@SuppressWarnings("serial")
	public class Info extends RuntimeException implements I.ExInfo {
		public final I.Metadata data;

		public Info(String s, I.Metadata data) {
			this(s, data, null);
		}

		public Info(String s, I.Metadata data, Throwable throwable) {
			super(s, throwable);
			if (data != null) {
				this.data = data;
			} else {
				throw new IllegalArgumentException("Additional data must be non-nil.");
			}
		}

		public I.Metadata getData() {
			return data;
		}

		public String toString() {
			return "Ex.Info - " + getMessage() + " " + data.toString();
		}
	}

	@SuppressWarnings("serial")
	public class Syntax extends RuntimeException {
		public Syntax() {
		}

		public Syntax(String message) {
			super(message);
		}

		public Syntax(String message, Throwable cause) {
			super(message, cause);
		}

		public Syntax(Throwable cause) {
			super(cause);
		}

		public Syntax(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
			super(message, cause, enableSuppression, writableStackTrace);
		}
	}

	@SuppressWarnings("serial")
	public class UndefinedSymbol extends RuntimeException {
		public UndefinedSymbol() {
		}

		public UndefinedSymbol(String message) {
			super(message);
		}

		public UndefinedSymbol(String message, Throwable cause) {
			super(message, cause);
		}

		public UndefinedSymbol(Throwable cause) {
			super(cause);
		}

		public UndefinedSymbol(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
			super(message, cause, enableSuppression, writableStackTrace);
		}
	}

}
