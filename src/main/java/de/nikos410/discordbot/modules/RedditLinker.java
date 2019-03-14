package de.nikos410.discordbot.modules;

import de.nikos410.discordbot.framework.CommandModule;
import de.nikos410.discordbot.util.discord.DiscordIO;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RedditLinker extends CommandModule {

    @Override
    public String getDisplayName() {
        return "Reddit-Linker";
    }

    @Override
    public String getDescription() {
        return "Verlinkt Subreddits die erwähnt werden.";
    }

    @Override
    public boolean hasEvents() {
        return true;
    }

    @EventSubscriber
    public void onMessageReceived(final MessageReceivedEvent event) {
        final IMessage message = event.getMessage();
        final String messageContent = message.getContent();

        Pattern pattern = Pattern.compile("^/?r/(\\w+)$");
        Matcher matcher = pattern.matcher(messageContent);


        if (matcher.matches()) {
            DiscordIO.sendMessage(message.getChannel(), "https://www.reddit.com/r/" + matcher.group(1));
        }
    }
}