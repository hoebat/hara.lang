package hara.lang.interpreter;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.LineNumberReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hara.lang.base.*;
import hara.lang.base.Data.MapType;
import hara.lang.data.*;
import hara.lang.module.Builtin;

public interface Read {

	public class LineNumberingReader extends PushbackReader {

		private static final int newline = (int) '\n';

		private boolean _atLineStart = true;
		private boolean _prev;
		private int _columnNumber = 1;
		private StringBuilder sb = null;

		public LineNumberingReader(PushbackReader r) {
			super(new LineNumberReader(r));
		}

		public LineNumberingReader(PushbackReader r, int size) {
			super(new LineNumberReader(r, size));
		}

		public int getLineNumber() {
			return ((LineNumberReader) in).getLineNumber() + 1;
		}

		public void setLineNumber(int line) {
			((LineNumberReader) in).setLineNumber(line - 1);
		}

		public void captureString() {
			this.sb = new StringBuilder();
		}

		public String getString() {
			if (sb != null) {
				String ret = sb.toString();
				sb = null;
				return ret;
			}
			return null;
		}

		public int getColumnNumber() {
			return _columnNumber;
		}

		public int read() throws IOException {
			int c = super.read();
			_prev = _atLineStart;
			if ((c == newline) || (c == -1)) {
				_atLineStart = true;
				_columnNumber = 1;
			} else {
				_atLineStart = false;
				_columnNumber++;
			}
			if (sb != null && c != -1)
				sb.append((char) c);
			return c;
		}

		public void unread(int c) throws IOException {
			super.unread(c);
			_atLineStart = _prev;
			_columnNumber--;
			if (sb != null)
				sb.deleteCharAt(sb.length() - 1);
		}

		public String readLine() throws IOException {
			int c = read();
			String line;
			switch (c) {
			case -1:
				line = null;
				break;
			case newline:
				line = "";
				break;
			default:
				String first = String.valueOf((char) c);
				String rest = ((LineNumberReader) in).readLine();
				if (sb != null)
					sb.append(rest + "\n");
				line = (rest == null) ? first : first + rest;
				_prev = false;
				_atLineStart = true;
				_columnNumber = 1;
				break;
			}
			return line;
		}

		public boolean atLineStart() {
			return _atLineStart;
		}
	}

	@SuppressWarnings("rawtypes")
	public class LispReader {

		static final Keyword EOF = Keyword.create(null, "eof");

		static BiFunction[] macros = new BiFunction[256];
		static BiFunction[] dispatchMacros = new BiFunction[256];
		static Pattern symbolPat = Pattern.compile("[:]?([\\D&&[^/]].*/)?(/|[\\D&&[^/]][^/]*)");
		static Pattern intPat = Pattern.compile(
				"([-+]?)(?:(0)|([1-9][0-9]*)|0[xX]([0-9A-Fa-f]+)|0([0-7]+)|([1-9][0-9]?)[rR]([0-9A-Za-z]+)|0[0-9]+)(N)?");
		static Pattern ratioPat = Pattern.compile("([-+]?[0-9]+)/([0-9]+)");
		static Pattern floatPat = Pattern.compile("([-+]?[0-9]+(\\.[0-9]*)?([eE][-+]?[0-9]+)?)(M)?");

		// static Function taggedReader = new TaggedReader();

		static {
			macros['"'] = new StringReader();
			macros[';'] = new CommentReader();
			macros['^'] = new MetaReader();
			macros['('] = new ListReader();
			macros[')'] = new UnmatchedDelimiterReader();
			macros['['] = new VectorReader();
			macros[']'] = new UnmatchedDelimiterReader();
			macros['{'] = new MapReader();
			macros['}'] = new UnmatchedDelimiterReader();
			macros['\\'] = new CharacterReader();
			macros['#'] = new DispatchReader();

			dispatchMacros['#'] = new SymbolicValueReader();
			dispatchMacros['^'] = new MetaReader();
			dispatchMacros['"'] = new RegexReader();
			dispatchMacros['{'] = new SetReader();
			dispatchMacros['['] = new QueueReader();
			dispatchMacros['<'] = new UnreadableReader();
			dispatchMacros['_'] = new DiscardReader();

		}

		public interface S {

			static boolean nonConstituent(int ch) {
				return ch == '@' || ch == '`' || ch == '~';
			}

			static boolean isWhitespace(int ch) {
				return Character.isWhitespace(ch) || ch == ',';
			}

		}

