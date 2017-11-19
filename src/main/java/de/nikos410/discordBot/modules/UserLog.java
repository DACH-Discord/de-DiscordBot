package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.annotations.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.annotations.CommandSubscriber;
import org.json.JSONObject;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserBanEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserLeaveEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@CommandModule(moduleName = "Userlog", commandOnly = false)
public class UserLog {
    private final static Path USERLOG_PATH = Paths.get("data/userLog.json");

    private final DiscordBot bot;

    private JSONObject jsonUserLog;
    private IChannel userLogChannel;
    private boolean isEnabled;

    private IMessage purgeCommandMessage;


    public UserLog (final DiscordBot bot) {
        this.bot = bot;
    }

    @EventSubscriber
    public void onStartup(ReadyEvent event) {
        // UserLog Kanal auslesen
        final String userLogFileContent = Util.readFile(USERLOG_PATH);
        if (userLogFileContent == null) {
            throw new RuntimeException("[ERROR] Could not read userLog File!");
        }
        jsonUserLog = new JSONObject(userLogFileContent);

        this.isEnabled = jsonUserLog.getBoolean("on");
        final long channelID = jsonUserLog.getLong("channel");
        this.userLogChannel = bot.client.getChannelByID(channelID);
        if (this.userLogChannel == null) {
            System.err.println("[ERR] Invalid UserLog Channel!");
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
        String embedString = "**Name:** " + user.getName() + '#' + user.getDiscriminator() + '\n' +
                "**ID:** " + user.getStringID() + '\n' +
                "**Discord beigetreten:** vor " + joinedDays + " Tagen";

        if (joinedDays <= 1) {
            embedString = embedString + '\n' + ":exclamation: Neuer Nutzer! :exclamation:";
        }

        // Embed
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.appendField(":white_check_mark: Nutzer ist dem Server beigetreten!", embedString,
                false);
        embedBuilder.withThumbnail(user.getAvatarURL());
        embedBuilder.withFooterText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM. | HH:mm")));
        embedBuilder.withColor(new Color(119, 178, 85));

        final EmbedObject embedObject = embedBuilder.build();

        Util.sendBufferedEmbed(userLogChannel, embedObject);
    }

    private void userLeaveNotify(final IUser user) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();
        final DateTimeFormatter timeStampFormatter = DateTimeFormatter.ofPattern("dd.MM. | HH:mm");

        embedBuilder.withThumbnail(user.getAvatarURL());
        embedBuilder.appendField(":x: Nutzer hat den Server verlassen!",
                "**Name:** " + user.getName() + '#' + user.getDiscriminator() + '\n' +
                        "**ID:** " + user.getStringID(),
                false);
        embedBuilder.withFooterText(LocalDateTime.now().format(timeStampFormatter));
        embedBuilder.withColor(new Color(221, 46, 68));

        final EmbedObject embedObject = embedBuilder.build();

        Util.sendBufferedEmbed(userLogChannel, embedObject);
    }

    private void userBanNotify(final IUser user) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();
        final DateTimeFormatter timeStampFormatter = DateTimeFormatter.ofPattern("dd.MM. | HH:mm");

        embedBuilder.withThumbnail(user.getAvatarURL());
        embedBuilder.appendField(":hammer: Nutzer gebannt!",
                "**Name:** " + user.getName() + '#' + user.getDiscriminator() + '\n' +
                        "**ID:** " + user.getStringID(),
                false);
        embedBuilder.withFooterText(LocalDateTime.now().format(timeStampFormatter));

        final EmbedObject embedObject = embedBuilder.build();

