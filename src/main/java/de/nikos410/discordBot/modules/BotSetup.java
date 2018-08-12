package de.nikos410.discordBot.modules;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.discord.DiscordIO;
import de.nikos410.discordBot.util.discord.GuildOperations;
import de.nikos410.discordBot.util.discord.UserOperations;
import de.nikos410.discordBot.framework.annotations.AlwaysLoaded;
import de.nikos410.discordBot.framework.annotations.CommandModule;
import de.nikos410.discordBot.framework.CommandPermissions;
import de.nikos410.discordBot.framework.annotations.CommandSubscriber;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
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

    @CommandSubscriber(command = "help", help = "Zeigt diese Hilfe an")
    public void command_help(final IMessage message) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();
        final Map<String, Object> loadedModules = bot.getLoadedModules();

        for (final String key : loadedModules.keySet()) {
            final Object module = loadedModules.get(key);

            final StringBuilder helpBuilder = new StringBuilder();

            for (final Method method : module.getClass().getMethods()) {

                if (method.isAnnotationPresent(CommandSubscriber.class)) {
                    final CommandSubscriber annotation = method.getDeclaredAnnotationsByType(CommandSubscriber.class)[0];

                    if (bot.getUserPermissionLevel(message.getAuthor(), message.getGuild()) >= annotation.permissionLevel()) {
                        final String command = annotation.command();
                        final String help = annotation.help();

                        helpBuilder.append(String.format("`%s` %s", command, help));
                        helpBuilder.append('\n');
                    }
                }
            }

            final String moduleHelp = helpBuilder.toString();

            final CommandModule[] annotations = module.getClass().getDeclaredAnnotationsByType(CommandModule.class);
            final String moduleName = annotations[0].moduleName();

            if (!moduleHelp.isEmpty()) {
                embedBuilder.appendField(moduleName, moduleHelp, false);
            }
        }

        final EmbedObject embedObject = embedBuilder.build();
        DiscordIO.sendEmbed(message.getAuthor().getOrCreatePMChannel(), embedObject);

        if (!message.getChannel().isPrivate()) {
            DiscordIO.sendMessage(message.getChannel(), ":mailbox_with_mail:");
        }
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
        // List loaded modules
        final StringBuilder loadedBuilder = new StringBuilder();
        for (final String key : bot.getLoadedModules().keySet()) {
            loadedBuilder.append(key);
            loadedBuilder.append('\n');
        }
        final String loadedModulesString = loadedBuilder.toString().isEmpty() ? "_keine_" : loadedBuilder.toString();

        // List unloaded modules
        final StringBuilder unloadedBuilder = new StringBuilder();
        for (final String key : bot.getUnloadedModules()) {
            unloadedBuilder.append(key);
            unloadedBuilder.append('\n');
        }
        final String unloadedModulesString = unloadedBuilder.toString().isEmpty() ? "_keine_" : unloadedBuilder.toString();

        // Build embed
        final EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.appendField("Aktivierte Module", loadedModulesString, true);
        embedBuilder.appendField("Deaktivierte Module", unloadedModulesString, true);

        DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    @CommandSubscriber(command = "loadmodule", help = "Ein Modul aktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_LoadModule(final IMessage message, final String moduleName) {
        final boolean result = bot.activateModule(moduleName);

        final String resultMessage;
        if (result) {
            resultMessage = String.format(":white_check_mark: Modul `%s` aktiviert.", moduleName);
        }
        else {
            resultMessage = String.format("Fehler! Modul `%s` ist bereits aktiviert oder existiert nicht.", moduleName);
        }

        DiscordIO.sendMessage(message.getChannel(), resultMessage);
    }

    @CommandSubscriber(command = "unloadmodule", help = "Ein Modul deaktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_UnloadModule(final IMessage message, final String moduleName) {
        final boolean result = bot.deactivateModule(moduleName);

        final String resultMessage;
        if (result) {
            resultMessage = String.format(":white_check_mark: Modul `%s` deaktiviert.", moduleName);
        }
        else {
            resultMessage = String.format("Fehler! Modul `%s` ist bereits deaktiviert oder existiert nicht.", moduleName);
        }

        DiscordIO.sendMessage(message.getChannel(), resultMessage);
    }
}
