package hara.kernel.base;

import java.util.concurrent.ConcurrentHashMap;
import hara.lang.data.Symbol;

public class Namespace {
  public final Symbol name;
  public final ConcurrentHashMap<Symbol, Var> mappings;
  public final ConcurrentHashMap<Symbol, Namespace> aliases;
  public final ConcurrentHashMap<Symbol, Class> imports;

  public Namespace(Symbol name) {
    this.name = name;
    this.mappings = new ConcurrentHashMap<>();
    this.aliases = new ConcurrentHashMap<>();
    this.imports = new ConcurrentHashMap<>();
  }
}
