package hara.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import hara.kernel.base.Parser;

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

  @Override
  protected CallTarget parse(ParsingRequest request) {
    Object form =
        Parser.LispReader.readString(request.getSource().getCharacters().toString(), null);
    return HaraAnalyzer.compile(this, form);
  }
}
