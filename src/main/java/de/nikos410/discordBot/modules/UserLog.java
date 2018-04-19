package de.nikos410.discordBot.modules;

import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.discord.DiscordIO;
import de.nikos410.discordBot.util.io.IOUtil;
import de.nikos410.discordBot.modular.annotations.CommandModule;
import de.nikos410.discordBot.modular.CommandPermissions;
import de.nikos410.discordBot.modular.annotations.CommandSubscriber;

import org.json.JSONException;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserBanEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserLeaveEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

@CommandModule(moduleName = "Userlog", commandOnly = false)
public class UserLog {
    private final static Path USERLOG_PATH = Paths.get("data/userLog.json");

    private final DiscordBot bot;

    private JSONObject jsonUserLog;
    private IChannel userLogChannel;
    private boolean isEnabled;

    private IMessage purgeCommandMessage;

    private Logger log = LoggerFactory.getLogger(UserLog.class);

    public UserLog (final DiscordBot bot) {
        this.bot = bot;
    }

    @EventSubscriber
    public void onStartup(ReadyEvent event) {
        // UserLog Kanal auslesen
        final String userLogFileContent = IOUtil.readFile(USERLOG_PATH);
        if (userLogFileContent == null) {
            log.error("Could not read configuration file. Deactivating module.");
            bot.unloadModule("Userlog");
            return;
        }
        jsonUserLog = new JSONObject(userLogFileContent);

        this.isEnabled = jsonUserLog.getBoolean("on");
        try {
            final long channelID = jsonUserLog.getLong("channel");
            this.userLogChannel = bot.client.getChannelByID(channelID);

            if (this.userLogChannel == null) {
                log.error("Invalid Userlog channel. Deactivating module.");
                bot.unloadModule("Userlog");
            }
        }
        catch (JSONException e) {
            log.error("Invalid Userlog channel. Deactivating module.");
            bot.unloadModule("Userlog");
        }
    }

    @EventSubscriber
    public void onUserJoin(UserJoinEvent event) {
        if (this.isEnabled) {
            this.userJoinNotify(event.getUser());
        }
    }

    @EventSubscriber
    public void onUnserLeave(UserLeaveEvent event) {
        if (this.isEnabled) {
            this.userLeaveNotify(event.getUser());
        }
    }

    @EventSubscriber
    public void onUserBan(UserBanEvent event) {
        if (this.isEnabled) {
            this.userBanNotify(event.getUser());
        }
    }

    private void userJoinNotify(final IUser user) {
        final LocalDateTime joinTimeStamp = user.getCreationDate();
        int joinedDays = (int)joinTimeStamp.until(LocalDateTime.now(), ChronoUnit.DAYS);

        // String für Embed
        String embedString = String.format("**Name:** %s#%s \n" +
                "**ID:** %s \n" +
                "**Discord beigetreten:** vor %s Tagen",
                user.getName(), user.getDiscriminator(),
                user.getStringID(),
                joinedDays);

        if (joinedDays <= 1) {
            embedString = embedString + "\n:exclamation: Neuer Nutzer!";
        }

        // Embed
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.appendField(":white_check_mark: Nutzer ist dem Server beigetreten!", embedString, false);
        embedBuilder.withThumbnail(user.getAvatarURL());
        embedBuilder.withFooterText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM. | HH:mm")));
        embedBuilder.withColor(new Color(119, 178, 85));

        final EmbedObject embedObject = embedBuilder.build();

        DiscordIO.sendEmbed(userLogChannel, embedObject);
    }

