package de.nikos410.discordBot.framework;

import java.lang.reflect.Method;

/**
 * Wrapper class containing the properties for a bot command.
 */
public class Command {
    public final Object object;
    public final Method method;

    public final boolean pmAllowed;
    public final PermissionLevel permissionLevel;
    public final int parameterCount;
    public final boolean passContext;
    public final boolean ignoreParameterCount;

    /**
     * Create a new command object.
     *
     * @param object The module that contains the command.
     * @param method The method that should be invoked to execute the command.
     * @param pmAllowed Set whether the command should be available in private messages.
     * @param permissionLevel Which permission level is necessary to execute the command.
     * @param parameterCount The number of parameters the command accepts.
     * @param passContext Set whether to append additional parameters or to ignore them.
     * @param ignoreParameterCount Don't check if enough parameters are given when executing.
     */
    public Command(final Object object, final Method method, final boolean pmAllowed, final PermissionLevel permissionLevel,
                   final int parameterCount, final boolean passContext, final boolean ignoreParameterCount) {
        this.object = object;
        this.method = method;
        this.pmAllowed = pmAllowed;
        this.permissionLevel = permissionLevel;
        this.parameterCount = parameterCount;
        this.passContext = passContext;
        this.ignoreParameterCount = ignoreParameterCount;
    }
}
