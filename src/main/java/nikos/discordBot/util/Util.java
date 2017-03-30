package nikos.discordBot.util;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;
import sx.blah.discord.util.RequestBuffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class Util {
    /*
    RequestBuffer.request(() -> {
            // Stuff
        });
     */

    public static synchronized void sendMessage(final IChannel channel, final String message) {
        try {
            channel.sendMessage(message);
        } catch (RateLimitException e) {
            System.err.println("[Error] Ratelimited!");
        } catch (MissingPermissionsException e) {
            System.err.println("[Error] Missing Permissions");
        } catch (DiscordException e) {
            System.err.println("[Error] " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static synchronized void sendEmbed(final IChannel channel, final EmbedObject embedObject) {
        try {
            channel.sendMessage(embedObject);
        } catch (RateLimitException e) {
            System.err.println("[Error] Ratelimited!");
        } catch (MissingPermissionsException e) {
            System.err.println("[Error] Missing Permissions");
        } catch (DiscordException e) {
            System.err.println("[Error] " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static synchronized void sendPM(final IUser user, final String message) {
        try {
            final IPrivateChannel channel = user.getOrCreatePMChannel();
            channel.sendMessage(message);
        } catch (RateLimitException e) {
            System.err.println("[Error] Ratelimited!");
        } catch (MissingPermissionsException e) {
            System.err.println("[Error] Missing Permissions");
        } catch (DiscordException e) {
            System.err.println("[Error] " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getContext(final String message) {
        if (message.contains(" ")) {
            return message.substring(message.indexOf(' ') + 1);
        }
        else {
            return "";
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

    public static String readFile(Path path) {
        try {
            return new String(Files.readAllBytes(path));
        }
        catch (IOException e){
            System.err.println("[Error] " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static void writeToFile(Path file, String text) {
        try {
            Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e) {
            System.err.println("[Error] " + e.getMessage());
            e.printStackTrace();
        }

    }
}