    private void userLeaveNotify(final IUser user) {
        // String für Embed
        String embedString = String.format("**Name:** %s#%s \n" +
                        "**ID:** %s",
                user.getName(), user.getDiscriminator(),
                user.getStringID());

        final EmbedBuilder embedBuilder = new EmbedBuilder();
        final DateTimeFormatter timeStampFormatter = DateTimeFormatter.ofPattern("dd.MM. | HH:mm");

        embedBuilder.withThumbnail(user.getAvatarURL());
        embedBuilder.appendField(":x: Nutzer hat den Server verlassen!", embedString, false);
        embedBuilder.withFooterText(LocalDateTime.now().format(timeStampFormatter));
        embedBuilder.withColor(new Color(221, 46, 68));

        final EmbedObject embedObject = embedBuilder.build();

        DiscordIO.sendEmbed(userLogChannel, embedObject);
    }

    private void userBanNotify(final IUser user) {
        // String für Embed
        String embedString = String.format("**Name:** %s#%s \n" +
                        "**ID:** %s",
                user.getName(), user.getDiscriminator(),
                user.getStringID());

        final EmbedBuilder embedBuilder = new EmbedBuilder();
        final DateTimeFormatter timeStampFormatter = DateTimeFormatter.ofPattern("dd.MM. | HH:mm");

        embedBuilder.withThumbnail(user.getAvatarURL());
        embedBuilder.appendField(":hammer: Nutzer gebannt!", embedString, false);
        embedBuilder.withFooterText(LocalDateTime.now().format(timeStampFormatter));

        final EmbedObject embedObject = embedBuilder.build();

        DiscordIO.sendEmbed(userLogChannel, embedObject);
    }


    @CommandSubscriber(command = "userlog", help = "Zeigt Userlog-Konfuguration an", permissionLevel = CommandPermissions.ADMIN)
    public void command_Userlog(final IMessage message) {
        DiscordIO.sendMessage(message.getChannel(), String.format("Kanal: %s \nEnabled: `%s`", userLogChannel.mention(), isEnabled));
    }

    @CommandSubscriber(command = "userlog_channel", help = "Kanal für Userlog ändern", permissionLevel = CommandPermissions.ADMIN)
    public void command_Userlog_Channel(final IMessage message, final String channelID) {
        if (jsonUserLog.has("channel")) {
            jsonUserLog.remove("channel");
        }
        jsonUserLog.put("channel", channelID);
        saveUserLogJSON();

        this.userLogChannel = this.bot.client.getChannelByID(Long.parseLong(channelID));
        if (userLogChannel == null) {
            DiscordIO.sendMessage(message.getChannel(), String.format(":x: Kanal mit der ID `%s` nicht gefunden!", channelID));
        }
        else {
            DiscordIO.sendMessage(message.getChannel(), String.format(":white_check_mark: Neuer Kanal: %s", userLogChannel.mention()));
        }
    }

    @CommandSubscriber(command = "userlog_enable", help = "Userlog aktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_Userlog_Enable(final IMessage message) {
        this.isEnabled = true;
        if (jsonUserLog.has("on")) {
            jsonUserLog.remove("on");
        }
        jsonUserLog.put("on", true);
        saveUserLogJSON();

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Aktiviert!");
    }

    @CommandSubscriber(command = "userlog_disable", help = "Userlog deaktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_Userlog_Disable(final IMessage message) {
        this.isEnabled = false;
        if (jsonUserLog.has("on")) {
            jsonUserLog.remove("on");
        }
        jsonUserLog.put("on", false);
        saveUserLogJSON();

        DiscordIO.sendMessage(message.getChannel(), ":x: Deaktiviert!");
    }

    @CommandSubscriber(command = "userlog_test", help = "Userlog-Ausgabe testen", permissionLevel = CommandPermissions.ADMIN)
    public void command_Userlog_Test(final IMessage message) {
        final IUser user = message.getAuthor();
        userJoinNotify(user);
        userLeaveNotify(user);
        userBanNotify(user);
    }

    private void saveUserLogJSON() {
        log.debug("Saving UserLog file.");

        final String jsonOutput = jsonUserLog.toString(4);
        IOUtil.writeToFile(USERLOG_PATH, jsonOutput);

        jsonUserLog = new JSONObject(jsonOutput);
    }
}
