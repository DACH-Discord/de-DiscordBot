package de.nikos410.discordBot.modules;


import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.discord.DiscordIO;
import de.nikos410.discordBot.util.discord.UserOperations;
import de.nikos410.discordBot.util.io.IOUtil;
import de.nikos410.discordBot.modular.annotations.CommandModule;
import de.nikos410.discordBot.modular.CommandPermissions;
import de.nikos410.discordBot.modular.annotations.CommandSubscriber;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;

import java.nio.file.Path;
import java.nio.file.Paths;

@CommandModule(moduleName = "Regeln", commandOnly = false)
public class Rules {
    private final static Path RULES_PATH = Paths.get("data/rules.json");

    private final DiscordBot bot;

    private JSONObject rulesJSON;

    private Logger log = LoggerFactory.getLogger(Rules.class);

    public Rules (final DiscordBot bot) {
        this.bot = bot;

        // Welcome Nachricht auslesen
        final String welcomeFileContent = IOUtil.readFile(RULES_PATH);
        this.rulesJSON = new JSONObject(welcomeFileContent);
    }

    @EventSubscriber
    public void onUserJoin(UserJoinEvent event) {
        final IGuild guild = event.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (guildJSON.has("welcome") && guildJSON.has("rulesDE") && guildJSON.has("footer")) {

            DiscordIO.sendMessage(event.getUser().getOrCreatePMChannel(), guildJSON.getString("welcome") +
                    "\n\n" + guildJSON.getString("rulesDE") + "\n\n\n" + guildJSON.getString("footer"));
        }
    }

    @CommandSubscriber(command = "regeln", help = "Die Regeln dieses Servers")
    public void command_Regeln(final IMessage message) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (guildJSON.has("rulesDE")) {
            DiscordIO.sendMessage(message.getAuthor().getOrCreatePMChannel(), guildJSON.getString("rulesDE"));

            if (!message.getChannel().isPrivate()) {
                DiscordIO.sendMessage(message.getChannel(), ":mailbox_with_mail:");
            }
        }
        else {
            DiscordIO.sendMessage(message.getChannel(), "Keine Regeln für diesen Server hinterlegt.");
        }
    }

    @CommandSubscriber(command = "rules", help = "The rules of this server")
    public void command_Rules(final IMessage message) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (guildJSON.has("rulesEN")) {
            DiscordIO.sendMessage(message.getAuthor().getOrCreatePMChannel(), guildJSON.getString("rulesEN"));

            if (!message.getChannel().isPrivate()) {
                DiscordIO.sendMessage(message.getChannel(), ":mailbox_with_mail:");
            }
        }
        else {
            DiscordIO.sendMessage(message.getChannel(), "No rules found for this server.");
        }
    }

    @CommandSubscriber(command = "welcomeTest", help = "Begrüßungsnachricht testen", permissionLevel = CommandPermissions.ADMIN)
    public void command_WelcomeTest(final IMessage message) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (guildJSON.has("welcome") && guildJSON.has("rulesDE") && guildJSON.has("footer")) {

            DiscordIO.sendMessage(message.getChannel(), guildJSON.getString("welcome") +
                    "\n\n" + guildJSON.getString("rulesDE") + "\n\n\n" + guildJSON.getString("footer"));
        }
    }

    @CommandSubscriber(command = "enableWelcome", help = "Begrüßungsnachricht aktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_EnableWelcome(final IMessage message) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        guildJSON.put("on", true);
        saveJSON();

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Aktiviert!");
        log.info(String.format("%s enabled welcome messages for server %s (ID: %s)", UserOperations.makeUserString(message.getAuthor(), message.getGuild()),
                guild.getName(), guild.getStringID()));
    }

    @CommandSubscriber(command = "disableWelcome", help = "Begrüßungsnachricht deaktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_DisableWelcome(final IMessage message) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        guildJSON.put("on", false);
        saveJSON();

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Deaktiviert!");
        log.info(String.format("%s disabled welcome messages for server %s (ID: %s)", UserOperations.makeUserString(message.getAuthor(), message.getGuild()),
                guild.getName(), guild.getStringID()));
    }

    @CommandSubscriber(command = "setWelcome", help = "Begrüßungsnachricht ändern", permissionLevel = CommandPermissions.ADMIN)
    public void command_setWelcome(final IMessage message, final String welcomeMessage) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        guildJSON.put("welcome", welcomeMessage);
        saveJSON();

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Begrüßungs-Nachricht geändert:");
        DiscordIO.sendMessage(message.getChannel(), welcomeMessage);
    }

    @CommandSubscriber(command = "setRegeln", help = "Regeln (deutsch) ändern", permissionLevel = CommandPermissions.ADMIN)
    public void command_SetRegeln(final IMessage message, final String rulesDE) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        guildJSON.put("rulesDE", rulesDE);
        saveJSON();

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Regeln (DE) geändert:");
        DiscordIO.sendMessage(message.getChannel(), rulesDE);
        log.info(String.format("%s changed rules. (DE)", UserOperations.makeUserString(message.getAuthor(), message.getGuild())));
    }

    @CommandSubscriber(command = "setRules", help = "Regeln (englisch) ändern", permissionLevel = CommandPermissions.ADMIN)
    public void command_SetRules(final IMessage message, final String rulesEN) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        guildJSON.put("rulesEN", rulesEN);
        saveJSON();

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Regeln (EN) geändert:");
        DiscordIO.sendMessage(message.getChannel(), rulesEN);
        log.info(String.format("%s changed rules. (EN)", UserOperations.makeUserString(message.getAuthor(), message.getGuild())));
    }

    @CommandSubscriber(command = "setFooter", help = "Footer der Begüßungsnachricht ändern.",
            permissionLevel = CommandPermissions.ADMIN)
    public void command_SetFooter(final IMessage message, final String footer) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        guildJSON.put("footer", footer);
        saveJSON();

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Begrüßungs-Footer geändert:");
        DiscordIO.sendMessage(message.getChannel(), footer);
        log.info(String.format("%s changed rules. (DE)", UserOperations.makeUserString(message.getAuthor(), message.getGuild())));
    }

    private JSONObject getJSONForGuild (final IGuild guild) {
        if (rulesJSON.has(guild.getStringID())) {
            return rulesJSON.getJSONObject(guild.getStringID());
        }
        else {
            final JSONObject guildJSON = new JSONObject();
            rulesJSON.put(guild.getStringID(), guildJSON);
            return guildJSON;
        }
    }

    private void saveJSON() {
        log.debug("Saving rules file.");
        final String jsonOutput = rulesJSON.toString(4);
        IOUtil.writeToFile(RULES_PATH, jsonOutput);
    }
}
