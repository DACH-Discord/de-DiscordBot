package de.nikos410.discordbot.util.discord;

import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;

import java.util.List;

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

    /**
     * Get the role that was specified in the given message. First, checks if the parameter contains a valid role ID
     * and returns the corresponding channel. Second, checks if the message contains exactly one role Mention and returns
     * the mentioned role.
     *
     * @param message The message that should be checked for mentions.
     * @param roleParameter The parameter that should be checked for a valid role ID.
     * @return The role that was specified, or null if none was found.
     */
    public static IRole getRoleFromMessage(final IMessage message, final String roleParameter) {
        if (message == null || roleParameter == null) {
            return null;
        }

        if (roleParameter.matches("^[0-9]{18}$")) {
            // The channel parameter looks like a role ID
            // Try to find a channel with that ID
            final IRole role = message.getGuild().getRoleByID(Long.parseLong(roleParameter));
            if (role != null) {
                // A channel was found
                return role;
            }
        }

        final List<IRole> mentions = message.getRoleMentions();
        if (mentions.size() == 1) {
            // Exactly one mention found
            return mentions.get(0);
        }
        else {
            // Too many or no mentions found
            return null;
        }
    }
}
