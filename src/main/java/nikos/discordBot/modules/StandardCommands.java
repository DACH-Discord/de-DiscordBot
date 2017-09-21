package nikos.discordBot.modules;

import nikos.discordBot.util.Util;
import org.json.JSONObject;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Standard Listeners
 */
public class StandardCommands {
    private static IDiscordClient client;

    private final static Path CONFIG_PATH = Paths.get("config/config.json");

    private static final String MODULE_NAME = "Allgemeine Befehle";
    private static final char SEPARATOR = '⠀';
    private static final String COMMANDS =
            "`help           " + SEPARATOR + "`  Zeigt diese Hilfe" + '\n' +
            "`quote <ID>     " + SEPARATOR + "`  Zitiert die Nachricht mit der angegebenen ID" + '\n' +
            "`ping           " + SEPARATOR + "`  Pong" + '\n' +
            "`uptime         " + SEPARATOR + "`  Zeigt seit wann der Bot online ist" + '\n' +
            "`git            " + SEPARATOR + "`  Quellcode des Bots";

    private final LocalDateTime startupTimestamp;
    private final String prefix;
    private final String ownerID;

    public StandardCommands(IDiscordClient dClient) {
        client = dClient;

        startupTimestamp = LocalDateTime.now();

        // Prefix und Owner ID aus Config-Datei auslesen
        final String configFileContent = Util.readFile(CONFIG_PATH);
        if (configFileContent == null) {
            throw new RuntimeException("[ERROR] Config-Datei konnte nicht gelesen werden!");
        }
        final JSONObject json = new JSONObject(configFileContent);
        this.prefix = json.getString("prefix");
        this.ownerID = json.getString("owner");
    }

    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) throws DiscordException, RateLimitException, MissingPermissionsException, InterruptedException {
        final IMessage message = event.getMessage();
        final String messageContent = message.getContent().toLowerCase();

        if (messageContent.startsWith(prefix + "help")) {
            command_Help(message);
        }
        else if (messageContent.startsWith(prefix + "ping")) {
            command_Ping(message);
        }
        else if (messageContent.startsWith(prefix + "shutdown")) {
            command_Shutdown(message);
        }
        else if (messageContent.startsWith(prefix + "quote")) {
            command_Quote(message);
        }
        else if (messageContent.startsWith(prefix + "uptime")) {
            command_Uptime(message);
        }
        else if (messageContent.startsWith(prefix + "setusername")) {
            command_SetUsername(message);
        }
        else if (messageContent.startsWith(prefix + "git")) {
            command_Git(message);
        }
    }

    /**********
     * COMMANDS
     **********/

    private void command_Help(final IMessage message) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(new Color(114, 137, 218));
        embedBuilder.appendField(MODULE_NAME, COMMANDS, false);

        final EmbedObject embedObject = embedBuilder.build();

        Util.sendBufferedEmbed(message.getChannel(), embedObject);
    }

    private void command_Quote(final IMessage commandMessage) {
        if (!commandMessage.getContent().contains(" ")) {
            Util.sendMessage(commandMessage.getChannel(), "Keine ID angegeben!");
            return;
        }

        commandMessage.delete();

        final IUser commandUser = commandMessage.getAuthor();
        final IGuild commandGuild = commandMessage.getGuild();
        final String quoteID = Util.getContext(commandMessage.getContent());
        final IMessage quoteMessage = commandGuild.getMessageByID(quoteID);
        if (quoteMessage == null) {
            Util.sendMessage(commandMessage.getChannel(), "Nachricht mit der ID `" + quoteID + "` nicht gefunden!");
            return;
        }
        final IUser quoteAuthor = quoteMessage.getAuthor();
        final IRole quoteAuthorTopRole = Util.getTopRole(quoteAuthor, quoteMessage.getGuild());

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withAuthorIcon(quoteAuthor.getAvatarURL());
        embedBuilder.withAuthorName(quoteAuthor.getDisplayName(quoteMessage.getGuild()));
        embedBuilder.withDesc(quoteMessage.getContent());
        embedBuilder.withFooterText("Zitiert von " + commandUser.getName() + '#' + commandUser.getDiscriminator());
        embedBuilder.withColor(quoteAuthorTopRole.getColor());

        EmbedObject embedObject = embedBuilder.build();

        Util.sendBufferedEmbed(commandMessage.getChannel(), embedObject);
    }

    private void command_Ping(final IMessage message) {
        Util.sendMessage(message.getChannel(), "**Pong!**");
    }

    private void command_Uptime(final IMessage message) {
        final DateTimeFormatter timeStampFormatter = DateTimeFormatter.ofPattern("dd.MM. | HH:mm");
        Util.sendMessage(message.getChannel(), "Online seit: " + startupTimestamp.format(timeStampFormatter));
    }

    private void command_SetUsername(final IMessage message) {
        if (!message.getAuthor().getID().equals(ownerID)) {
            return;
        }

        final String context = Util.getContext(message.getContent());
        try {
            client.changeUsername(context);
            Util.sendMessage(message.getChannel(), ":white_check_mark: Neuer Username gesetzt: " + context);
        }
        catch (RateLimitException e) {
            System.err.println("[ERR] Ratelimited!");
            Util.sendMessage(message.getChannel(), ":x: Ratelimited!");
        }
    }

    private void command_Shutdown(final IMessage message) throws DiscordException, InterruptedException {
        if (!message.getAuthor().getID().equals(ownerID)) {
            return;
        }

        System.out.println("[INFO] Shutting down...");
        Util.sendMessage(message.getChannel(), "Shutting down... :zzz:");

        Thread.sleep(1000);

        client.logout();

        while (client.isLoggedIn()) {
            Thread.sleep(100);
        }

        System.exit(0);
    }

    private void command_Git(final IMessage message) {
        Util.sendMessage(message.getChannel(), "https://github.com/Nikos410/de-DiscordBot/");
    }
}
