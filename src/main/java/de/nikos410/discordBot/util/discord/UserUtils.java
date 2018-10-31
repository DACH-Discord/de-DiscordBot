package de.nikos410.discordBot.util.discord;

import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;

import java.util.List;

/**
 * Contains some helper methods for doing stuff involving users.
 */
public class UserUtils {

    /**
     * Returns the highest role of a user.
     *
     * @param user The user whose highest role will be returned.
     * @param guild The guild for which to get the user's highest role.
     * @return The highest role of the given user for the given guild.
     */
    public static IRole getTopRole(final IUser user, final IGuild guild) {
        final List<IRole> roles = user.getRolesForGuild(guild);
        IRole topRole = guild.getEveryoneRole();

        for (IRole role : roles) {
            if (role.getPosition() > topRole.getPosition()) {
                topRole = role;
            }
        }
        return topRole;
    }

    public static boolean hasRole(final IUser user, final IRole role, final IGuild guild) {
        return hasRole(user, role.getLongID(), guild);
    }

    public static boolean hasRole(final IUser user, final long roleID, final IGuild guild) {
        final List<IRole> roles = user.getRolesForGuild(guild);

        for (IRole role : roles) {
            if (roleID == role.getLongID()) {
                return true;
            }
        }
        return false;
    }

    public static String makeUserString(final IUser user, final IGuild guild) {
        final String name = user.getName();
        final String displayName = user.getDisplayName(guild);

        if (name.equals(displayName)) {
            return name;
        }
        else {
            return String.format("%s (%s#%s)", displayName, name, user.getDiscriminator());
        }

    }
}
