package nikos.discordBot.modules;


import nikos.discordBot.util.Util;
import org.json.JSONObject;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Mod {
    private final static Path CONFIG_PATH = Paths.get("config/config.json");

    private final static String MODULE_NAME = "Mod";
    private final static char SEPARATOR = '⠀';
    private final static String COMMANDS = "`kick <u> <m>   " + SEPARATOR + "`  Nutzer mit Nachricht vom Server kicken";
    private final static String COMMANDS_FOOTER = "<u> = user, <m> = Nachricht";

    private static IDiscordClient client;
    private static IUser self;

    private JSONObject jsonMod;

    private final String prefix;
    private final String ownerID;
    private final String modID;


    public Mod(final IDiscordClient dClient) {
        client = dClient;
        self = client.getOurUser();

        // Prefix und Owner ID aus Config-Datei auslesen
        final String configFileContent = Util.readFile(CONFIG_PATH);
        final JSONObject jsonConfig = new JSONObject(configFileContent);
        this.prefix = jsonConfig.getString("prefix");
        this.ownerID = jsonConfig.getString("owner");
        this.modID = jsonConfig.getString("modRole");
    }

    @EventSubscriber
    public void onMessageRecieved(final MessageReceivedEvent event) {
        final IMessage message = event.getMessage();
        final String messageContent = message.getContent();

        if (messageContent.equalsIgnoreCase(prefix + "help")) {
            this.command_Help(message);
        }
        else if (messageContent.startsWith(prefix + "kick")) {
            this.command_Kick(message);
        }
    }

    private void command_Help(final IMessage message) {
        if (Util.hasRoleByID(message.getAuthor(), this.modID, message.getGuild())) {
            final EmbedBuilder embedBuilder = new EmbedBuilder();

            embedBuilder.withColor(new Color(59, 168, 25));
            embedBuilder.appendField(MODULE_NAME, COMMANDS, false);
            embedBuilder.withFooterText(COMMANDS_FOOTER);

            final EmbedObject embedObject = embedBuilder.build();

            Util.sendBufferedEmbed(message.getChannel(), embedObject);
        }
    }

    private void command_Kick(final IMessage message) {
        if (Util.hasRoleByID(message.getAuthor(), this.modID, message.getGuild())) {

            final List<IUser> mentions = message.getMentions();
            if (mentions.size() <1) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: Kein Nutzer angegeben!");
            }
            else if (mentions.size() > 1) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: In der Nachricht keine Nutzer erwähnen!");
            }

            final IUser kickUser = mentions.get(0);
            if (kickUser == null) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: Nutzer nicht gefunden!");
                return;
            }

            String customMessage = Util.getContext(message.getContent(), 2);
            if (customMessage.isEmpty()) {
                customMessage = "kein";
            }

            final String kickMessage = "**Du wurdest gekickt!** (Du kannst dem Server jedoch erneut beitreten)" +
            "\n Hinweis: _" + customMessage;

            Util.sendPM(kickUser, kickMessage);
            message.getGuild().kickUser(kickUser);
            Util.sendMessage(message.getChannel(), ":door::arrow_left:");
        }
        else {
            message.getGuild().kickUser(message.getAuthor());
            Util.sendMessage(message.getChannel(), "Netter Versuch ;)");
            Util.sendPM(message.getAuthor(), "Netter Versuch ;)");
        }
    }

    private void command_Ban(final IMessage message) {
        if (Util.hasRoleByID(message.getAuthor(), this.modID, message.getGuild())) {

            final List<IUser> mentions = message.getMentions();
            if (mentions.size() <1) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: Kein Nutzer angegeben!");
            }
            else if (mentions.size() > 1) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: In der Nachricht keine Nutzer erwähnen!");
            }

            final IUser banUser = mentions.get(0);
            if (banUser == null) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: Nutzer nicht gefunden!");
                return;
            }

            String customMessage = Util.getContext(message.getContent(), 2);
            if (customMessage.isEmpty()) {
                customMessage = "kein";
            }

            final String banMessage = "**Du wurdest gebannt!** \n Hinweis: _" + customMessage;

            Util.sendPM(banUser, banMessage);
            message.getGuild().kickUser(banUser);
            Util.sendMessage(message.getChannel(), ":hammer:");
        }
        else {
            message.getGuild().kickUser(message.getAuthor());
            Util.sendMessage(message.getChannel(), "Netter Versuch ;)");
            Util.sendPM(message.getAuthor(), "Netter Versuch ;)");
        }
    }
}
