package de.nikos410.discordBot.modules;

import java.util.concurrent.TimeUnit;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.discord.DiscordIO;
import de.nikos410.discordBot.util.discord.GuildOperations;
import de.nikos410.discordBot.util.discord.UserOperations;
import de.nikos410.discordBot.modular.annotations.AlwaysLoaded;
import de.nikos410.discordBot.modular.annotations.CommandModule;
import de.nikos410.discordBot.modular.CommandPermissions;
import de.nikos410.discordBot.modular.annotations.CommandSubscriber;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.Permissions;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RateLimitException;

@CommandModule(moduleName = "Bot-Setup", commandOnly = true)
@AlwaysLoaded
public class BotSetup {
    private final DiscordBot bot;
    private final IDiscordClient client;

    private final static Logger LOG = LoggerFactory.getLogger(DiscordBot.class);


    public BotSetup (final DiscordBot bot) {
        this.bot = bot;
        this.client = bot.client;
    }

    @CommandSubscriber(command = "setupRoles", help = "Moderator- und Admin-Rolle setzen",
            pmAllowed = false, passContext = false)
    public void command_setupRoles(final IMessage message, final String modRoleID, final String adminRoleID) {
        // Check if user is allowed to use this command
        final IRole userTopRole = UserOperations.getTopRole(message.getAuthor(), message.getGuild());
        if (!userTopRole.getPermissions().contains(Permissions.MANAGE_SERVER) && !userTopRole.getPermissions().contains(Permissions.ADMINISTRATOR)) {
            DiscordIO.sendMessage(message.getChannel(), "Du benötigst die permission \"Server Verwalten\" oder \"Administrator\" um diesen Befehl zu benutzen");
            return;
        }

        // Check if parameters are valid
        if (!modRoleID.matches("^[0-9]{18}$") || !adminRoleID.matches("^[0-9]{18}$")) {
            DiscordIO.sendMessage(message.getChannel(), "Ungültige Eingabe! Syntax: `setupRoles <modID> <adminID>`");
            return;
        }

        // Check if IDs are valid
        if (!GuildOperations.hasRoleByID(message.getGuild(), Long.parseLong(modRoleID))) {
            DiscordIO.sendMessage(message.getChannel(), String.format("Anscheinend existiert keine Rolle mit der ID `%s` auf diesem Server.", modRoleID));
            return;
        }
        if (!GuildOperations.hasRoleByID(message.getGuild(), Long.parseLong(adminRoleID))) {
            DiscordIO.sendMessage(message.getChannel(), String.format("Anscheinend existiert keine Rolle mit der ID `%s` auf diesem Server.", adminRoleID));
            return;
        }

        final JSONObject rolesJSON = bot.rolesJSON;
        final String guildID = message.getGuild().getStringID();

        final JSONObject serverRoles;
        if (rolesJSON.has(guildID)) {
            serverRoles = rolesJSON.getJSONObject(guildID);
        }
        else {
            serverRoles = new JSONObject();
        }
        serverRoles.put("modRole", Long.parseLong(modRoleID));
        serverRoles.put("adminRole", Long.parseLong(adminRoleID));

        bot.saveRoles();

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:

        LOG.info("Updated mod and admin roles for ");
    }

    @CommandSubscriber(command = "shutdown", help = "Schaltet den Bot aus", permissionLevel = CommandPermissions.OWNER)
    public void command_Shutdown(final IMessage message) {
        DiscordIO.sendMessage(message.getChannel(), "Ausschalten... :zzz:");
        LOG.info("Shutting down.");
        
        try {
            this.client.logout();

            while (this.client.isLoggedIn()) {
                TimeUnit.SECONDS.sleep(1);
            }

            System.exit(0);
        }
        catch (InterruptedException e) {
            DiscordIO.errorNotify(e, message.getChannel());
        }
    }

    @CommandSubscriber(command = "setbotname", help = "Nutzernamen des Bots ändern", permissionLevel = CommandPermissions.OWNER)
    public void command_SetUsername(final IMessage message, final String newUserName) {
        try {
            this.client.changeUsername(newUserName);
            DiscordIO.sendMessage(message.getChannel(), String.format(":white_check_mark: Neuer Username gesetzt: `%s`", newUserName));
            LOG.info(String.format("%s changed the bots username to %s.", UserOperations.makeUserString(message.getAuthor(), message.getGuild()), newUserName));
        }
        catch (RateLimitException e) {
            DiscordIO.errorNotify(e, message.getChannel());
            LOG.warn("Bot was ratelimited while trying to change its username.");
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
        for (final String key : bot.getUnloadedModules()) {
            unloadedBuilder.append(key);
            unloadedBuilder.append('\n');
        }
        final String unloadedModulesString = unloadedBuilder.toString().isEmpty() ? "_keine_" : unloadedBuilder.toString();

        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.appendField("Aktivierte Module", loadedModulesString, true);
        embedBuilder.appendField("Deaktivierte Module", unloadedModulesString, true);

        DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    @CommandSubscriber(command = "loadmodule", help = "Ein Modul aktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_LoadModule(final IMessage message, final String moduleName) {
        try {
            String msg = bot.loadModule(moduleName);
            DiscordIO.sendMessage(message.getChannel(), msg);
        }
        catch (NullPointerException e) {
            DiscordIO.errorNotify(e, message.getChannel());
            LOG.error(String.format("Something went wrong while activating module \"%s\"", moduleName));
        }
    }

    @CommandSubscriber(command = "unloadmodule", help = "Ein Modul deaktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_UnloadModule(final IMessage message, final String moduleName) {
        try {
            String msg = bot.unloadModule(moduleName);
            DiscordIO.sendMessage(message.getChannel(), msg);
        }
        catch (NullPointerException e) {
            DiscordIO.errorNotify(e, message.getChannel());
            LOG.error(String.format("Something went wrong while deactivating module \"%s\"", moduleName));
        }
    }

}
