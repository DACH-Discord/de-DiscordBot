package de.nikos410.discordBot.framework;

/**
 * Provides 4 permission levels
 */
public enum PermissionLevel {
    /**
     * The default level.
     */
    EVERYONE("Everyone", 0),

    /**
     * Necessary to use the moderator tools.
     */
    MODERATOR("Moderator", 1),

    /**
     * Necessary to setup the bot for a guild.
     */
    ADMIN("Admin", 2),

    /**
     * Necessary to configure and manage the bot.
     */
    OWNER("Owner", 3);

    final String name;
    final int level;

    PermissionLevel(final String levelName, int level) {
        this.name = levelName;
        this.level = level;
    }

    /**
     * Get the name of the permission level.
     *
     * @return The name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get the permission level as a number.
     *
     * @return The level.
     */
    public int getLevel() {
        return this.level;
    }
}
