package hara.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import hara.kernel.base.Parser;
import hara.kernel.base.Reader;
import java.util.ArrayList;
import java.util.List;

@TruffleLanguage.Registration(
    id = HaraLanguage.ID,
    name = "Hara",
    implementationName = "Hara Truffle",
    version = "0.1",
    defaultMimeType = HaraLanguage.MIME_TYPE,
    characterMimeTypes = HaraLanguage.MIME_TYPE)
public final class HaraLanguage extends TruffleLanguage<HaraContext> {
  public static final String ID = "hara";
  public static final String MIME_TYPE = "application/x-hara";

  @Override
  protected HaraContext createContext(Env environment) {
    return new HaraContext(environment);
  }

  public static HaraContext currentContext() {
    return getCurrentContext(HaraLanguage.class);
  }

  @Override
  protected CallTarget parse(ParsingRequest request) {
    Source source = request.getSource();
    SourceSection sourceSection =
        source.getLength() == 0
            ? source.createUnavailableSection()
            : source.createSection(0, source.getLength());
    return HaraAnalyzer.compile(
        this, readAll(source.getCharacters().toString()), sourceSection, currentContext());
  }

  private static Object[] readAll(String source) {
    Reader reader = new Reader(source);
    Object eof = new Object();
    List<Object> forms = new ArrayList<>();
    Object form;
    do {
      form = Parser.LispReader.read(reader, false, eof, false, null);
      if (form != eof) {
        forms.add(form);
      }
    } while (form != eof);
    return forms.toArray();
  }
}
