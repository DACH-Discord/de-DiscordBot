package de.nikos410.discordBot.framework;

import java.lang.reflect.Method;

public class Command {
    public final Object object;
    public final Method method;

    public final boolean pmAllowed;
    public final int permissionLevel;
    public final int parameterCount;
    public final boolean passContext;

    public Command(final Object object, final Method method, final boolean pmAllowed, final int permissionLevel, final int parameterCount, final boolean passContext) {
        this.object = object;
        this.method = method;
        this.pmAllowed = pmAllowed;
        this.permissionLevel = permissionLevel;
        this.parameterCount = parameterCount;
        this.passContext = passContext;
    }
}
