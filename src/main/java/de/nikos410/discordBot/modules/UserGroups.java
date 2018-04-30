package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.annotations.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.annotations.CommandSubscriber;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.EmbedBuilder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@CommandModule(moduleName = "Nutzergruppen", commandOnly = true)
public class UserGroups {
    private final static Path USERGROUPS_PATH = Paths.get("data/usergroups.json");
    private JSONObject usergroupsJSON;

    private final DiscordBot bot;

    private Logger log = LoggerFactory.getLogger(UserGroups.class);


    public UserGroups(final DiscordBot bot) {
        this.bot = bot;

        final String jsonContent = Util.readFile(USERGROUPS_PATH);
        usergroupsJSON = new JSONObject(jsonContent);
    }

    @CommandSubscriber(command = "createGroup", help = "Neue Gruppe erstellen", pmAllowed = false, permissionLevel = CommandPermissions.MODERATOR)
    public void command_createGroup(final IMessage message, final String groupName) {

        if (usergroupsJSON.has(groupName)) {
            Util.sendMessage(message.getChannel(), ":x: Gruppe existiert bereits!");
            return;
        }

        final IRole role = message.getGuild().createRole();
        role.changePermissions(EnumSet.noneOf(Permissions.class));
        role.changeName(groupName);
        role.changeMentionable(true);

        usergroupsJSON.put(groupName, role.getLongID());
        saveJSON();

        Util.sendMessage(message.getChannel(), String.format(":white_check_mark: Gruppe `%s` erstellt.", groupName));
        log.info(String.format("%s created new group %s.", Util.makeUserString(message.getAuthor(), message.getGuild()), groupName));
    }

    @CommandSubscriber(command = "createGroupChannel", help = "Channel für Gruppe erstellen", pmAllowed = false, permissionLevel = CommandPermissions.MODERATOR)
    public void command_createGroupChannel(final IMessage message, final String groupName, final String channelName) {
        if (!usergroupsJSON.has(groupName)) {
            Util.sendMessage(message.getChannel(), String.format(":x: Gruppe `%s` nicht gefunden!", groupName));
            return;
        }

        final IGuild server = message.getGuild();
        final IRole groupRole = server.getRoleByID(usergroupsJSON.getLong(groupName));
        final IRole everyoneRole = server.getEveryoneRole();
        final IRole modRole = server.getRoleByID(bot.configJSON.getLong("modRole"));
        final IChannel channel = server.createChannel(channelName);

        channel.overrideRolePermissions(groupRole, EnumSet.of(Permissions.READ_MESSAGES, Permissions.SEND_MESSAGES), EnumSet.noneOf(Permissions.class));
        channel.overrideRolePermissions(modRole, EnumSet.of(Permissions.READ_MESSAGES, Permissions.SEND_MESSAGES), EnumSet.noneOf(Permissions.class));
        channel.overrideRolePermissions(everyoneRole, EnumSet.noneOf(Permissions.class), EnumSet.of(Permissions.READ_MESSAGES, Permissions.SEND_MESSAGES));

        Util.sendMessage(message.getChannel(), String.format(":white_check_mark: Channel '%s' für Gruppe `%s` erstellt.", channelName, groupName));
        log.info(String.format("%s created new channel %s for group %s.", Util.makeUserString(message.getAuthor(), message.getGuild()), channelName, groupName));
    }

    @CommandSubscriber(command = "linkGroupChannel", help = "Bestehenden Channel mit Gruppe verknüpfen", pmAllowed = false, permissionLevel = CommandPermissions.MODERATOR)
    public void command_linkGroupChannel(final IMessage message, final String groupName, final String channelIdString) {
        if (!usergroupsJSON.has(groupName)) {
            Util.sendMessage(message.getChannel(), String.format(":x: Gruppe `%s` nicht gefunden!", groupName));
            return;
        }

        try {
            final long channelId = Long.parseLong(channelIdString);
            final IGuild server = message.getGuild();
            final IRole groupRole = server.getRoleByID(usergroupsJSON.getLong(groupName));

            final IChannel channelToLink = server.getChannelByID(channelId);
            linkGroupChannel(message, groupRole, channelToLink);

            final String channelName = channelToLink.getName();
            Util.sendMessage(message.getChannel(), String.format(":white_check_mark: Bestehenden Channel '%s' mit Gruppe `%s` verknüpft.", channelName, groupName));
            log.info(String.format("%s linked channel %s to group %s.", Util.makeUserString(message.getAuthor(), message.getGuild()), channelName, groupName));
        } catch (final NumberFormatException e) {
            Util.sendMessage(message.getChannel(), ":x: Channel ID konnte nicht geparsed werden!");
        }
    }

