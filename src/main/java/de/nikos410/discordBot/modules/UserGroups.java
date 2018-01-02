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
    public void command_createGroup(final IMessage message) {
        final String groupname = Util.getContext(message.getContent()).toLowerCase();

        if (usergroupsJSON.has(groupname)) {
            Util.sendMessage(message.getChannel(), ":x: Gruppe existiert bereits!");
            return;
        }

        final IRole role = message.getGuild().createRole();
        role.changeName(groupname);
        role.changeMentionable(true);

        usergroupsJSON.put(groupname, role.getLongID());
        saveJSON();

        Util.sendMessage(message.getChannel(), ":white_check_mark: Gruppe `" + groupname + "` erstellt.");
    }

    @CommandSubscriber(command = "removeGroup", help = "Gruppe entfernen", pmAllowed = false, permissionLevel = CommandPermissions.MODERATOR)
    public void command_removeGroup(final IMessage message) {
        final String groupname = Util.getContext(message.getContent()).toLowerCase();

        if (!usergroupsJSON.has(groupname)) {
            Util.sendMessage(message.getChannel(), ":x: Gruppe `" + groupname + "` nicht gefunden!");
            return;
        }

        final IRole role = message.getGuild().getRoleByID(usergroupsJSON.getLong(groupname));
        role.delete();

        usergroupsJSON.remove(groupname);
        saveJSON();

        Util.sendMessage(message.getChannel(), ":white_check_mark: Gruppe `" + groupname + "` entfernt.");
    }

    @CommandSubscriber(command = "group", help = "Sich selbst eine Rolle zuweisen / wieder entfernen", pmAllowed = false, permissionLevel = CommandPermissions.EVERYONE)
    public void command_Group(final IMessage message) {
        final String groupname = Util.getContext(message.getContent()).toLowerCase();
        final IUser user = message.getAuthor();
        final IGuild guild = message.getGuild();

        if (usergroupsJSON.has(groupname)) {
            // Gruppe existiert bereits

            final long roleID = usergroupsJSON.getLong(groupname);
            final IRole role = message.getGuild().getRoleByID(roleID);

            if (Util.hasRole(user, role, guild)) {
                user.removeRole(role);
                Util.sendMessage(message.getChannel(), ":white_check_mark: Du wurdest aus der Gruppe `" + groupname + "` entfernt.");
            }
            else {
                user.addRole(role);
                Util.sendMessage(message.getChannel(), ":white_check_mark: Du wurdest zur Gruppe `" + groupname + "` hinzugefügt.");
            }
        }
        else {
            Util.sendMessage(message.getChannel(), ":x: Gruppe `" + groupname + "` nicht gefunden!");
        }
    }

    @CommandSubscriber(command = "groups", help = "Alle Rollen auflisten", pmAllowed = true, permissionLevel = CommandPermissions.EVERYONE)
    public void command_groups(final IMessage message) {
        final StringBuilder stringBuilder = new StringBuilder();

        for (String key : usergroupsJSON.keySet()) {
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