		static public Object readString(String s, Map opts) {
			PushbackReader r = new PushbackReader(new java.io.StringReader(s));

			return read(r, (opts == null) ? Builtin.hashMap(new Object[] {}) : opts);
		}

		static void unread(PushbackReader r, int ch) {
			if (ch != -1)
				try {
					r.unread(ch);
				} catch (IOException e) {
					throw Ex.Sneaky(e);
				}
		}

		@SuppressWarnings("serial")
		public static class ReaderException extends RuntimeException {
			final int line;
			final int column;

			public ReaderException(int line, int column, Throwable cause) {
				super(cause);
				this.line = line;
				this.column = column;
			}
		}

		static public int readSingle(PushbackReader r) {
			try {
				return r.read();
			} catch (IOException e) {
				throw Ex.Sneaky(e);
			}
		}

		@SuppressWarnings("unchecked")
		static public Object read(PushbackReader r, Map opts) {
			return read(r, !opts.has(EOF), opts.lookup(EOF), false, opts);
		}

		@SuppressWarnings("unchecked")
		static public Object read(PushbackReader r, boolean eofIsError, Object eofValue, boolean isRecursive,
				Map opts) {

			try {
				for (;;) {
					int ch = readSingle(r);

					while (S.isWhitespace(ch))
						ch = readSingle(r);

					if (ch == -1) {
						if (eofIsError)
							throw new Ex.Runtime("EOF while reading");
						return eofValue;
					}

					if (Character.isDigit(ch)) {
						Object n = readNumber(r, (char) ch);
						return n;
					}

					BiFunction macroFn = getMacro(ch);
					if (macroFn != null) {
						Object ret = macroFn.apply(r, opts);
						if (ret == r)
							continue;
						return ret;
					}

					if (ch == '+' || ch == '-') {
						int ch2 = readSingle(r);
						if (Character.isDigit(ch2)) {
							unread(r, ch2);
							Object n = readNumber(r, (char) ch);
							return n;
						}
						unread(r, ch2);
					}

					String token = readToken(r, (char) ch, true);
					return interpretToken(token);
				}
			} catch (Exception e) {
				if (isRecursive || !(r instanceof LineNumberingReader))
					throw Ex.Sneaky(e);
				LineNumberingReader rdr = (LineNumberingReader) r;
				// throw new Ex.Runtime(String.format("ReaderError:(%d,1) %s",
				// rdr.getLineNumber(), e.getMessage()), e);
				throw new ReaderException(rdr.getLineNumber(), rdr.getColumnNumber(), e);
			}
		}

		@SuppressWarnings("unchecked")
		public static ArrayList readDelimitedList(char delim, PushbackReader r, boolean isRecursive, Map opts) {
			final int firstline = (r instanceof LineNumberingReader) ? ((LineNumberingReader) r).getLineNumber() : -1;

			ArrayList list = new ArrayList();

			for (;;) {
				int ch = readSingle(r);

				while (S.isWhitespace(ch))
					ch = readSingle(r);

				if (ch == -1) {
					if (firstline < 0)
						throw new Ex.Runtime("EOF while reading");
					else
						throw new Ex.Runtime("EOF while reading, starting at line " + firstline);
				}

				if (ch == delim)
					break;

				BiFunction macroFn = getMacro(ch);
				if (macroFn != null) {
					Object mret = macroFn.apply(r, opts);
					if (mret != r)
						list.add(mret);
				} else {
					unread(r, ch);
					Object o = read(r, true, null, isRecursive, opts);
					if (o != r)
						list.add(o);
				}
			}
			return list;
		}

		static private String readToken(PushbackReader r, char initch, boolean leadConstituent) {
			StringBuilder sb = new StringBuilder();
			if (leadConstituent && S.nonConstituent(initch))
				throw new Ex.Runtime("Invalid leading character: " + initch);

			sb.append(initch);

			for (;;) {
				int ch = readSingle(r);

				if (ch == -1 || S.isWhitespace(ch) || isTerminatingMacro(ch)) {
					unread(r, ch);
					return sb.toString();
				} else if (S.nonConstituent(ch))
					throw new Ex.Runtime("Invalid constituent character: " + (char) ch);
				sb.append((char) ch);
			}
		}

		static private Object readNumber(PushbackReader r, char initch) {
			StringBuilder sb = new StringBuilder();
			sb.append(initch);

			for (;;) {
				int ch = readSingle(r);
				if (ch == -1 || S.isWhitespace(ch) || isMacro(ch)) {
					unread(r, ch);
					break;
				}
				sb.append((char) ch);
			}

			String s = sb.toString();
			Object n = matchNumber(s);
			if (n == null)
				throw new NumberFormatException("Invalid number: " + s);
			return n;
		}

