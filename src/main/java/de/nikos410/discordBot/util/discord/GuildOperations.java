package de.nikos410.discordBot.util.discord;

import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IRole;

public class GuildOperations {

    public static boolean hasRoleByID(final IGuild guild, final long roleID) {
        // More reliable than guild.getRoleByID(roleID) != null

        for (IRole role : guild.getRoles()) {
            if (roleID == role.getLongID()) {
                return true;
            }
        }
        return false;
    }
    public static boolean hasRoleByID(final IGuild guild, final String roleID) {
        // More reliable than guild.getRoleByID(roleID) != null

        for (IRole role : guild.getRoles()) {
            if (roleID.equals(role.getStringID())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasChannelByID(final IGuild guild, final long channelID) {
        for (IChannel channel : guild.getChannels()) {
            if (channelID == channel.getLongID()) {
                return true;
            }
        }
        return false;
    }
    public static boolean hasChannelByID(final IGuild guild, final String channelID) {
        for (IChannel channel : guild.getChannels()) {
            if (channelID.equals(channel.getStringID())) {
                return true;
            }
        }
        return false;
    }
}
