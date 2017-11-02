package de.nikos410.discordBot.util.general;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.*;

public class Util {

    /*
    RequestBuffer.request(() -> {
            // Stuff
        });
     */

    public static synchronized void sendMessage(final IChannel channel, final String message) {
        if (message.length() <= 2000 ) {
            try {
                channel.sendMessage(message);
            } catch (RateLimitException e) {
                System.err.println("[ERR] Ratelimited!");
            } catch (MissingPermissionsException e) {
                System.err.println("[ERR] Missing Permissions");
            } catch (DiscordException e) {
                error(e, channel);
            }
        }
        else {
            sendMessage(channel, message.substring(0,1999));
            sendMessage(channel, message.substring(2000));
        }
    }

    public static synchronized IMessage sendSingleMessage(final IChannel channel, final String message) {
        try {
            return channel.sendMessage(message);
        } catch (RateLimitException e) {
            System.err.println("[ERR] Ratelimited!");
        } catch (MissingPermissionsException e) {
            System.err.println("[ERR] Missing Permissions");
        } catch (DiscordException e) {
            error(e, channel);
        }
        return null;
    }

    public static void sendBufferedEmbed(final IChannel channel, final EmbedObject embedObject) {
        try {
            RequestBuffer.request(() -> {
                channel.sendMessage(embedObject);
            });
        } catch (RateLimitException e) {
            System.err.println("[ERR] Ratelimited!");
        } catch (MissingPermissionsException e) {
            System.err.println("[ERR] Missing Permissions");
        } catch (DiscordException e) {
            error(e, channel);
        }
    }

    public static synchronized void sendPM(final IUser user, final String message) {
        final IPrivateChannel channel = user.getOrCreatePMChannel();
        sendPM(channel, message);
    }

    private static synchronized void sendPM(final IPrivateChannel channel, final String message) {
        if (message.length() <= 2000 ) {
            try {
                channel.sendMessage(message);
            } catch (RateLimitException e) {
                System.err.println("[ERR] Ratelimited!");
            } catch (MissingPermissionsException e) {
                System.err.println("[ERR] Missing Permissions");
            } catch (DiscordException e) {
                error(e, channel);
            }
        }
        else {
            sendPM(channel, message.substring(0,1999));
            sendPM(channel, message.substring(2000));
        }
    }

    public static String getContext(final String message) {
        return getContext(message, 1);
    }

    public static String getContext(final String message, final int level) {
        if (level == 1) {
            if (message.contains(" ")) {
                return message.substring(message.indexOf(' ') + 1);
            }
            else {
                return "";
            }
        }
        else {
            return getContext(getContext(message, level-1));
        }
    }

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
        return hasRoleByID(user, role.getStringID(), guild);
    }

    public static boolean hasRoleByID(final IUser user, final String roleID, final IGuild guild) {
        final List<IRole> roles = user.getRolesForGuild(guild);

        for (IRole role : roles) {
            if (roleID.equals(role.getStringID())) {
                return true;
            }
        }
        return false;
    }

    public static String readFile(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
        catch (IOException e){
            error(e);
            return null;
        }
    }

    public static Path writeToFile(Path file, String text) {
        try {
            return Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e) {
            error(e);
            return null;
        }
    }

    public static void error(final Exception e, final IChannel channel) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(new Color(255, 42, 50));
        embedBuilder.appendField("Fehler aufgetreten", e.toString(), false);
        embedBuilder.withFooterText("Mehr Infos in der Konsole");

        sendBufferedEmbed(channel, embedBuilder.build());

        error(e);
    }

    public static void error(final Exception e) {
        System.err.println(e.toString() + '\n');
        e.printStackTrace(System.err);
    }
}