		static private int readUnicodeChar(String token, int offset, int length, int base) {
			if (token.length() != offset + length)
				throw new IllegalArgumentException("Invalid unicode character: \\" + token);
			int uc = 0;
			for (int i = offset; i < offset + length; ++i) {
				int d = Character.digit(token.charAt(i), base);
				if (d == -1)
					throw new IllegalArgumentException("Invalid digit: " + token.charAt(i));
				uc = uc * base + d;
			}
			return (char) uc;
		}

		static private int readUnicodeChar(PushbackReader r, int initch, int base, int length, boolean exact) {
			int uc = Character.digit(initch, base);
			if (uc == -1)
				throw new IllegalArgumentException("Invalid digit: " + (char) initch);
			int i = 1;
			for (; i < length; ++i) {
				int ch = readSingle(r);
				if (ch == -1 || S.isWhitespace(ch) || isMacro(ch)) {
					unread(r, ch);
					break;
				}
				int d = Character.digit(ch, base);
				if (d == -1)
					throw new IllegalArgumentException("Invalid digit: " + (char) ch);
				uc = uc * base + d;
			}
			if (i != length && exact)
				throw new IllegalArgumentException("Invalid character length: " + i + ", should be: " + length);
			return uc;
		}

		static private Object interpretToken(String s) {
			if (s.equals("nil")) {
				return null;
			} else if (s.equals("true")) {
				return G.T;
			} else if (s.equals("false")) {
				return G.F;
			}

			Object ret = matchSymbol(s);
			if (ret != null)
				return ret;

			throw new Ex.Runtime("Invalid token: " + s);
		}

		private static Object matchSymbol(String s) {
			Matcher m = symbolPat.matcher(s);
			if (m.matches()) {
				// int gc = m.groupCount();
				String ns = m.group(1);
				String name = m.group(2);
				if (ns != null && ns.endsWith(":/") || name.endsWith(":"))
					return null;

				boolean isKeyword = s.charAt(0) == ':';

				return isKeyword ? Keyword.create(ns, name) : Symbol.create(ns, name);
			}
			return null;
		}

		private static Number matchNumber(String s) {
			Matcher m = intPat.matcher(s);
			if (m.matches()) {
				if (m.group(2) != null) {
					if (m.group(8) != null)
						return BigInteger.ZERO;
					return Num.num(0);
				}
				boolean negate = (m.group(1).equals("-"));
				String n;
				int radix = 10;
				if ((n = m.group(3)) != null)
					radix = 10;
				else if ((n = m.group(4)) != null)
					radix = 16;
				else if ((n = m.group(5)) != null)
					radix = 8;
				else if ((n = m.group(7)) != null)
					radix = Integer.parseInt(m.group(6));
				if (n == null)
					return null;
				BigInteger bn = new BigInteger(n, radix);
				if (negate)
					bn = bn.negate();
				if (m.group(8) != null)
					return bn;
				return bn.bitLength() < 64 ? Num.num(bn.longValue()) : bn;
			}
			m = floatPat.matcher(s);
			if (m.matches()) {
				if (m.group(4) != null)
					return new BigDecimal(m.group(1));
				return Double.parseDouble(s);
			}
			/*
			 * m = ratioPat.matcher(s); if (m.matches()) { String numerator = m.group(1); if
			 * (numerator.startsWith("+")) numerator = numerator.substring(1);
			 * 
			 * return Num.divide(Num.reduceBigInt(BigInt.fromBigInteger(new
			 * BigInteger(numerator))), Num.reduceBigInt(BigInt.fromBigInteger(new
			 * BigInteger(m.group(2))))); }
			 */
			return null;
		}

		static private BiFunction getMacro(int ch) {
			if (ch < macros.length)
				return macros[ch];
			return null;
		}

		static private boolean isMacro(int ch) {
			return (ch < macros.length && macros[ch] != null);
		}

		static private boolean isTerminatingMacro(int ch) {
			return (ch != '#' && ch != '\'' && isMacro(ch));
		}

		public static class UnreadableReader implements BiFunction<PushbackReader, Map, Void> {
			@Override
			public Void apply(PushbackReader reader, Map opts) {
				throw new Ex.Runtime("Unreadable form");
			}
		}

		public static class UnmatchedDelimiterReader implements BiFunction<PushbackReader, Map, Void> {

			@SuppressWarnings("unchecked")
			@Override
			public Void apply(PushbackReader reader, Map opts) {
				throw new Ex.Runtime("Unmatched delimiter: " + opts.lookup(Keyword.create("delimiter")));
			}
		}

