package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.annotations.AlwaysLoaded;
import de.nikos410.discordBot.util.modular.annotations.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.annotations.CommandSubscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RateLimitException;

import java.util.concurrent.TimeUnit;

@CommandModule(moduleName = "Bot-Setup", commandOnly = true)
@AlwaysLoaded
public class BotSetup {
    private final DiscordBot bot;
    private final IDiscordClient client;

    private Logger log = LoggerFactory.getLogger(DiscordBot.class);


    public BotSetup (final DiscordBot bot) {
        this.bot = bot;
        this.client = bot.client;
    }

    @CommandSubscriber(command = "shutdown", help = "Schaltet den Bot aus", permissionLevel = CommandPermissions.OWNER)
    public void command_Shutdown(final IMessage message) {
        Util.sendMessage(message.getChannel(), "Ausschalten... :zzz:");
        log.info("Shutting down.");
        
        try {
            this.client.logout();

            while (this.client.isLoggedIn()) {
                TimeUnit.SECONDS.sleep(1);
            }

            System.exit(0);
        }
        catch (InterruptedException e) {
            Util.errorNotify(e, message.getChannel());
        }
    }

    @CommandSubscriber(command = "setbotname", help = "Nutzernamen des Bots ändern", permissionLevel = CommandPermissions.OWNER)
    public void command_SetUsername(final IMessage message, final String newUserName) {
        try {
            this.client.changeUsername(newUserName);
            Util.sendMessage(message.getChannel(), String.format(":white_check_mark: Neuer Username gesetzt: `%s`", newUserName));
            log.info(String.format("%s changed the bots username to %s.", Util.makeUserString(message.getAuthor(), message.getGuild()), newUserName));
        }
        catch (RateLimitException e) {
            Util.errorNotify(e, message.getChannel());
            log.warn("Bot was ratelimited while trying to change its username.");
        }
    }

    @CommandSubscriber(command = "modules", help = "Alle Module anzeigen")
    public void command_ListModules(final IMessage message) {

        StringBuilder loadedBuilder = new StringBuilder();
        for (final String key : bot.getLoadedModules().keySet()) {
            loadedBuilder.append(key);
            loadedBuilder.append('\n');
        }
        final String loadedModulesString = loadedBuilder.toString().isEmpty() ? "_keine_" : loadedBuilder.toString();

        StringBuilder unloadedBuilder = new StringBuilder();
        for (final String key : bot.getUnloadedModules().keySet()) {
            unloadedBuilder.append(key);
            unloadedBuilder.append('\n');
        }
        final String unloadedModulesString = unloadedBuilder.toString().isEmpty() ? "_keine_" : unloadedBuilder.toString();

        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.appendField("Aktivierte Module", loadedModulesString, true);
        embedBuilder.appendField("Deaktivierte Module", unloadedModulesString, true);

        Util.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    @CommandSubscriber(command = "loadmodule", help = "Ein Modul aktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_LoadModule(final IMessage message, final String moduleName) {
        try {
            String msg = bot.loadModule(moduleName);
            Util.sendMessage(message.getChannel(), msg);
        }
        catch (NullPointerException e) {
            Util.errorNotify(e, message.getChannel());
            log.error(String.format("Something went wrong while activating module \"%s\"", moduleName));
        }
    }

    @CommandSubscriber(command = "unloadmodule", help = "Ein Modul deaktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_UnloadModule(final IMessage message, final String moduleName) {
        try {
            String msg = bot.unloadModule(moduleName);
            Util.sendMessage(message.getChannel(), msg);
        }
        catch (NullPointerException e) {
            Util.errorNotify(e, message.getChannel());
            log.error(String.format("Something went wrong while deactivating module \"%s\"", moduleName));
        }
    }

}
