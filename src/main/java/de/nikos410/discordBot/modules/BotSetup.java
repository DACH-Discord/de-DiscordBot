package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.annotations.AlwaysLoaded;
import de.nikos410.discordBot.util.modular.annotations.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.annotations.CommandSubscriber;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.RateLimitException;

import java.util.concurrent.TimeUnit;

@CommandModule(moduleName = "Bot-Setup", commandOnly = true)
@AlwaysLoaded
public class BotSetup {
    private final DiscordBot bot;
    private final IDiscordClient client;

    public BotSetup (final DiscordBot bot) {
        this.bot = bot;
        this.client = bot.client;
    }

    @CommandSubscriber(command = "shutdown", help = "Schaltet den Bot aus", pmAllowed = true, permissionLevel = CommandPermissions.OWNER)
    public void command_Shutdown(final IMessage message) {
        Util.sendMessage(message.getChannel(), "Ausschalten... :zzz:");
        System.out.println("[INFO] Shutting down...");
        
        try {
            this.client.logout();

            while (this.client.isLoggedIn()) {
                TimeUnit.SECONDS.sleep(1);
            }

            System.exit(0);
        }
        catch (InterruptedException e) {
            Util.error(e, message.getChannel());
        }
    }

    @CommandSubscriber(command = "setbotname", help = "Nutzernamen des Bots ändern", pmAllowed = true, permissionLevel = CommandPermissions.OWNER)
    public void command_SetUsername(final IMessage message) {
        final String newUserName = Util.getContext(message.getContent());

        try {
            this.client.changeUsername(newUserName);
            Util.sendMessage(message.getChannel(), ":white_check_mark: Neuer Username gesetzt: `" + newUserName + "`");
        }
        catch (RateLimitException e) {
            Util.error(e, message.getChannel());
        }
    }

    @CommandSubscriber(command = "modules", help = "Alle Module anzeigen", pmAllowed = true, permissionLevel = CommandPermissions.EVERYONE)
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
