package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.AlwaysLoaded;
import de.nikos410.discordBot.util.modular.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.CommandSubscriber;

import sx.blah.discord.handle.obj.IMessage;

@CommandModule(moduleName = "Bot-Setup", commandOnly = true)
@AlwaysLoaded
public class BotSetup {
    private final DiscordBot bot;

    public BotSetup (final DiscordBot bot) {
        this.bot = bot;
    }

    @CommandSubscriber(command = "shutdown", help = "Schaltet den Bot aus", pmAllowed = true, permissionLevel = CommandPermissions.OWNER)
    public void command_Shutdown(final IMessage message) {
        Util.sendMessage(message.getChannel(), "Ausschalten... :zzz:");
        System.out.println("[INFO] Shutting down...");


        try {
            this.bot.shutdown();
        }
        catch (InterruptedException e) {
            Util.error(e, message.getChannel());
        }
    }

    @CommandSubscriber(command = "modules", help = "Alle Module anzeigen", pmAllowed = true, permissionLevel = CommandPermissions.ADMIN)
    public void command_ListModules(final IMessage message) {
        bot.listModules(message.getChannel());
    }

    @CommandSubscriber(command = "loadmodule", help = "Ein Modul aktivieren", pmAllowed = true, permissionLevel = CommandPermissions.ADMIN)
    public void command_LoadModule(final IMessage message) {
        final String messageContext = Util.getContext(message.getContent());
        this.bot.loadModule(messageContext, message.getChannel());
    }

    @CommandSubscriber(command = "unloadmodule", help = "Ein Modul deaktivieren", pmAllowed = true, permissionLevel = CommandPermissions.ADMIN)
    public void command_UnloadModule(final IMessage message) {
        final String messageContext = Util.getContext(message.getContent());
        this.bot.unloadModule(messageContext, message.getChannel());
    }
}
