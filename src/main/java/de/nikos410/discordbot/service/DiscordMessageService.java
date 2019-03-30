package de.nikos410.discordbot.service;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;

import java.io.File;
import java.util.List;

public interface DiscordMessageService {

    /**
     * Send a list of lines to the specified channel. If the message gets too long (&gt;2000 characters)
     * it will be split into multiple messages. The individual lines will not be split. Attachments will be added to the last message.
     *
     * Will try 20 times, once every 0.5 seconds, if the bot gets rate limited.
     *
     * @param channel the channel in which the message(s) will be sent
     * @param lines the lines to send
     * @param attachments Attachments to add to the message
     * @return a list containing the sent message(s).
     */
    List<IMessage> sendMessage(IChannel channel, List<String> lines, File... attachments);

    /**
     * Send a string to a channel. If the string is too long (&gt;2000 characters), it will be split into
     * multiple messages. Will split in the middle of lines or words. Attachments will be added to the last message.
     *
     * Will try 20 times, once every 0.5 seconds, if the bot gets rate limited.
     *
     * @param channel the channel in which the message(s) will be sent
     * @param message the content of the message
     * @param attachments Attachments to add to the message
     * @return a list containing the sent message(s).
     */
    List<IMessage> sendMessage(IChannel channel, String message, File... attachments);

    /**
     * Send an embed to a channel.
     *
     * Will try 20 times, once every 0.5 seconds, if the bot gets rate limited.
     *
     * @param channel The channel in which the message(s) will be sent
     * @param embedObject The embed to send
     * @param attachments Attachments to add to the message
     * @return A list containing the sent message(s).
     */
    IMessage sendEmbed(IChannel channel, EmbedObject embedObject, File... attachments);

    /**
     * Send a notification about an error to a channel.
     *
     * @param errorMessage The message that will be included in the notification.
     * @param channel The channel in which to send the notification.
     */
    IMessage errorNotify(final String errorMessage, final IChannel channel);
}
