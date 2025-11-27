package hara.lang.kernel;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Permissions {

    public static final Permissions ALL = new Permissions(true, Collections.emptySet());
    public static final Permissions NONE = new Permissions(false, Collections.emptySet());

    private final boolean allowAll;
    private final Set<String> allowedCommands;

    private Permissions(boolean allowAll, Set<String> allowedCommands) {
        this.allowAll = allowAll;
        this.allowedCommands = Collections.unmodifiableSet(new HashSet<>(allowedCommands));
    }

    public Permissions allow(String command) {
        Set<String> newSet = new HashSet<>(allowedCommands);
        newSet.add(command);
        return new Permissions(this.allowAll, newSet);
    }

    public Permissions allow(Enum<?> command) {
        return allow(command.name());
    }

    public boolean check(String command) {
        if (allowAll) return true;
        return allowedCommands.contains(command);
    }
}
