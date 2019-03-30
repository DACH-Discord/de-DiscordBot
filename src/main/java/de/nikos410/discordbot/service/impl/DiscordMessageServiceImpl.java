package de.nikos410.discordbot.service.impl;

import de.nikos410.discordbot.service.DiscordMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RateLimitException;

import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DiscordMessageServiceImpl implements DiscordMessageService {
    private static final Logger LOG = LoggerFactory.getLogger(DiscordMessageServiceImpl.class);

    @Override
    public synchronized List<IMessage> sendMessage(IChannel channel, List<String> lines, File... attachments) {
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
        sentMessages.add(sendSingleMessage(channel, builder.toString(), attachments));


        return sentMessages;
    }

    @Override
    public synchronized List<IMessage> sendMessage(IChannel channel, String message, File... attachments) {
        if (message.length() <= 2000 ) {
            // Content fits into a single message
            final List<IMessage> sentMessages = new ArrayList<>();
            sentMessages.add(sendSingleMessage(channel, message, attachments));
            return sentMessages;
        }
        else {
            // Content does not fit into a single message -> split
            final List<IMessage> sentMessages = new ArrayList<>();
            sentMessages.add(sendSingleMessage(channel, message.substring(0,1999)));
            sentMessages.addAll(sendMessage(channel, message.substring(1999), attachments));
            return sentMessages;
        }
    }

    private synchronized IMessage sendSingleMessage(final IChannel channel, final String message, final File... attachments){
        return sendSingleMessage(channel, message, attachments, 20);
    }

    private synchronized IMessage sendSingleMessage(final IChannel channel, final String message, final File[] attachments, final int tries){
        try {
            if (attachments == null || attachments.length == 0) {
                return channel.sendMessage(sanitizeMessage(message));
            }
            else {
                return channel.sendFiles(sanitizeMessage(message), attachments);
            }
        }
        catch (FileNotFoundException e) {
            this.errorNotify("Attachment file not found: " + e.getMessage(), channel);
        }
        catch (RateLimitException rle) {
            if (tries > 0) {
                // Try again after 500ms
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                }
                catch (InterruptedException ie) {
                    LOG.warn("Sleep was interrupted.", ie);
                    // Restore interrupted state
                    Thread.currentThread().interrupt();
                }

                return sendSingleMessage(channel, message, attachments, tries - 1);
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

    @Override
    public synchronized IMessage sendEmbed(IChannel channel, EmbedObject embedObject, File... attachments) {
        return sendEmbed(channel, embedObject, attachments, 20);
    }

    private synchronized IMessage sendEmbed(final IChannel channel, final EmbedObject embedObject, final File[] attachments, final int tries) {
        try {
            if (attachments == null || attachments.length == 0) {
                return channel.sendMessage(embedObject);
            }
            else {
                return channel.sendFiles(embedObject, attachments);
            }
        }
        catch (FileNotFoundException e) {
            this.errorNotify("Attachment file not found: " + e.getMessage(), channel);
        }
        catch (RateLimitException rle) {
            if (tries > 0) {
                // Try again after 500ms
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                }
                catch (InterruptedException ie) {
                    LOG.warn("Sleep was interrupted.", ie);
                    // Restore interrupted state
                    Thread.currentThread().interrupt();
                }

                return sendEmbed(channel, embedObject, attachments, tries - 1);
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

    @Override
    public IMessage errorNotify(String errorMessage, IChannel channel) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(new Color(255, 42, 50));
        embedBuilder.appendField("Interner Fehler", errorMessage, false);
        embedBuilder.withFooterText("Mehr Infos in der Konsole");

        return sendEmbed(channel, embedBuilder.build());
    }

    /**
     * Sanitize a message by replacing "@everyone" with "(at)everyone"
     *
     * @param message The message to be sanitized
     * @return The sanitized message
     */
    public static String sanitizeMessage(final String message) {
        return message
                .replaceAll("@everyone", "@\\u{200B}everyone")
                .replaceAll("@here", "@\\u{200B}here");
    }
}
