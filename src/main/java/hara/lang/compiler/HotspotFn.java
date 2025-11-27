package hara.lang.compiler;

import hara.lang.base.*;
import hara.lang.data.*;
import hara.lang.lib.Env;
import hara.lang.lib.Builtin;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import java.util.Iterator;

@SuppressWarnings("rawtypes")
public class HotspotFn extends Obj.FN implements I.Fn {
    private final Env.FnEval interpreted;
    private I.Fn compiled;
    private final AtomicLong counter = new AtomicLong(0);
    private static final long THRESHOLD = 5;

    public HotspotFn(Env.FnEval interpreted) {
        super(interpreted.meta());
        this.interpreted = interpreted;
    }

    public I.Fn getTarget() {
        if (compiled != null) {
            return compiled;
        }

        long count = counter.incrementAndGet();
        if (count == THRESHOLD) {
            compile();
        }
        return interpreted;
    }

    private synchronized void compile() {
        if (compiled != null) return;
        try {
            List bodyList = (List) interpreted.getBody();
            Object params = interpreted.getParams();

            // Check if body is (do expr)
            if (bodyList.count() == 2 &&
                bodyList.nth(0) instanceof Symbol &&
                ((Symbol)bodyList.nth(0)).getName().equals("do")) {

                Object expr = bodyList.nth(1);

                // Construct (fn params expr)
                List expression = Builtin.Struct.list(
                    new Object[] {
                        Builtin.Basic.symbol("fn"),
                        params,
                        expr
                    }
                );

                Compiler compiler = new Compiler();
                Compiler.Result result = compiler.compile(expression);

                // FnEval has _rt field which is I.Runtime
                // But interpreted is Env.FnEval type, which has .getRuntime() method via inner class FnEnv?
                // No, FnEval is a static class. It has _rt field.
                // It does NOT have getRuntime() public method.
                // Env.FnEval implements I.Fn, extending Obj.FN.

                // Looking at Env.java again:
                // FnEval has `final I.Runtime _rt;`
                // And `final I.Env _env;`
                // It does NOT expose them via getters.

                // I need to add getRuntime() to FnEval in Env.java.
                // I previously added getBody() and getParams().

                DynamicClassLoader loader = new DynamicClassLoader(((I.Runtime)interpreted.getRuntime()).classLoader());
                Class<?> compiledClass = loader.defineClass(result.className.replace('/', '.'), result.bytes);
                compiled = (I.Fn) compiledClass.getConstructor().newInstance();
            }
        } catch (Throwable t) {
            // Fail silently, stick to interpreted
            t.printStackTrace();
        }
    }

    public I.Fn getCompiled() {
        return compiled;
    }

    @Override
    public Supplier getArg0() { return getTarget().getArg0(); }

    @Override
    public Function getArg1() { return getTarget().getArg1(); }

    @Override
    public BiFunction getArg2() { return getTarget().getArg2(); }

    @Override
    public Function getArgN() { return getTarget().getArgN(); }

    @Override
    public Object apply(Object args) { return getTarget().apply(args); }
}
