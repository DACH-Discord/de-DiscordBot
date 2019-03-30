package de.nikos410.discordbot.framework;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper class containing the properties for a bot command.
 */
public class CommandWrapper {
    private final String name;
    private final String help;
    private final Map<String, String> parameterDescriptions;
    private final ModuleWrapper module;
    private final Method method;
    private final boolean pmAllowed;
    private final PermissionLevel permissionLevel;
    private final int expectedParameterCount;
    private final boolean passContext;
    private final boolean ignoreParameterCount;

    /**
     * Create a new command wrapper.
     *
     * @param name The name of this command.
     * @param help The help for this command.
     * @param parameterDescriptions The parameterDescriptions for this command. Key is the parameter name, Value is the description for that parameter.
     * @param module The name of module that contains the command.
     * @param method The method that should be invoked to execute the command.
     * @param pmAllowed Set whether the command should be available in private messages.
     * @param permissionLevel Which permission level is necessary to execute the command.
     * @param expectedParameterCount The number of parameterDescriptions the command accepts.
     * @param passContext Set whether to append additional parameterDescriptions or to ignore them.
     * @param ignoreParameterCount Don't check if enough parameterDescriptions are given when executing.
     */
    public CommandWrapper(final String name,
                          final String help,
                          final Map<String, String> parameterDescriptions,
                          final ModuleWrapper module,
                          final Method method,
                          final boolean pmAllowed,
                          final PermissionLevel permissionLevel,
                          final int expectedParameterCount,
                          final boolean passContext,
                          final boolean ignoreParameterCount) {
        this.name = name;
        this.help = help;
        this.parameterDescriptions = parameterDescriptions;
        this.module = module;
        this.method = method;
        this.pmAllowed = pmAllowed;
        this.permissionLevel = permissionLevel;
        this.expectedParameterCount = expectedParameterCount;
        this.passContext = passContext;
        this.ignoreParameterCount = ignoreParameterCount;
    }

    /**
     * @return The name of this command.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The help for this command.
     */
    public String getHelp() {
        return help;
    }

    /**
     * @return The parameterDescriptions for this command.
     */
    public Map<String, String> getParameterDescriptions() {
        return parameterDescriptions;
    }

    /**
     * @return The name of the module that contains this command.
     */
    public ModuleWrapper getModule() {
        return module;
    }

    /**
     * @return The method that should be invoked to execute the command.
     */
    public Method getMethod() {
        return method;
    }

    /**
     * @return True if the command should be available in private messages.
     */
    public boolean isPmAllowed() {
        return pmAllowed;
    }

    /**
     * @return The permission level that is necessary to execute the command.
     */
    public PermissionLevel getPermissionLevel() {
        return permissionLevel;
    }

    /**
     * @return The number of parameterDescriptions the command accepts.
     */
    public int getExpectedParameterCount() {
        return expectedParameterCount;
    }

    /**
     * @return True if additional parameterDescriptions should be appended to the last one or ignored.
     */
    public boolean isPassContext() {
        return passContext;
    }

    /**
     * @return True if the parameter count check should be ignored.
     */
    public boolean isIgnoreParameterCount() {
        return ignoreParameterCount;
    }
}
