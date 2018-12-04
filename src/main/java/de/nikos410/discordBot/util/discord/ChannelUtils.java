package de.nikos410.discordBot.util.discord;

import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;

import java.util.List;

public class ChannelUtils {

    /**
     * Get the channel that was specified in the given message. First, checks if the parameter contains a valid channel ID
     * and returns the corresponding channel. Second, checks if the message contains exactly one channel Mention and returns
     * the mentioned channel.
     *
     * @param message The message that should be checked for mentions.
     * @param channelParameter The parameter that should be checked for a valid channel ID.
     * @return The channel that was specified, or null if none was found.
     */
    public static IChannel getChannelFromMessage(final IMessage message, final String channelParameter) {
        if (message == null || channelParameter == null) {
            return null;
        }

        if (channelParameter.matches("^[0-9]{18}$")) {
            // The channel parameter looks like a channel ID
            // Try to find a channel with that ID
            final IChannel channel = message.getGuild().getChannelByID(Long.parseLong(channelParameter));
            if (channel != null) {
                // A channel was found
                return channel;
            }
        }

        final List<IChannel> mentions = message.getChannelMentions();
        if (mentions.size() == 1) {
            // Exactly one mention found
            return mentions.get(0);
        }
        else {
            // Too many or no mentions found
            return null;
        }
    }
}
