package hara.lang.lib;

import java.io.File;
import java.net.MalformedURLException;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.aether.resolution.DependencyResolutionException;

import hara.lang.base.Ex;
import hara.lang.base.I;
import hara.lang.base.I.OFn;
import hara.lang.maven.Resolver;

public final class Maven {

    private static final Resolver resolver = new Resolver();

    private static Object doLoad(I.Runtime rt, String coordinate) {
        try {
            List<File> files = resolver.resolve(coordinate);
            for (File file : files) {
                rt.pathAdd(file.toURI().toURL());
            }
            return true;
        } catch (DependencyResolutionException | MalformedURLException e) {
            throw Ex.Sneaky(e);
        }
    }

    public static final OFn load = new OFn() {
        @Override
        public BiFunction<Object, Object, Object> getArg2() {
            return (rt, coordinate) -> doLoad((I.Runtime) rt, (String) coordinate);
        }

        @Override
        public Function<Object, Object> getArgN() {
            return (vargs) -> {
                Object[] args = (Object[]) vargs;
                return doLoad((I.Runtime) args[0], (String) args[1]);
            };
        }
    };
}
