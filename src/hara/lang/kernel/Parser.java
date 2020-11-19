package hara.lang.kernel;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.LineNumberReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hara.lang.data.Keyword;
import hara.lang.data.Symbol;
import hara.lang.base.*;

public interface Parser {

	public class LineNumberingReader extends PushbackReader {
	
		private static final int newline = (int) '\n';
	
		private boolean _atLineStart = true;
		private boolean _prev;
		private int _columnNumber = 1;
		private StringBuilder sb = null;
	
		public LineNumberingReader(Reader r) {
			super(new LineNumberReader(r));
		}
	
		public LineNumberingReader(Reader r, int size) {
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
}
