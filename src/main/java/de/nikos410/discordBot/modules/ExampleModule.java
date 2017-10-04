package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.CommandSubscriber;
import sx.blah.discord.handle.obj.IMessage;

@CommandModule(moduleName = "Example Module", commandOnly = true)
public class ExampleModule {

    @CommandSubscriber(command = "Ping", help = ":ping_pong:", pmAllowed = true, permissionLevel = CommandPermissions.MODERATOR)
    public void command_Ping(final IMessage message) throws Exception {
        Util.sendMessage(message.getChannel(), "pong");
    }
}
