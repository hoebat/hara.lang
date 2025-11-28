package hara.lang.kernel.command;

import java.util.List;
import java.util.Map;
import hara.lang.base.It;
import hara.lang.base.Ex;
import hara.lang.base.Arr;
import hara.lang.base.G;
import hara.lang.kernel.Command;
import hara.lang.kernel.Foundation;
import hara.lang.lib.Read;
import hara.lang.compiler.Compiler;
import hara.lang.compiler.CompilerException;
import hara.lang.compiler.DynamicClassLoader;

public class Core {

    @Command(name = "HELP")
    public static Object cmdHELP(Foundation F, List<Object> args) {
        return Foundation.Fn.runHELP(F, F.REGISTRY.keySet());
    }

    @Command(name = "SHUTDOWN")
    public static Object cmdSHUTDOWN(Foundation F, List<Object> args) {
        System.exit(1);
        return null;
    }

    @Command(name = "PING")
    public static Object cmdPING(Foundation F, List<Object> args) {
        return "PONG";
    }

    @Command(name = "ECHO")
    public static Object cmdECHO(Foundation F, List<Object> args) {
        return args;
    }

    @Command(name = "DIR")
    public static Object cmdDIR(Foundation F, List<Object> args) {
        return Foundation.Fn.runDIR(F);
    }

    @Command(name = "INFO")
    public static Object runInfo(Foundation F, List<Object> args) {
        return Foundation.Fn.runInfo(F);
    }

    @Command(name = "EVAL")
    public static Object runEval(Foundation F, List<Object> args) {
        return Foundation.Fn.runSessionFor(F, args.get(0).toString(),
                rt -> G.display(rt.eval(rt.readString(args.get(1).toString()))));
    }

    @Command(name = "COMPILE")
    public static Object runCompile(Foundation F, List<Object> args) {
        try {
            hara.lang.data.List expression = (hara.lang.data.List) Read.LispReader.readString(args.get(0).toString(), null);
            Compiler compiler = new Compiler();
            byte[] bytecode = compiler.compile(expression);
            DynamicClassLoader loader = new DynamicClassLoader(Foundation.class.getClassLoader());
            Class<?> clazz = loader.defineClass(null, bytecode);
            return clazz.getConstructor().newInstance();
        } catch (CompilerException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw Ex.Sneaky(e);
        }
    }
}
