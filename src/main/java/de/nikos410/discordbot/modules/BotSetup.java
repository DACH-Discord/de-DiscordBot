package de.nikos410.discordbot.modules;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import de.nikos410.discordbot.DiscordBot;
import de.nikos410.discordbot.framework.PermissionLevel;
import de.nikos410.discordbot.util.discord.DiscordIO;
import de.nikos410.discordbot.util.discord.GuildUtils;
import de.nikos410.discordbot.util.discord.UserUtils;
import de.nikos410.discordbot.framework.annotations.CommandModule;
import de.nikos410.discordbot.framework.annotations.CommandSubscriber;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.obj.Guild;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RateLimitException;

@CommandModule(moduleName = "Bot-Setup", commandOnly = true)
public class BotSetup {
    private static final Logger LOG = LoggerFactory.getLogger(DiscordBot.class);

    private final DiscordBot bot;
    private final IDiscordClient client;

    public BotSetup (final DiscordBot bot) {
        this.bot = bot;
        this.client = bot.getClient();
    }

    @CommandSubscriber(command = "help", help = "Zeigt diese Hilfe an")
    public void command_help(final IMessage message) {
        final EmbedBuilder helpEmbedBuilder = new EmbedBuilder();
        final Map<String, Object> loadedModules = bot.getLoadedModules();

        // Visit all modules that are loaded
        for (final Object module : loadedModules.values()) {
            final StringBuilder helpStringBuilder = new StringBuilder();

            // Visit all Methods of that module
            for (final Method method : module.getClass().getMethods()) {

                // Ignore methods that are not a command
                if (method.isAnnotationPresent(CommandSubscriber.class)) {
                    final CommandSubscriber annotation = method.getDeclaredAnnotationsByType(CommandSubscriber.class)[0];

                    // Only list commands that are available to that user
                    if (bot.getUserPermissionLevel(message.getAuthor(), message.getGuild()).getLevel()
                            >= annotation.permissionLevel().getLevel()) {
                        final String command = annotation.command();
                        final String help = annotation.help();

                        helpStringBuilder.append(String.format("`%s` %s", command, help));
                        helpStringBuilder.append('\n');
                    }
                }
            }

            final String helpString = helpStringBuilder.toString();

            final CommandModule[] annotations = module.getClass().getDeclaredAnnotationsByType(CommandModule.class);
            final String moduleName = annotations[0].moduleName();

            if (!helpString.isEmpty()) {
                helpEmbedBuilder.appendField(moduleName, helpString, false);
            }
        }

        final EmbedObject embedObject = helpEmbedBuilder.build();
        DiscordIO.sendEmbed(message.getAuthor().getOrCreatePMChannel(), embedObject);

        if (!message.getChannel().isPrivate()) {
            DiscordIO.sendMessage(message.getChannel(), ":mailbox_with_mail:");
        }
    }

    @CommandSubscriber(command = "setModRole", help = "Moderator-Rolle setzen", pmAllowed = false, passContext = false, ignoreParameterCount = true)
    public void command_setModRole (final IMessage message, final String roleIDParameter) {
        setRole(message, roleIDParameter, "modRole");
    }

    @CommandSubscriber(command = "setAdminRole", help = "Admin-Rolle setzen", pmAllowed = false, passContext = false, ignoreParameterCount = true)
    public void command_setAdminRole (final IMessage message, final String roleIDParameter) {
        setRole(message, roleIDParameter, "adminRole");
    }

