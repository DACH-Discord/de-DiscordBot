package nikos.discordBot.modules;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage;

public class WapBapRemover {
    public WapBapRemover(IDiscordClient dClient) {

    }

    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) {
        final IMessage message = event.getMessage();
        final String messageContent = message.getContent();

        if (messageContent.contains("youtu.be/4gSOMba1UdM") ||
                messageContent.contains("youtube.com/watch?v=4gSOMba1UdM") ) {
            message.delete();
        }
    }
}