		public static class CharacterReader implements BiFunction<PushbackReader, Map, Character> {

			@Override
			public Character apply(PushbackReader reader, Map opts) {
				PushbackReader r = (PushbackReader) reader;
				int ch = readSingle(r);
				if (ch == -1)
					throw new Ex.Runtime("EOF while reading character");
				String token = readToken(r, (char) ch, false);
				if (token.length() == 1)
					return Character.valueOf(token.charAt(0));
				else if (token.equals("newline"))
					return '\n';
				else if (token.equals("space"))
					return ' ';
				else if (token.equals("tab"))
					return '\t';
				else if (token.equals("backspace"))
					return '\b';
				else if (token.equals("formfeed"))
					return '\f';
				else if (token.equals("return"))
					return '\r';
				else if (token.startsWith("u")) {
					char c = (char) readUnicodeChar(token, 1, 4, 16);
					if (c >= '\uD800' && c <= '\uDFFF') // surrogate code unit?
						throw new Ex.Runtime("Invalid character constant: \\u" + Integer.toString(c, 16));
					return c;
				} else if (token.startsWith("o")) {
					int len = token.length() - 1;
					if (len > 3)
						throw new Ex.Runtime("Invalid octal escape sequence length: " + len);
					int uc = readUnicodeChar(token, 1, len, 8);
					if (uc > 0377)
						throw new Ex.Runtime("Octal escape sequence must be in range [0, 377].");
					return (char) uc;
				}
				throw new Ex.Runtime("Unsupported character: \\" + token);
			}
		}

		public static class StringReader implements BiFunction<PushbackReader, Map, String> {
			@Override
			public String apply(PushbackReader r, Map opts) {
				StringBuilder sb = new StringBuilder();

				for (int ch = readSingle(r); ch != '"'; ch = readSingle(r)) {
					if (ch == -1)
						throw new Ex.Runtime("EOF while reading string");
					if (ch == '\\') // escape
					{
						ch = readSingle(r);
						if (ch == -1)
							throw new Ex.Runtime("EOF while reading string");
						switch (ch) {
						case 't':
							ch = '\t';
							break;
						case 'r':
							ch = '\r';
							break;
						case 'n':
							ch = '\n';
							break;
						case '\\':
							break;
						case '"':
							break;
						case 'b':
							ch = '\b';
							break;
						case 'f':
							ch = '\f';
							break;
						case 'u': {
							ch = readSingle(r);
							if (Character.digit(ch, 16) == -1)
								throw new Ex.Runtime("Invalid unicode escape: \\u" + (char) ch);
							ch = readUnicodeChar((PushbackReader) r, ch, 16, 4, true);
							break;
						}
						default: {
							if (Character.isDigit(ch)) {
								ch = readUnicodeChar((PushbackReader) r, ch, 8, 3, false);
								if (ch > 0377)
									throw new Ex.Runtime("Octal escape sequence must be in range [0, 377].");
							} else
								throw new Ex.Runtime("Unsupported escape character: \\" + (char) ch);
						}
						}
					}
					sb.append((char) ch);
				}
				return sb.toString();
			}
		}

		public static class RegexReader implements BiFunction<PushbackReader, Map, Pattern> {
			static StringReader stringrdr = new StringReader();

			@Override
			public Pattern apply(PushbackReader r, Map opts) {
				StringBuilder sb = new StringBuilder();
				for (int ch = readSingle(r); ch != '"'; ch = readSingle(r)) {
					if (ch == -1)
						throw new Ex.Runtime("EOF while reading regex");
					sb.append((char) ch);
					if (ch == '\\') // escape
					{
						ch = readSingle(r);
						if (ch == -1)
							throw new Ex.Runtime("EOF while reading regex");
						sb.append((char) ch);
					}
				}
				return Pattern.compile(sb.toString());
			}
		}

		public static class CommentReader implements BiFunction<PushbackReader, Map, Reader> {
			@Override
			public Reader apply(PushbackReader r, Map opts) {
				int ch;
				do {
					ch = readSingle(r);
				} while (ch != -1 && ch != '\n' && ch != '\r');
				return r;
			}
		}

		public static class DiscardReader implements BiFunction<PushbackReader, Map, Reader> {
			@Override
			public Reader apply(PushbackReader r, Map opts) {
				read(r, true, null, true, opts);
				return r;
			}
		}

		public static class DispatchReader implements BiFunction<PushbackReader, Map, Object> {

