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
import sx.blah.discord.handle.obj.IMessage;

import java.nio.file.Path;
import java.nio.file.Paths;

@CommandModule(moduleName = "Regeln", commandOnly = false)
public class Rules {
    private final static Path RULES_PATH = Paths.get("data/rules.json");

    private final DiscordBot bot;

    private JSONObject jsonWelcome;
    private String welcomeMessage;
    private String rulesDE;
    private String rulesEN;
    private String welcomeFooter;
    private boolean isEnabled;

    private Logger log = LoggerFactory.getLogger(Rules.class);

    public Rules (final DiscordBot bot) {
        this.bot = bot;

        // Welcome Nachricht auslesen
        final String welcomeFileContent = IOUtil.readFile(RULES_PATH);
        this.jsonWelcome = new JSONObject(welcomeFileContent);

        this.welcomeMessage = jsonWelcome.getString("welcome");
        this.rulesDE = jsonWelcome.getString("rulesDE");
        this.rulesEN = jsonWelcome.getString("rulesEN");
        this.welcomeFooter = jsonWelcome.getString("footer");
        this.isEnabled = jsonWelcome.getBoolean("on");
    }

    @EventSubscriber
    public void onUserJoin(UserJoinEvent event) {
        if (this.isEnabled) {
            DiscordIO.sendMessage(event.getUser().getOrCreatePMChannel(), welcomeMessage + "\n\n" + rulesDE + "\n\n\n" + String.format(welcomeFooter,
                    bot.configJSON.getString("prefix")));
        }
    }

    @CommandSubscriber(command = "regeln", help = "Die Regeln dieses Servers")
    public void command_Regeln(final IMessage message) {
        DiscordIO.sendMessage(message.getAuthor().getOrCreatePMChannel(), this.rulesDE);

        if (!message.getChannel().isPrivate()) {
            DiscordIO.sendMessage(message.getChannel(), ":mailbox_with_mail:");
        }
    }

    @CommandSubscriber(command = "rules", help = "The rules of this server")
    public void command_Rules(final IMessage message) {
        DiscordIO.sendMessage(message.getAuthor().getOrCreatePMChannel(), this.rulesEN);

        if (!message.getChannel().isPrivate()) {
            DiscordIO.sendMessage(message.getChannel(), ":mailbox_with_mail:");
        }
    }

    @CommandSubscriber(command = "welcomeset_test", help = "Begrüßungsnachricht testen", permissionLevel = CommandPermissions.ADMIN)
    public void command_Welcomeset_Test(final IMessage message) {
        DiscordIO.sendMessage(message.getAuthor().getOrCreatePMChannel(), welcomeMessage + "\n\n" + rulesDE + "\n\n\n" + String.format(welcomeFooter,
                bot.configJSON.getString("prefix")));
    }

    @CommandSubscriber(command = "welcomeset_enable", help = "Begrüßungsnachricht aktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_Welcomeset_Enable(final IMessage message) {
        this.isEnabled = true;
        if (jsonWelcome.has("on")) {
            jsonWelcome.remove("on");
        }
        jsonWelcome.put("on", true);
        this.saveJSON();

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Aktiviert!");
        log.info(String.format("%s enabled welcome messages.", UserOperations.makeUserString(message.getAuthor(), message.getGuild())));

    }

    @CommandSubscriber(command = "welcomeset_disable", help = "Begrüßungsnachricht deaktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_Welcomeset_Disable(final IMessage message) {
        this.isEnabled = false;
        if (jsonWelcome.has("on")) {
            jsonWelcome.remove("on");
        }
        jsonWelcome.put("on", false);
        this.saveJSON();

        DiscordIO.sendMessage(message.getChannel(), ":x: Deaktiviert!");
        log.info(String.format("%s disabled welcome messages.", UserOperations.makeUserString(message.getAuthor(), message.getGuild())));

    }

    @CommandSubscriber(command = "welcomeset_welcome", help = "Begrüßungsnachricht ändern", permissionLevel = CommandPermissions.ADMIN)
    public void command_Welcomeset_Welcome(final IMessage message, final String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;

        if (jsonWelcome.has("welcome")) {
            jsonWelcome.remove("welcome");
        }
        jsonWelcome.put("welcome", this.welcomeMessage);
        this.saveJSON();

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Begrüßungs-Nachricht geändert");
        DiscordIO.sendMessage(message.getChannel(), this.welcomeMessage);
    }

    @CommandSubscriber(command = "welcomeset_regeln", help = "Regeln (deutsch) ändern", permissionLevel = CommandPermissions.ADMIN)
    public void command_Welcomeset_Regeln(final IMessage message, final String rules) {
        this.rulesDE = rules;
        if (jsonWelcome.has("rulesDE")) {
            jsonWelcome.remove("rulesDE");
        }
        jsonWelcome.put("rulesDE", this.rulesDE);
        this.saveJSON();

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Regeln geändert:");
        DiscordIO.sendMessage(message.getChannel(), this.rulesDE);
        log.info(String.format("%s changed rules. (GER)", UserOperations.makeUserString(message.getAuthor(), message.getGuild())));

    }

    @CommandSubscriber(command = "welcomeset_rules", help = "Regeln (englisch) ändern", permissionLevel = CommandPermissions.ADMIN)
    public void command_Welcomeset_Rules(final IMessage message, final String rules) {
        this.rulesEN = rules;
        if (jsonWelcome.has("rulesEN")) {
            jsonWelcome.remove("rulesEN");
        }
        jsonWelcome.put("rulesEN", this.rulesEN);
        this.saveJSON();

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Regeln geändert:");
        DiscordIO.sendMessage(message.getChannel(), this.rulesEN);
        log.info(String.format("%s changed rules. (EN)", UserOperations.makeUserString(message.getAuthor(), message.getGuild())));

    }

    @CommandSubscriber(command = "welcomeset_footer", help = "Footer der Begüßungsnachricht ändern. `%s` für Befehls-Prefix",
            permissionLevel = CommandPermissions.ADMIN)
    public void command_Welcomeset_Footer(final IMessage message, final String footer) {
        this.welcomeFooter = footer;
        if (jsonWelcome.has("footer")) {
            jsonWelcome.remove("footer");
        }
        jsonWelcome.put("footer", this.welcomeFooter);
        this.saveJSON();

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Begrüßungs-Footer geändert");
        DiscordIO.sendMessage(message.getChannel(), String.format(this.welcomeFooter,
                bot.configJSON.getString("prefix")));
        log.info(String.format("%s changed welcome footer.", UserOperations.makeUserString(message.getAuthor(), message.getGuild())));
    }

    private void saveJSON() {
        log.debug("Saving welcome file.");
        final String jsonOutput = jsonWelcome.toString(4);
        IOUtil.writeToFile(RULES_PATH, jsonOutput);

        jsonWelcome = new JSONObject(jsonOutput);
    }
}
