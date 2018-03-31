package de.nikos410.discordBot.util.general;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.*;

public class Util {

    private static Logger log = LoggerFactory.getLogger(Util.class);

    /**
     * Eine Nachricht senden. Wenn die Nachricht zu lang (>2000 Zeichen) ist, wird sie in mehrere kürzere
     * Nachrichten aufgeteilt.
     *
     * @param channel der Kanal in dem die Nachricht gesendet werden soll
     * @param lines die Zeilen der Nachricht
     * @return Die gesendete(n) Nachricht(en)
     */
    public static synchronized List<IMessage> sendMessage(final IChannel channel, final List<String> lines) {
        final List<IMessage> sentMessages = new ArrayList<>();

        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() + line.length() <= 2000) {
                // Zeile passt
                builder.append(line);
            }
            else {
                // Zeile passt nicht
                sentMessages.add(sendSingleMessage(channel, builder.toString()));
                builder = new StringBuilder();
            }
        }

        return sentMessages;
    }

    /**
     * Eine Nachricht senden. Wenn die Nachricht zu lang (>2000 Zeichen) ist, wird sie in mehrere kürzere
     * Nachrichten umgebrochen
     *
     * @param channel der Kanal in dem die Nachricht gesendet werden soll
     * @param message der Inhalt der Nachricht
     * @return Die gesendete(n) Nachricht(en)
     */
    public static synchronized List<IMessage> sendMessage(final IChannel channel, final String message) {
        if (message.length() <= 2000 ) {
            // Nachricht ist maximal 2000 Zeichen lang
            final List<IMessage> sentMessages = new ArrayList<>();
            sentMessages.add(sendSingleMessage(channel, message));
            return sentMessages;
        }
        else {
            // Nachricht ist länger als 2000 Zeichen -> umbrechen und aufteilen
            final List<IMessage> sentMessages = new ArrayList<>();
            sentMessages.add(sendSingleMessage(channel, message.substring(0,1999)));
            sentMessages.addAll(sendMessage(channel, message.substring(2000)));
            return sentMessages;
        }
    }

    private static synchronized IMessage sendSingleMessage(final IChannel channel, final String message){
        return sendSingleMessage(channel, message, 0);
    }

    private static synchronized IMessage sendSingleMessage(final IChannel channel, final String message, final int tries){
        try {
            return channel.sendMessage(message);
        }
        catch (RateLimitException e) {
            // 20 Versuche im Abstand von 0,5 Sekunden
            if (tries < 20) {
                sleep(500);
                sendSingleMessage(channel, message, tries+1);
            }
            else {
                log.warn("Bot was ratelimited while trying to send message. (20 tries)", e);
            }
        }
        catch (DiscordException e) {
            log.error("Message could not be sent.", e);
        }

        return null;
    }

    public static synchronized IMessage sendEmbed(final IChannel channel, final EmbedObject embedObject) {
        return sendEmbed(channel, embedObject, 0);
    }

    public static IMessage sendEmbed(final IChannel channel, final EmbedObject embedObject, final int tries) {
        try {
            return channel.sendMessage(embedObject);
        }
        catch (RateLimitException e) {
            // 20 Versuche im Abstand von 0,5 Sekunden
            if (tries < 20) {
                sleep(500);
                sendEmbed(channel, embedObject, tries+1);
            }
            else {
                log.warn("Bot was ratelimited while trying to send embed. (20 tries)", e);
            }
        }
        catch (DiscordException e) {
            log.error("Embed could not be sent.", e);
        }

        return null;
    }

    private static void sleep (int milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
        }
        catch (InterruptedException e) {
            log.error("Sleep was interrupted.", e);
        }
    }

    public static synchronized void sendPM(final IUser user, final String message) {
        final IPrivateChannel channel = user.getOrCreatePMChannel();
        sendMessage(channel, message);
    }

    @Deprecated
    public static String getContext(final String message) {
        return getContext(message, 1);
    }

    @Deprecated
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
        return hasRoleByID(user, role.getLongID(), guild);
    }

    public static boolean hasRoleByID(final IUser user, final long roleID, final IGuild guild) {
        final List<IRole> roles = user.getRolesForGuild(guild);

        for (IRole role : roles) {
            if (roleID == role.getLongID()) {
                return true;
            }
        }
        return false;
    }

    public static String readFile(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
        catch (IOException | NullPointerException e){
            log.error(String.format("Could not read file from Path \"%s\"", path), e);
            return null;
        }
    }

    public static Path writeToFile(Path path, String text) {
        try {
            return Files.write(path, text.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e) {
            log.error(String.format("Could not write to Path \"%s\"", path), e);
            return null;
        }
    }

    public static void errorNotify(final Exception e, final IChannel channel) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(new Color(255, 42, 50));
        embedBuilder.appendField("Fehler aufgetreten", e.toString(), false);
        embedBuilder.withFooterText("Mehr Infos in der Konsole");

        sendEmbed(channel, embedBuilder.build());
    }

    public static void errorNotify(final String s, final IChannel channel) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(new Color(255, 42, 50));
        embedBuilder.appendField("Fehler aufgetreten", s, false);
        embedBuilder.withFooterText("Mehr Infos in der Konsole");

        sendEmbed(channel, embedBuilder.build());
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