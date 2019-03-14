package de.nikos410.discordbot.framework;

import java.util.List;

/**
 * Wrapper class containing a bot module's instance and a list of all commands in this module.
 */
public class ModuleWrapper {
    private final Class<? extends CommandModule> moduleClass;
    private final String name;

    private String displayName;
    private String description;
    private CommandModule instance;
    private List<CommandWrapper> commands;
    private ModuleStatus status;

    public ModuleWrapper(final Class<? extends CommandModule> moduleClass) {
        this.moduleClass = moduleClass;
        this.name = moduleClass.getSimpleName();
    }

    public Class<? extends CommandModule> getModuleClass() {
        return moduleClass;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CommandModule getInstance() {
        return instance;
    }

    public void setInstance(final CommandModule instance) {
        this.instance = instance;
    }

    public List<CommandWrapper> getCommands() {
        return commands;
    }

    public void setCommands(final List<CommandWrapper> commands) {
        this.commands = commands;
    }

    public ModuleStatus getStatus() {
        return status;
    }

    public void setStatus(final ModuleStatus status) {
        this.status = status;
    }

    /**
     * Enum for specifying the current status of a module.
     */
    public enum ModuleStatus {
        /**
         * The module is loaded, it's features are available.
         */
        ACTIVE,

        /**
         * The module is not loaded, it's features are not available.
         */
        INACTIVE,

        /**
         * The module could not be loaded.
         */
        FAILED,
    }
}
