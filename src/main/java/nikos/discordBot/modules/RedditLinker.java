package nikos.discordBot.modules;

import nikos.discordBot.util.Util;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;

import java.nio.file.Path;
import java.nio.file.Paths;

public class RedditLinker {
    private final static Path LOGGER_PATH = Paths.get("data/logger/");

    private static IDiscordClient client;

    public RedditLinker(IDiscordClient dClient) {
        client = dClient;
    }

    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) {
        final IMessage message = event.getMessage();
        final String messageContent = message.getContent();

        if (messageContent.matches ("^/r/(\\w+)$") ) {
            Util.sendMessage(message.getChannel(), "https://www.reddit.com" + messageContent);
        }
    }
}
