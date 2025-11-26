package hara.lang.lib;

import java.util.concurrent.CompletableFuture;

import hara.lang.base.I;
import hara.lang.base.I.Fn;

public class HostApplicative implements I.IApplicable, HasRuntime {

    public final Object function;
    public final Object form;
    public final boolean async;
    public final Object runtime;

    public HostApplicative(Object function, Object form, boolean async, Object runtime) {
        this.function = function;
        this.form = form;
        this.async = async;
        this.runtime = runtime;
    }

    @Override
    public Object applyIn(@SuppressWarnings("unused") Object app, @SuppressWarnings("unused") Object rt, Object args) {
        Fn f = (Fn) (function != null ? function : Eval.eval(form, null));
        if (async) {
            return CompletableFuture.supplyAsync(() -> f.invoke((Object[])args));
        } else {
            return f.invoke((Object[])args);
        }
    }

    @Override
    public Object applyDefault(Object app) {
        return null;
    }

    @Override
    public Object transformIn(Object app, Object rt, Object args) {
        return args;
    }

    @Override
    public Object transformOut(Object app, Object rt, Object args, Object retrn) {
        return retrn;
    }

    @Override
    public Object getRuntime() {
        return runtime;
    }
}
