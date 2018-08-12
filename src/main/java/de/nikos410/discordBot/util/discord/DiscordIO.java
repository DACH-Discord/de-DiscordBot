package de.nikos410.discordBot.util.discord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RateLimitException;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DiscordIO {

    private final static Logger LOG = LoggerFactory.getLogger(DiscordIO.class);

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
        catch (RateLimitException rle) {
            // 20 Versuche im Abstand von 0,5 Sekunden
            if (tries < 20) {
                // 500ms warten
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                }
                catch (InterruptedException ie) {
                    LOG.error("Sleep was interrupted.", ie);
                }
                finally {
                    sendSingleMessage(channel, message, tries+1);
                }
            }
            else {
                LOG.warn("Bot was ratelimited while trying to send message. (20 tries)", rle);
            }
        }
        catch (DiscordException de) {
            LOG.error("Message could not be sent.", de);
        }

        return null;
    }

    public static synchronized IMessage sendEmbed(final IChannel channel, final EmbedObject embedObject) {
        return sendEmbed(channel, embedObject, 0);
    }

    private static IMessage sendEmbed(final IChannel channel, final EmbedObject embedObject, final int tries) {
        try {
            return channel.sendMessage(embedObject);
        }
        catch (RateLimitException rle) {
            // 20 Versuche im Abstand von 0,5 Sekunden
            if (tries < 20) {
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                }
                catch (InterruptedException ie) {
                    LOG.error("Sleep was interrupted.", ie);
                }
                finally {
                    sendEmbed(channel, embedObject, tries + 1);
                }
            }
            else {
                LOG.warn("Bot was ratelimited while trying to send embed. (20 tries)", rle);
            }
        }
        catch (DiscordException de) {
            LOG.error("Embed could not be sent.", de);
        }

        return null;
    }

    public static void errorNotify(final Exception e, final IChannel channel) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(new Color(255, 42, 50));
        embedBuilder.appendField("Interner Fehler", e.toString(), false);
        embedBuilder.withFooterText("Mehr Infos in der Konsole");

        sendEmbed(channel, embedBuilder.build());
    }

    public static void errorNotify(final String errorMessage, final IChannel channel) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(new Color(255, 42, 50));
        embedBuilder.appendField("Interner Fehler", errorMessage, false);
        embedBuilder.withFooterText("Mehr Infos in der Konsole");

        sendEmbed(channel, embedBuilder.build());
    }
}
