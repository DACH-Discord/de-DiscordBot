package de.nikos410.discordbot.util.discord;

import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;

import java.util.List;

/**
 * Contains some helper methods for doing stuff involving users.
 */
public class UserUtils {

    /**
     * Get the user that was specified in the given message. First, checks if the parameter contains a valid user ID
     * and returns the corresponding user. Second, checks if the message contains exactly one user Mention and returns
     * the mentionend user.
     *
     * @param message The message that should be checked for mentions.
     * @param userParameter The parameter that should be checked for a valid user ID.
     * @return The user that was specified, or null if none was found.
     */
    public static IUser getUserFromMessage(final IMessage message, final String userParameter) {
        if (message == null || userParameter == null) {
            return null;
        }

        if (userParameter.matches("^[0-9]{18}$")) {
            // The user parameter looks like a user ID
            // Try to find a user with that ID
            final IUser user = message.getGuild().getUserByID(Long.parseLong(userParameter));
            if (user != null) {
                // A user was found
                return user;
            }
        }

        final List<IUser> mentions = message.getMentions();
        if (mentions.size() == 1) {
            // Exactly one mention found
            return mentions.get(0);
        }
        else {
            // Too many or no mentions found
            return null;
        }
    }

    /**
     * Get the highest role of a user.
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

    /**
     * Check if a user has a role on a guild.
     *
     * @param user The user who to check for the role.
     * @param role The role for which to check.
     * @param guild The guild on which to check the user for the role.
     * @return True if the user has the given role on the given guild, otherwise false.
     */
    public static boolean hasRole(final IUser user, final IRole role, final IGuild guild) {
        return hasRole(user, role.getLongID(), guild);
    }

    /**
     * Check if a user has a role on a guild.
     *
     * @param user The user who to check for the role.
     * @param roleID The ID of the role for which to check.
     * @param guild The guild on which to check the user for the role.
     * @return True if the user has a role with the given ID on the given guild, otherwise false.
     */
    public static boolean hasRole(final IUser user, final long roleID, final IGuild guild) {
        final List<IRole> roles = user.getRolesForGuild(guild);

        for (IRole role : roles) {
            if (roleID == role.getLongID()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create a String that can be used to identify a user on a guild.
     * If the user does not have a nickname for that guild, returns the username followed by the users discriminator.
     * (e.g. {@code Nikos#0410})
     *
     * If the user does have a nickname, that nickname will be included. (e.g. {@code "Sokin" (Nikos#0410) })
     *
     * @param user The user for which to create the String.
     * @param guild The guild on which to look for a nickname of that user.
     * @return The generated String.
     */
    public static String makeUserString(final IUser user, final IGuild guild) {
        final String name = user.getName();
        final String discriminator = user.getDiscriminator();
        final String displayName = user.getDisplayName(guild);

        if (name.equals(displayName)) {
            return String.format("%s#%s", name, discriminator);
        }
        else {
            return String.format("\"%s\" (%s#%s)", displayName, name, discriminator);
        }

    }
}