    /**
     * Update the admin or mod role for a server.
     *
     * @param message The message that triggered the command
     * @param roleIDParameter The parameter that was specified with the command
     * @param fieldName The name of the role in the roles.json file ("adminRole" or "modRole")
     */
    private void setRole(final IMessage message, final String roleIDParameter, final String fieldName) {
        final IGuild guild = message.getGuild();
        final IChannel channel = message.getChannel();

        LOG.info("Setting role {} for guild {} (ID: {})", fieldName, guild.getName(), guild.getStringID());

        // User needs to have the permission "Manage Server" or "Admin"
        if (!canUserSetup(message.getAuthor(), guild)) {
            DiscordIO.sendMessage(channel, "Du benötigst die permission \"Server Verwalten\" oder \"Administrator\" um diesen Befehl zu benutzen");
            LOG.info("Missing permissions. Aborting.");
            return;
        }

        // Get role
        final IRole role = GuildUtils.getRoleFromMessage(message, roleIDParameter);
        if (role == null) {
            LOG.info("No valid role specified. Aborting.");
            DiscordIO.sendMessage(channel, ":x: Keine gültige Rolle angegeben!");
            return;
        }

        final JSONObject rolesJSON = bot.rolesJSON;
        final String guildID = guild.getStringID();

        final JSONObject guildRoles;
        if (rolesJSON.has(guildID)) {
            guildRoles = rolesJSON.getJSONObject(guildID);
        }
        else {
            LOG.debug("No JSON object found for this guild. Creating.");
            guildRoles = new JSONObject();
            rolesJSON.put(guildID, guildRoles);
            LOG.debug("Successfully created JSON object for this guild.");
        }
        guildRoles.put(fieldName, role.getLongID());

        bot.saveRoles();

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:

        LOG.info("Updated role {} for guild {} (ID: {}). New role: {}", fieldName, guild.getName(),
                guildID, role.getStringID());
    }

    /**
     * Checks if a user is authorized to setup the bot for a server
     * User needs to have a role with the permission "Manage Server" or "Administrator"
     *
     * @return true if the user is authorized, false if not
     */
    private boolean canUserSetup(final IUser user, final IGuild guild) {
        LOG.trace("Checking if user {} (ID: {}) is authorized to setup roles for guild {} (ID: {})...",
                UserUtils.makeUserString(user, guild), user.getStringID(),
                guild.getName(), guild.getStringID());

        for (IRole role : user.getRolesForGuild(guild)) {
            LOG.trace("Checking role {} (ID: {})", role.getName(), role.getStringID());
            final EnumSet<Permissions> rolePermissions = role.getPermissions();
            if (rolePermissions.contains(Permissions.MANAGE_SERVER) ||
            rolePermissions.contains(Permissions.ADMINISTRATOR)) {
                return true;
            }
        }
        return false;
    }


    @CommandSubscriber(command = "shutdown", help = "Schaltet den Bot aus", permissionLevel = PermissionLevel.OWNER)
    public void command_shutdown(final IMessage message) {
        DiscordIO.sendMessage(message.getChannel(), "Ausschalten... :zzz:");
        LOG.info("Shutting down.");
        this.client.logout();
    }

    @CommandSubscriber(command = "setbotname", help = "Nutzernamen des Bots ändern", permissionLevel = PermissionLevel.OWNER)
    public void command_setUsername(final IMessage message, final String newUserName) {
        try {
            LOG.info("Changing the username to {}.", newUserName);
            this.client.changeUsername(newUserName);
            DiscordIO.sendMessage(message.getChannel(), String.format(":white_check_mark: Neuer Username gesetzt: `%s`", newUserName));
        }
        catch (RateLimitException e) {
            DiscordIO.errorNotify(e, message.getChannel());
            LOG.warn("Ratelimited while trying to change username.");
        }
    }

    @CommandSubscriber(command = "modules", help = "Alle Module anzeigen")
    public void command_listModules(final IMessage message) {
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

    @CommandSubscriber(command = "loadmodule", help = "Ein Modul aktivieren", permissionLevel = PermissionLevel.ADMIN)
    public void command_loadModule(final IMessage message, final String moduleName) {
        final boolean result = bot.activateModule(moduleName);

        if (result) {
            // Method returned true, everything went fine
            message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
        }
        else {
            // Method returned false, module doesn't exist or is already activated
            DiscordIO.sendMessage(message.getChannel(),
                    String.format("Fehler! Modul `%s` ist bereits aktiviert oder existiert nicht.", moduleName));
        }
    }

    @CommandSubscriber(command = "unloadmodule", help = "Ein Modul deaktivieren", permissionLevel = PermissionLevel.ADMIN)
    public void command_unloadModule(final IMessage message, final String moduleName) {
        if (moduleName.equalsIgnoreCase("Bot-Setup")) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Das Bot-Setup Modul kann nicht deaktiviert werden.");
            return;
        }

        final boolean result = bot.deactivateModule(moduleName);

        if (result) {
            // Method returned true, everything went fine
            message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
        }
        else {
            // Method returned false, module doesn't exist or is already deactivated
            DiscordIO.sendMessage(message.getChannel(),
                    String.format("Fehler! Modul `%s` ist bereits deaktiviert oder existiert nicht.", moduleName));
        }
    }
}
