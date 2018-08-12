package de.nikos410.discordBot.modules;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.discord.DiscordIO;
import de.nikos410.discordBot.util.discord.GuildOperations;
import de.nikos410.discordBot.util.discord.UserOperations;
import de.nikos410.discordBot.util.io.IOUtil;
import de.nikos410.discordBot.framework.annotations.CommandModule;
import de.nikos410.discordBot.framework.CommandPermissions;
import de.nikos410.discordBot.framework.annotations.CommandSubscriber;

import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.guild.role.RoleDeleteEvent;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.EmbedBuilder;

@CommandModule(moduleName = "Nutzergruppen", commandOnly = false)
public class UserGroups {
    private final static Path USERGROUPS_PATH = Paths.get("data/usergroups.json");
    private static final String NON_GAME_GROUP_NAME_PREFIX = "~";

    private final JSONObject usergroupsJSON;

    private final DiscordBot bot;

    private final static Logger LOG = LoggerFactory.getLogger(UserGroups.class);

    public UserGroups(final DiscordBot bot) {
        this.bot = bot;

        final String jsonContent = IOUtil.readFile(USERGROUPS_PATH);
        usergroupsJSON = new JSONObject(jsonContent);
    }

    @CommandSubscriber(command = "createGroup", help = "Neue Gruppe erstellen", pmAllowed = false, permissionLevel = CommandPermissions.MODERATOR)
    public void command_createGroup(final IMessage message, final String groupName) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (guildJSON.has(groupName)) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Gruppe existiert bereits!");
            return;
        }

        final IRole role = message.getGuild().createRole();
        role.changePermissions(EnumSet.noneOf(Permissions.class));
        role.changeName(groupName);
        if (!groupName.startsWith(NON_GAME_GROUP_NAME_PREFIX)) {
            role.changeMentionable(true);
        }

        guildJSON.put(groupName, role.getLongID());
        saveJSON();

        DiscordIO.sendMessage(message.getChannel(), String.format(":white_check_mark: Gruppe `%s` erstellt.", groupName));
        LOG.info(String.format("%s created new group %s.", UserOperations.makeUserString(message.getAuthor(), guild), groupName));
    }

    @CommandSubscriber(command = "removeGroup", help = "Gruppe entfernen", pmAllowed = false, permissionLevel = CommandPermissions.MODERATOR)
    public void command_removeGroup(final IMessage message, final String groupName) {
        final IGuild guild = message.getGuild();

        // Validate group first
        validateGroup(guild, groupName);

        final JSONObject guildJSON = getJSONForGuild(guild);

        if (!guildJSON.has(groupName)) {
            DiscordIO.sendMessage(message.getChannel(), String.format(":x: Gruppe `%s` nicht gefunden!", groupName));
            return;
        }

        final IRole role = guild.getRoleByID(guildJSON.getLong(groupName));
        role.delete();

        guildJSON.remove(groupName);
        saveJSON();

        DiscordIO.sendMessage(message.getChannel(), String.format(":white_check_mark: Gruppe `%s` entfernt.", groupName));
        LOG.info(String.format("%s deleted group %s.", UserOperations.makeUserString(message.getAuthor(), guild), groupName));
    }

    @CommandSubscriber(command = "group", help = "Sich selbst eine Rolle zuweisen / wieder entfernen", pmAllowed = false)
    public void command_Group(final IMessage message, final String groupName) {
        final IGuild guild = message.getGuild();

        // Validate group first
        validateGroup(guild, groupName);

        final IUser user = message.getAuthor();
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (guildJSON.has(groupName)) {

            final long roleID = guildJSON.getLong(groupName);
            final IRole role = message.getGuild().getRoleByID(roleID);

            if (UserOperations.hasRole(user, role, guild)) {
                user.removeRole(role);
                DiscordIO.sendMessage(message.getChannel(), String.format(":white_check_mark: Du wurdest aus der Gruppe `%s` entfernt.", groupName));
                LOG.info(String.format("%s left group %s.", UserOperations.makeUserString(message.getAuthor(), guild), groupName));
            }
            else {
                user.addRole(role);
                DiscordIO.sendMessage(message.getChannel(), String.format(":white_check_mark: Du wurdest zur Gruppe `%s` hinzugefügt.", groupName));
                LOG.info(String.format("%s joined group %s.", UserOperations.makeUserString(message.getAuthor(), guild), groupName));
            }
        }
        else {
            DiscordIO.sendMessage(message.getChannel(), String.format(":x: Gruppe `%s` nicht gefunden!", groupName));
        }
    }

    @CommandSubscriber(command = "groups", help = "Alle Rollen auflisten", pmAllowed = false)
    public void command_groups(final IMessage message) {
        final IGuild guild = message.getGuild();

        // Validate all groups first
        validateAllGroupsForGuild(guild);

        final JSONObject guildJSON = getJSONForGuild(guild);

        final StringBuilder stringBuilder = new StringBuilder();

        final List<String> keyList = new LinkedList<>();
        keyList.addAll(guildJSON.keySet());
        Collections.sort(keyList);

        for (String key : keyList) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append('\n');
            }
            stringBuilder.append(key);
        }
        if (stringBuilder.length() == 0) {
            stringBuilder.append("_Keine_");
        }

        final EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.appendField("Verfügbare Gruppen:", stringBuilder.toString(), false);

        embedBuilder.withFooterText(String.format("Weise dir mit '%sgroup <Gruppe> selbst eine dieser Gruppen zu'", bot.configJSON.getString("prefix")));

        DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    private JSONObject getJSONForGuild (final IGuild guild) {
        if (usergroupsJSON.has(guild.getStringID())) {
            return usergroupsJSON.getJSONObject(guild.getStringID());
        }
        else {
            final JSONObject guildJSON = new JSONObject();
            usergroupsJSON.put(guild.getStringID(), guildJSON);
            return guildJSON;
        }
    }

    /**
     * Delete a group if the corresponding role gets deleted
     * @param event The event that is dispatched if a role is deleted
     */
    @EventSubscriber
    public void onRoleDelete(final RoleDeleteEvent event) {
        final long deletedRoleID = event.getRole().getLongID();

        final JSONObject guildJSON = getJSONForGuild(event.getGuild());
        for (String currentKey : guildJSON.keySet()) {
            final long currentID = guildJSON.getLong(currentKey);

            if (currentID == deletedRoleID) {
                guildJSON.remove(currentKey);
            }
        }

        saveJSON();
    }

    /**
     * On startup, make sure all roles corresponting to a group (still) exist
     * @param event Gets dispatched when Bot is ready
     */
    @EventSubscriber
    public void validateAllGroups(final ReadyEvent event) {
        // Validate all groups for all guilds
        final IDiscordClient client = event.getClient();
        for (IGuild guild : client.getGuilds()) {
            validateAllGroupsForGuild(guild);
        }
    }

    /**
     * Make sure all roles corresponding to a group on the specified guild (still) exist
     * @param guild The guild for which to validate groups
     */
    private void validateAllGroupsForGuild(final IGuild guild) {
        final JSONObject guildJSON = getJSONForGuild(guild);

        final Iterator<String> keyIterator = guildJSON.keys();
        while (keyIterator.hasNext()) {
            final String groupName = keyIterator.next();
            final long roleID = guildJSON.getLong(groupName);

            if (!GuildOperations.hasRoleByID(guild, roleID)) {
                keyIterator.remove();
                saveJSON();
            }
        }
    }

    /**
     * Make sure the role corresponding to a group (still) exists
     * @param guild The guild containing the group
     * @param groupName The name of the group to validate
     */
    private void validateGroup(final IGuild guild, final String groupName) {
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (!guildJSON.has(groupName)) {
            return;
        }

        final long roleID = guildJSON.getLong(groupName);

        if (!GuildOperations.hasRoleByID(guild, roleID)) {
            guildJSON.remove(groupName);
            saveJSON();
        }
    }

    private void saveJSON() {
        LOG.debug("Saving UserGroups file.");

        final String jsonOutput = usergroupsJSON.toString(4);
        IOUtil.writeToFile(USERGROUPS_PATH, jsonOutput);
    }
}
