package de.nikos410.discordBot.util.modular;

import java.lang.reflect.Method;

public class Command {
    public final Object object;
    public final Method method;

    public final String help;
    public final boolean pmAllowed;
    public final int permissionLevel;
    public final int parameterCount;

    public Command(final Object object, final Method method, final String help, final boolean pmAllowed, final int permissionLevel, final int parameterCount) {
        this.object = object;
        this.method = method;
        this.help = help;
        this.pmAllowed = pmAllowed;
        this.permissionLevel = permissionLevel;
        this.parameterCount = parameterCount;
    }
}