    private void linkGroupChannel(final IMessage message, final IRole groupRole, final IChannel channelToLink) {
        final IGuild server = message.getGuild();
        final IRole everyoneRole = server.getEveryoneRole();
        final IRole modRole = server.getRoleByID(bot.configJSON.getLong("modRole"));

        channelToLink.overrideRolePermissions(groupRole, EnumSet.of(Permissions.READ_MESSAGES, Permissions.SEND_MESSAGES), EnumSet.noneOf(Permissions.class));
        channelToLink.overrideRolePermissions(modRole, EnumSet.of(Permissions.READ_MESSAGES, Permissions.SEND_MESSAGES), EnumSet.noneOf(Permissions.class));
        channelToLink.overrideRolePermissions(everyoneRole, EnumSet.noneOf(Permissions.class), EnumSet.of(Permissions.READ_MESSAGES, Permissions.SEND_MESSAGES));
    }

    @CommandSubscriber(command = "removeGroup", help = "Gruppe entfernen", pmAllowed = false, permissionLevel = CommandPermissions.MODERATOR)
    public void command_removeGroup(final IMessage message, final String groupName) {

        if (!usergroupsJSON.has(groupName)) {
            Util.sendMessage(message.getChannel(), String.format(":x: Gruppe `%s` nicht gefunden!", groupName));
            return;
        }

        final IRole role = message.getGuild().getRoleByID(usergroupsJSON.getLong(groupName));
        role.delete();

        usergroupsJSON.remove(groupName);
        saveJSON();

        Util.sendMessage(message.getChannel(), String.format(":white_check_mark: Gruppe `%s` entfernt.", groupName));
        log.info(String.format("%s deleted group %s.", Util.makeUserString(message.getAuthor(), message.getGuild()), groupName));
    }

    @CommandSubscriber(command = "group", help = "Sich selbst eine Rolle zuweisen / wieder entfernen", pmAllowed = false)
    public void command_Group(final IMessage message, final String groupName) {
        final IUser user = message.getAuthor();
        final IGuild guild = message.getGuild();

        if (usergroupsJSON.has(groupName)) {
            // Gruppe existiert bereits

            final long roleID = usergroupsJSON.getLong(groupName);
            final IRole role = message.getGuild().getRoleByID(roleID);

            if (Util.hasRole(user, role, guild)) {
                user.removeRole(role);
                Util.sendMessage(message.getChannel(), String.format(":white_check_mark: Du wurdest aus der Gruppe `%s` entfernt.", groupName));
                log.info(String.format("%s left group %s.", Util.makeUserString(message.getAuthor(), message.getGuild()), groupName));
            }
            else {
                user.addRole(role);
                Util.sendMessage(message.getChannel(), String.format(":white_check_mark: Du wurdest zur Gruppe `%s` hinzugefügt.", groupName));
                log.info(String.format("%s joined group %s.", Util.makeUserString(message.getAuthor(), message.getGuild()), groupName));
            }
        }
        else {
            Util.sendMessage(message.getChannel(), String.format(":x: Gruppe `%s` nicht gefunden!", groupName));
        }
    }

    @CommandSubscriber(command = "groups", help = "Alle Rollen auflisten")
    public void command_groups(final IMessage message) {
        final StringBuilder stringBuilder = new StringBuilder();

        final List<String> keyList = new LinkedList<>();
        keyList.addAll(usergroupsJSON.keySet());
        Collections.sort(keyList);

        for (final String key : keyList) {
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

        Util.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    private void saveJSON() {
        log.debug("Saving UserGroups file.");

        final String jsonOutput = usergroupsJSON.toString(4);
        Util.writeToFile(USERGROUPS_PATH, jsonOutput);
    }
}
