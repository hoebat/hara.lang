package hara.lang.kernel;

import java.util.List;

@FunctionalInterface
public interface Cmd {
    Object apply(Foundation f, List<Object> args);
}
