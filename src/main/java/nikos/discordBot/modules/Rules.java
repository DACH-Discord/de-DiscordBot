package nikos.discordBot.modules;

import nikos.discordBot.util.Util;
import org.json.JSONObject;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IPrivateChannel;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Rules {
    private static final String MODULE_NAME = "Regeln";
    private static final char SEPARATOR = '⠀';
    private static final String COMMANDS =
                    "`regeln         " + SEPARATOR + "`  Schickt die Regeln dieses Servers" + '\n' +
                    "`rules          " + SEPARATOR + "`  Schickt die Regeln dieses Servers";
    private final static Path WELCOME_PATH = Paths.get("data/welcome.json");
    private final static Path CONFIG_PATH = Paths.get("config/config.json");

    private static IDiscordClient client;

    private final String prefix;
    private final String ownerID;

    private JSONObject jsonWelcome;
    private String welcomeMessage;
    private String welcomeRules;
    private boolean on;

    public Rules(IDiscordClient dClient) {
        client = dClient;

        // Prefix und Owner ID aus Config-Datei auslesen
        final String configFileContent = Util.readFile(CONFIG_PATH);
        final JSONObject jsonConfig = new JSONObject(configFileContent);

        this.prefix = jsonConfig.getString("prefix");
        this.ownerID = jsonConfig.getString("owner");

        // Welcome Nachricht auslesen
        final String welcomeFileContent = Util.readFile(WELCOME_PATH);
        jsonWelcome = new JSONObject(welcomeFileContent);

        this.welcomeMessage = jsonWelcome.getString("welcome");
        this.welcomeRules = jsonWelcome.getString("rules");
        this.on = jsonWelcome.getBoolean("on");
    }

    @EventSubscriber
    public void onUserJoin(UserJoinEvent event) {
        if (this.on) {
            final IUser user = event.getUser();

            Util.sendPM(user, welcomeMessage);
            Util.sendPM(user, welcomeRules);
        }
    }

    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) {
        final IMessage message = event.getMessage();
        final String messageContent = message.getContent();
        final String messageContentLowerCase = messageContent.toLowerCase();

        if (messageContentLowerCase.startsWith(prefix + "help")) {
            final EmbedBuilder embedBuilder = new EmbedBuilder();

            embedBuilder.withColor(new Color(114, 137, 218));
            embedBuilder.appendField(MODULE_NAME, COMMANDS, false);

            final EmbedObject embedObject = embedBuilder.build();

            Util.sendEmbed(message.getChannel(), embedObject);
        }
        else if (messageContentLowerCase.equals(prefix + "rules") ||
                 messageContentLowerCase.equals(prefix + "regeln")) {
            Util.sendPM(message.getAuthor(), this.welcomeRules);

            if (!message.getChannel().isPrivate()) {
                Util.sendMessage(message.getChannel(), ":mailbox_with_mail:");
            }
        }

        // Folgende Befehle nur Owner
        if (!message.getAuthor().getID().equals(ownerID)) {
            return;
        }

        final String context = Util.getContext(Util.getContext(messageContent));
        if (messageContentLowerCase.equals(prefix + "welcomeset")) {
            // Im Channel antworten
            Util.sendMessage(message.getChannel(), this.welcomeMessage);
            Util.sendMessage(message.getChannel(), this.welcomeRules);

            // Per PM antworten
            Util.sendPM(message.getAuthor(), this.welcomeMessage);
            Util.sendPM(message.getAuthor(), this.welcomeRules);
        }
        else if (messageContentLowerCase.startsWith(prefix + "welcomeset welcome ")) {
            this.welcomeMessage = context;
            if (jsonWelcome.has("welcome")) {
                jsonWelcome.remove("welcome");
            }
            jsonWelcome.put("welcome", this.welcomeMessage);
            this.saveWelcomeJSON();

            Util.sendMessage(message.getChannel(), ":white_check_mark: Begrüßungs-Nachricht geändert");
            Util.sendMessage(message.getChannel(), welcomeMessage);
        }
        else if (messageContentLowerCase.startsWith(prefix + "welcomeset rules ")) {
            this.welcomeRules = context;
            if (jsonWelcome.has("rules")) {
                jsonWelcome.remove("rules");
            }
            jsonWelcome.put("rules", this.welcomeRules);
            this.saveWelcomeJSON();

            Util.sendMessage(message.getChannel(), ":white_check_mark: Regeln geändert:");
            Util.sendMessage(message.getChannel(), welcomeRules);
        }
        else if (messageContentLowerCase.equals(prefix + "welcomeset enable")) {
            this.on = true;
            if (jsonWelcome.has("on")) {
                jsonWelcome.remove("on");
            }
            jsonWelcome.put("on", true);
            this.saveWelcomeJSON();

            Util.sendMessage(message.getChannel(), ":white_check_mark: Aktiviert!");
        }
        else if (messageContentLowerCase.equals(prefix + "welcomeset disable")) {
            this.on = false;
            if (jsonWelcome.has("on")) {
                jsonWelcome.remove("on");
            }
            jsonWelcome.put("on", false);
            this.saveWelcomeJSON();

            Util.sendMessage(message.getChannel(), ":x: Deaktiviert!");
        }
    }

    private void saveWelcomeJSON() {
        final String jsonOutput = jsonWelcome.toString(4);
        Util.writeToFile(WELCOME_PATH, jsonOutput);

        jsonWelcome = new JSONObject(jsonOutput);
    }
}