			@SuppressWarnings("unchecked")
			@Override
			public Object apply(PushbackReader r, Map opts) {
				int ch = readSingle(r);
				if (ch == -1)
					throw new Ex.Runtime("EOF while reading character");
				BiFunction fn = dispatchMacros[ch];

				if (fn == null) {
					// try tagged reader

					/*
					 * if (Character.isLetter(ch)) { unread((PushbackReader) reader, ch); return
					 * taggedReader.invoke(reader, ch, opts); }
					 */

					throw new Ex.Runtime(String.format("No dispatch macro for: %c", (char) ch));
				}
				return fn.apply(r, opts);
			}
		}

		public static class SymbolicValueReader implements BiFunction<PushbackReader, Map, Object> {

			static Map specials = Builtin.hashMap(new Object[] {
					Symbol.create(null, "Inf"), Double.POSITIVE_INFINITY,
					Symbol.create(null, "-Inf"), Double.NEGATIVE_INFINITY, 
					Symbol.create(null, "NaN"), Double.NaN });

			@SuppressWarnings("unchecked")
			@Override
			public Object apply(PushbackReader r, Map opts) {
				Object o = read(r, true, null, true, opts);

				if (!(o instanceof Symbol))
					throw new Ex.Runtime("Invalid token: ##" + o);
				if (!(specials.has(o)))
					throw new Ex.Runtime("Unknown symbolic value: ##" + o);

				return specials.lookup(o);
			}
		}

		public static I.Metadata getMeta(PushbackReader r) {
			int line = -1;
			int column = -1;
			if (r instanceof LineNumberingReader) {
				line = ((LineNumberingReader) r).getLineNumber();
				column = ((LineNumberingReader) r).getColumnNumber() - 1;
			}
			return Builtin.hashMap(Arr.objects(Builtin.keyword("line"), line, Builtin.keyword("column"), column));
		}

		public static class ListReader implements BiFunction<PushbackReader, Map, List> {
			@Override
			public List apply(PushbackReader r, Map opts) {
				ArrayList list = readDelimitedList(')', r, true, opts);
				return (list.isEmpty()) ? List.Standard.EMPTY : Builtin.list(list.toArray());
			}
		}

		public static class MetaReader implements BiFunction<PushbackReader, Map, I.ObjType> {

			@Override
			public I.ObjType apply(PushbackReader r, Map opts) {

				Object meta = read(r, true, null, true, opts);

				if (meta instanceof Symbol || meta instanceof String)
					meta = Builtin.hashMap(Arr.objects(Builtin.keyword("tag"), meta));
				else if (meta instanceof Keyword)
					meta = Builtin.hashMap(Arr.objects(meta, G.T));
				else if (!(meta instanceof Map))
					throw new IllegalArgumentException("Metadata must be Symbol,Keyword,String or ");

				Object o = read(r, true, null, true, opts);
				if (o instanceof I.ObjType) {
					Data.MapType ometa = (Data.MapType) ((I.ObjType) o).meta();
					ometa = (MapType) Builtin.merge(ometa, meta);
					return ((I.ObjType) o).withMeta(ometa);
				} else
					throw new IllegalArgumentException("Metadata can only be applied to I.ObjTypes");
			}
		}

		public static class VectorReader implements BiFunction<PushbackReader, Map, Vector> {
			@Override
			public Vector apply(PushbackReader r, Map opts) {
				ArrayList list = readDelimitedList(']', r, true, opts);

				return Builtin.vector(list.toArray());
			}
		}

		public static class MapReader implements BiFunction<PushbackReader, Map, OrderedMap> {
			@Override
			public OrderedMap apply(PushbackReader r, Map opts) {
				ArrayList list = readDelimitedList('}', r, true, opts);

				if ((list.size() & 1) == 1) {
					throw new Ex.Runtime(" literal must contain an even number of forms");
				}

				// TODO: Check for same keys
				return Builtin.orderedMap(list.toArray());
			}
		}

		public static class SetReader implements BiFunction<PushbackReader, Map, OrderedSet> {
			@Override
			public OrderedSet apply(PushbackReader r, Map opts) {
				ArrayList list = readDelimitedList('}', r, true, opts);
				// TODO: Check for same entries
				return Builtin.orderedSet(list.toArray());
			}
		}

		public static class QueueReader implements BiFunction<PushbackReader, Map, Queue> {
			@Override
			public Queue apply(PushbackReader r, Map opts) {
				ArrayList list = readDelimitedList(']', r, true, opts);
				return Builtin.queue(list.toArray());
			}
		}
	}
}
