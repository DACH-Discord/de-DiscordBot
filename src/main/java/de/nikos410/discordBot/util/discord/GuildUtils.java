package de.nikos410.discordBot.util.discord;

import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IRole;

/**
 * Contains some helper methods for doing stuff involving guilds.
 */
public class GuildUtils {

    /**
     * Check whether a role with a given ID exists on a guild.
     *
     * @param guild The guild on which to search for the role.
     * @param roleID The ID of the role for which to search.
     * @return True if the role exists on that guild, otherwise false.
     */
    public static boolean roleExists(final IGuild guild, final long roleID) {
        // More reliable than guild.getRoleByID(roleID) != null

        for (IRole role : guild.getRoles()) {
            if (roleID == role.getLongID()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether a role with a given ID exists on a guild.
     *
     * @param guild The guild on which to search for the role.
     * @param roleID The ID of the role for which to search.
     * @return True if the role exists on that guild, otherwise false.
     */
    public static boolean roleExists(final IGuild guild, final String roleID) {
        // More reliable than guild.getRoleByID(roleID) != null

        for (IRole role : guild.getRoles()) {
            if (roleID.equals(role.getStringID())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether a channel with a given ID exists on a guild.
     *
     * @param guild The guild on which to search for the channel.
     * @param channelID The ID of the channel for which to search.
     * @return True if the channel exists on that guild, otherwise false.
     */
    public static boolean channelExists(final IGuild guild, final long channelID) {
        for (IChannel channel : guild.getChannels()) {
            if (channelID == channel.getLongID()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether a channel with a given ID exists on a guild.
     *
     * @param guild The guild on which to search for the channel.
     * @param channelID The ID of the channel for which to search.
     * @return True if the channel exists on that guild, otherwise false.
     */
    public static boolean channelExists(final IGuild guild, final String channelID) {
        for (IChannel channel : guild.getChannels()) {
            if (channelID.equals(channel.getStringID())) {
                return true;
            }
        }
        return false;
    }
}
