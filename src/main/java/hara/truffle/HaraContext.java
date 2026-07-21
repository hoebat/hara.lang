package hara.truffle;

import com.oracle.truffle.api.TruffleLanguage;

final class HaraContext {
  private final TruffleLanguage.Env environment;

  HaraContext(TruffleLanguage.Env environment) {
    this.environment = environment;
  }

  TruffleLanguage.Env environment() {
    return environment;
  }
}
