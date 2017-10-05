package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.CommandSubscriber;

import sx.blah.discord.handle.obj.IMessage;

@CommandModule(moduleName = "Allgemeine Befehle", commandOnly = true)
public class GeneralCommands {
    private final DiscordBot bot;

    public GeneralCommands (final DiscordBot bot) {
        this.bot = bot;
    }

    @CommandSubscriber(command = "Ping", help = ":ping_pong:", pmAllowed = true, permissionLevel = CommandPermissions.MODERATOR)
    public void command_Ping(final IMessage message) {
        Util.sendMessage(message.getChannel(), "pong");
    }
}
