package de.nikos410.discordbot.modules;

import java.util.EnumSet;
import java.util.List;

import de.nikos410.discordbot.framework.CommandModule;
import de.nikos410.discordbot.framework.CommandWrapper;
import de.nikos410.discordbot.framework.ModuleWrapper;
import de.nikos410.discordbot.framework.PermissionLevel;
import de.nikos410.discordbot.util.discord.DiscordIO;
import de.nikos410.discordbot.util.discord.GuildUtils;
import de.nikos410.discordbot.util.discord.UserUtils;
import de.nikos410.discordbot.framework.annotations.CommandSubscriber;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RateLimitException;

public class BotSetup extends CommandModule {
    private static final Logger LOG = LoggerFactory.getLogger(BotSetup.class);

    @Override
    public String getDisplayName() {
        return "Bot Setup";
    }

    @Override
    public String getDescription() {
        return "Mit diesem Modul kann der Bot konfiguriert und für einen Server eingerichtet werden.";
    }

    @CommandSubscriber(command = "help", help = "Zeigt diese Hilfe an")
    public void command_help(final IMessage message) {
        final EmbedBuilder helpEmbedBuilder = new EmbedBuilder();
        final List<ModuleWrapper> loadedModules = bot.getLoadedModules();

        for (ModuleWrapper module : loadedModules) {
            final StringBuilder moduleHelpBuilder = new StringBuilder();

            for (CommandWrapper command : module.getCommands()) {
                // Only list commands that are available to that user
                if (bot.getUserPermissionLevel(message.getAuthor(), message.getGuild()).getLevel()
                        >= command.getPermissionLevel().getLevel()) {

                    moduleHelpBuilder.append(String.format("`%s` - %s%n", command.getName(), command.getHelp()));
                }
            }

            final String helpString = moduleHelpBuilder.toString();
            if (moduleHelpBuilder.length() > 0) {
                helpEmbedBuilder.appendField(module.getDisplayName(), helpString, false);
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
            LOG.debug("No JSON instance found for this guild. Creating.");
            guildRoles = new JSONObject();
            rolesJSON.put(guildID, guildRoles);
            LOG.debug("Successfully created JSON instance for this guild.");
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

        LOG.info("Shutting down modules.");
        bot.getLoadedModules().forEach(module -> module.getInstance().shutdown());

        this.bot.getClient().logout();
    }

    @CommandSubscriber(command = "setbotname", help = "Nutzernamen des Bots ändern", permissionLevel = PermissionLevel.OWNER)
    public void command_setUsername(final IMessage message, final String newUserName) {
        try {
            LOG.info("Changing the username to {}.", newUserName);
            this.bot.getClient().changeUsername(newUserName);
            DiscordIO.sendMessage(message.getChannel(), String.format(":white_check_mark: Neuer Username gesetzt: `%s`", newUserName));
        }
        catch (RateLimitException e) {
            DiscordIO.errorNotify(e, message.getChannel());
            LOG.warn("Ratelimited while trying to change username.");
        }
    }

    @CommandSubscriber(command = "modules", help = "Alle Module anzeigen")
    public void command_listModules(final IMessage message) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        // List loaded modules
        final StringBuilder loadedBuilder = new StringBuilder();
        for (ModuleWrapper module : bot.getLoadedModules()) {
            loadedBuilder.append(module.getName());
            loadedBuilder.append('\n');
        }
        final String loadedModulesString = loadedBuilder.toString().isEmpty() ? "_keine_" : loadedBuilder.toString();
        embedBuilder.appendField("Aktivierte Module", loadedModulesString, true);

        // List unloaded modules
        final StringBuilder unloadedBuilder = new StringBuilder();
        for (ModuleWrapper module : bot.getUnloadedModules()) {
            unloadedBuilder.append(module.getName());
            unloadedBuilder.append('\n');
        }
        final String unloadedModulesString = unloadedBuilder.toString().isEmpty() ? "_keine_" : unloadedBuilder.toString();
        embedBuilder.appendField("Deaktivierte Module", unloadedModulesString, true);

        // Add failed modules, if present
        final List<ModuleWrapper> failedModules = bot.getFailedModules();
        if (!failedModules.isEmpty()) {
            final StringBuilder failedBuilder = new StringBuilder();
            for (ModuleWrapper module : failedModules) {
                failedBuilder.append(module.getName());
                failedBuilder.append('\n');
            }
            embedBuilder.appendField("Folgende Module konnten nicht geladen werden:", failedBuilder.toString(), true);
        }

        DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    @CommandSubscriber(command = "loadmodule", help = "Ein Modul aktivieren", permissionLevel = PermissionLevel.ADMIN)
    public void command_loadModule(final IMessage message, final String moduleName) {
        final ModuleWrapper result = bot.activateModule(moduleName);

        if (result == null) {
            DiscordIO.sendMessage(message.getChannel(),
                    String.format("Fehler! Modul `%s` ist bereits aktiviert oder existiert nicht.", moduleName));
        }
        else if (result.getStatus().equals(ModuleWrapper.ModuleStatus.FAILED)) {
            DiscordIO.sendMessage(message.getChannel(),
                    String.format("Fehler! Modul `%s` konnte nicht geladen werden!", moduleName));
        }
        else {
            message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
        }
    }

    @CommandSubscriber(command = "unloadmodule", help = "Ein Modul deaktivieren", permissionLevel = PermissionLevel.ADMIN)
    public void command_unloadModule(final IMessage message, final String moduleName) {
        if (moduleName.equalsIgnoreCase("Bot-Setup")) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Das Bot-Setup Modul kann nicht deaktiviert werden.");
            return;
        }

        final ModuleWrapper result = bot.deactivateModule(moduleName);

        if(result == null) {
            DiscordIO.sendMessage(message.getChannel(),
                    String.format("Fehler! Modul `%s` ist bereits deaktiviert oder existiert nicht.", moduleName));
        }
        else {
            message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:

        }
    }
}
