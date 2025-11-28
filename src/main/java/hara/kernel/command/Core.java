package hara.kernel.command;

import hara.compiler.Compiler;
import hara.compiler.DynamicClassLoader;
import hara.kernel.Command;
import hara.kernel.Foundation;
import hara.kernel.base.Read;
import hara.lang.base.Ex;
import hara.lang.base.G;

import java.util.List;

public class Core {

  @Command.Fn(name = "HELP")
  public static Object cmdHELP(Foundation F, List<Object> args) {
    return Foundation.Fn.runHELP(F, F.REGISTRY.keySet());
  }

  @Command.Fn(name = "SHUTDOWN")
  public static Object cmdSHUTDOWN(Foundation F, List<Object> args) {
    System.exit(1);
    return null;
  }

  @Command.Fn(name = "PING")
  public static Object cmdPING(Foundation F, List<Object> args) {
    return "PONG";
  }

  @Command.Fn(name = "ECHO")
  public static Object cmdECHO(Foundation F, List<Object> args) {
    return args;
  }

  @Command.Fn(name = "DIR")
  public static Object cmdDIR(Foundation F, List<Object> args) {
    return Foundation.Fn.runDIR(F);
  }

  @Command.Fn(name = "INFO")
  public static Object runInfo(Foundation F, List<Object> args) {
    return Foundation.Fn.runInfo(F);
  }

  @Command.Fn(name = "EVAL")
  public static Object runEval(Foundation F, List<Object> args) {
    return Foundation.Fn.runSessionFor(
        F, args.get(0).toString(), rt -> G.display(rt.eval(rt.readString(args.get(1).toString()))));
  }

  @Command.Fn(name = "COMPILE")
  public static Object runCompile(Foundation F, List<Object> args) {
    try {
      hara.lang.data.List expression =
          (hara.lang.data.List) Read.LispReader.readString(args.get(0).toString(), null);
      Compiler compiler = new Compiler();
      byte[] bytecode = compiler.compile(expression);
      DynamicClassLoader loader = new DynamicClassLoader(Foundation.class.getClassLoader());
      Class<?> clazz = loader.defineClass(null, bytecode);
      return clazz.getConstructor().newInstance();
    } catch (Exception e) {
      throw Ex.Sneaky(e);
    }
  }
}
