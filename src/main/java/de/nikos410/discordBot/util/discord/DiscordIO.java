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
     * Send a list of lines to the specified channel. If the message gets too long (>2000 characters)
     * it will be split into multiple messages. The individual lines will not be split.
     *
     * @param channel the channel in which the message(s) will be sent
     * @param lines the lines to send
     * @return a list containing the sent message(s).
     */
    public static synchronized List<IMessage> sendMessage(final IChannel channel, final List<String> lines) {
        final List<IMessage> sentMessages = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {

            if (builder.length() + line.length() > 2000) {
                // The message will be too long if we add the current line
                sentMessages.add(sendSingleMessage(channel, builder.toString()));
                builder = new StringBuilder();
            }

            // Add a line break if the builder already contains a line
            if (builder.length() > 0) {
                builder.append('\n');
            }

            builder.append(line);
        }

        return sentMessages;
    }

    /**
     * Send a string to a channel. If the string is too long (>2000 characters), it will be split into
     * multiple messages. Will split in the middle of lines or words.
     *
     * @param channel the channel in which the message(s) will be sent
     * @param message the content of the message
     * @return a list containing the sent message(s).
     */
    public static synchronized List<IMessage> sendMessage(final IChannel channel, final String message) {
        if (message.length() <= 2000 ) {
            // Content fits into a single message
            final List<IMessage> sentMessages = new ArrayList<>();
            sentMessages.add(sendSingleMessage(channel, message));
            return sentMessages;
        }
        else {
            // Content does not fit into a single message -> split
            final List<IMessage> sentMessages = new ArrayList<>();
            sentMessages.add(sendSingleMessage(channel, message.substring(0,1999)));
            sentMessages.addAll(sendMessage(channel, message.substring(1999)));
            return sentMessages;
        }
    }

    private static synchronized IMessage sendSingleMessage(final IChannel channel, final String message){
        return sendSingleMessage(channel, message, 20);
    }

    private static synchronized IMessage sendSingleMessage(final IChannel channel, final String message, final int tries){
        try {
            return channel.sendMessage(message);
        }
        catch (RateLimitException rle) {
            LOG.info("Ratelimited");
            // 20 Versuche im Abstand von 0,5 Sekunden
            if (tries > 0) {
                LOG.info("waiting");
                // 500ms warten
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                }
                catch (InterruptedException ie) {
                    LOG.error("Sleep was interrupted.", ie);
                }

                return sendSingleMessage(channel, message, tries - 1);
            }
            else {
                LOG.warn("Bot was ratelimited while trying to send message.", rle);
            }
        }
        catch (DiscordException de) {
            LOG.error("Message could not be sent.", de);
        }

        return null;
    }

    public static synchronized IMessage sendEmbed(final IChannel channel, final EmbedObject embedObject) {
        return sendEmbed(channel, embedObject, 20);
    }

    private static IMessage sendEmbed(final IChannel channel, final EmbedObject embedObject, final int tries) {
        try {
            return channel.sendMessage(embedObject);
        }
        catch (RateLimitException rle) {
            // 20 Versuche im Abstand von 0,5 Sekunden
            if (tries > 0) {
                // 500ms warten
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                }
                catch (InterruptedException ie) {
                    LOG.error("Sleep was interrupted.", ie);
                }

                return sendEmbed(channel, embedObject, tries - 1);
            }
            else {
                LOG.warn("Bot was ratelimited while trying to send embed.", rle);
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
