package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.annotations.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.annotations.CommandSubscriber;

import org.json.JSONObject;
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
        role.changeName(groupName);
        role.changeMentionable(true);

        usergroupsJSON.put(groupName, role.getLongID());
        saveJSON();

        Util.sendMessage(message.getChannel(), ":white_check_mark: Gruppe `" + groupName + "` erstellt.");
    }

    @CommandSubscriber(command = "removeGroup", help = "Gruppe entfernen", pmAllowed = false, permissionLevel = CommandPermissions.MODERATOR)
    public void command_removeGroup(final IMessage message, final String groupName) {

        if (!usergroupsJSON.has(groupName)) {
            Util.sendMessage(message.getChannel(), ":x: Gruppe `" + groupName + "` nicht gefunden!");
            return;
        }

        final IRole role = message.getGuild().getRoleByID(usergroupsJSON.getLong(groupName));
        role.delete();

        usergroupsJSON.remove(groupName);
        saveJSON();

        Util.sendMessage(message.getChannel(), ":white_check_mark: Gruppe `" + groupName + "` entfernt.");
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
                Util.sendMessage(message.getChannel(), ":white_check_mark: Du wurdest aus der Gruppe `" + groupName + "` entfernt.");
            }
            else {
                user.addRole(role);
                Util.sendMessage(message.getChannel(), ":white_check_mark: Du wurdest zur Gruppe `" + groupName + "` hinzugefügt.");
            }
        }
        else {
            Util.sendMessage(message.getChannel(), ":x: Gruppe `" + groupName + "` nicht gefunden!");
        }
    }

    @CommandSubscriber(command = "groups", help = "Alle Rollen auflisten")
    public void command_groups(final IMessage message) {
        final StringBuilder stringBuilder = new StringBuilder();

        final List<String> keyList = new LinkedList<>();
        keyList.addAll(usergroupsJSON.keySet());
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

        embedBuilder.withFooterText("Weise dir mit '" + bot.configJSON.getString("prefix") + "group <Gruppe>' selbst eine dieser Gruppen zu");

        Util.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    private void saveJSON() {
        final String jsonOutput = usergroupsJSON.toString(4);
        Util.writeToFile(USERGROUPS_PATH, jsonOutput);
    }
}