        Util.sendBufferedEmbed(userLogChannel, embedObject);
    }


    @CommandSubscriber(command = "userlog", help = "Zeigt Userlog-Konfuguration an", pmAllowed = true, permissionLevel = CommandPermissions.ADMIN)
    public void command_Userlog(final IMessage message) {
        final String reply = "Kanal: <#" + userLogChannel.getStringID() + ">" + '\n' +
                "Enabled: `" + isEnabled + '`';
        Util.sendMessage(message.getChannel(), reply);
    }

    @CommandSubscriber(command = "userlog_channel", help = "Kanal für Userlog ändern", pmAllowed = true, permissionLevel = CommandPermissions.ADMIN)
    public void command_Userlog_Channel(final IMessage message) {
        final String channelID = Util.getContext(message.getContent());
        if (jsonUserLog.has("channel")) {
            jsonUserLog.remove("channel");
        }
        jsonUserLog.put("channel", channelID);
        saveUserLogJSON();

        this.userLogChannel = this.bot.client.getChannelByID(Long.parseLong(channelID));
        if (userLogChannel == null) {
            System.err.print("[ERR] Invalid UserLog Channel!");
            Util.sendMessage(message.getChannel(), ":x: Kanal mit der ID `" + channelID + "` nicht gefunden!");
        }
        else {
            Util.sendMessage(message.getChannel(), ":white_check_mark: Neuer Kanal: " +
                    "<#" + userLogChannel.getStringID() + ">");
        }
    }

    @CommandSubscriber(command = "userlog_enable", help = "Userlog aktivieren", pmAllowed = true, permissionLevel = CommandPermissions.ADMIN)
    public void command_Userlog_Enable(final IMessage message) {
        this.isEnabled = true;
        if (jsonUserLog.has("on")) {
            jsonUserLog.remove("on");
        }
        jsonUserLog.put("on", true);
        saveUserLogJSON();

        Util.sendMessage(message.getChannel(), ":white_check_mark: Aktiviert!");
    }

    @CommandSubscriber(command = "userlog_disable", help = "Userlog deaktivieren", pmAllowed = true, permissionLevel = CommandPermissions.ADMIN)
    public void command_Userlog_Disable(final IMessage message) {
        this.isEnabled = false;
        if (jsonUserLog.has("on")) {
            jsonUserLog.remove("on");
        }
        jsonUserLog.put("on", false);
        saveUserLogJSON();

        Util.sendMessage(message.getChannel(), ":x: Deaktiviert!");
    }

    @CommandSubscriber(command = "userlog_test", help = "Userlog-Ausgabe testen", pmAllowed = true, permissionLevel = CommandPermissions.ADMIN)
    public void command_Userlog_Test(final IMessage message) {
        final IUser user = message.getAuthor();
        userJoinNotify(user);
        userLeaveNotify(user);
        userBanNotify(user);
    }

    @CommandSubscriber(command = "purge", help = "Nutzer vom Server kicken die seit 30 oder mehr Tagen offline waren",
            pmAllowed = false, permissionLevel = CommandPermissions.ADMIN)
    public void command_Purge(final IMessage message) {
        this.purgeCommandMessage = message;

        Util.sendSingleMessage(message.getChannel(), message.getGuild().getUsersToBePruned(30) +
                " Nutzer werden entfernt. Fortfahren? (y/n)");
    }

    @EventSubscriber
    public void onMessageReceived(final MessageReceivedEvent event) {
        if (this.purgeCommandMessage != null) {
            final IMessage inputMessage = event.getMessage();

            if (inputMessage.getAuthor().getLongID() == this.purgeCommandMessage.getAuthor().getLongID()) {
                final String inputMessageContent = inputMessage.getContent();

                if (inputMessage.getChannel().getLongID() == this.purgeCommandMessage.getChannel().getLongID()) {
                    if (inputMessageContent.equalsIgnoreCase("y")) {
                        // Purge
                        this.isEnabled = false;

                        final int userCount = this.purgeCommandMessage.getGuild().getUsersToBePruned(30);
                        this.purgeCommandMessage.getGuild().pruneUsers(30);
                        Util.sendMessage(this.purgeCommandMessage.getChannel(), userCount + " Nutzer entfernt. " + ":white_check_mark: ");

                        this.isEnabled = true;
                    }
                    else if (inputMessageContent.equalsIgnoreCase("n")) {
                        Util.sendMessage(this.purgeCommandMessage.getChannel(), ":white_check_mark:");
                    }
                }
            }
        }
    }

    private void saveUserLogJSON() {
        final String jsonOutput = jsonUserLog.toString(4);
        Util.writeToFile(USERLOG_PATH, jsonOutput);

        jsonUserLog = new JSONObject(jsonOutput);
    }
}
