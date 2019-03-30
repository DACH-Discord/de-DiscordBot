package de.nikos410.discordbot.modules;

import de.nikos410.discordbot.framework.CommandModule;
import de.nikos410.discordbot.framework.PermissionLevel;
import de.nikos410.discordbot.framework.annotations.CommandSubscriber;
import de.nikos410.discordbot.util.discord.GuildUtils;
import de.nikos410.discordbot.util.io.IOUtil;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.guild.member.UserBanEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserLeaveEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class UserLog extends CommandModule {
    private static final Logger LOG = LoggerFactory.getLogger(UserLog.class);

    private static final Path USERLOG_PATH = Paths.get("data/userLog.json");
    private JSONObject userlogJSON;

    @Override
    public String getDisplayName() {
        return "Nutzer-Log";
    }

    @Override
    public String getDescription() {
        return "Loggt in einen konfigurierbaren Kanal, welche Nutzer den Server betreten und verlassen.";
    }

    @Override
    public boolean hasEvents() {
        return true;
    }

    @Override
    public void init() {
        final String jsonContent = IOUtil.readFile(USERLOG_PATH);
        this.userlogJSON = new JSONObject(jsonContent);
    }

    @EventSubscriber
    public void onUserJoin(UserJoinEvent event) {
        final IGuild guild = event.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (guildJSON.has("on") && guildJSON.getBoolean("on") && guildJSON.has("channel")) {
            final long channelID = guildJSON.getLong("channel");
            final IChannel channel = guild.getChannelByID(channelID);
            if (channel != null) {
                userJoinNotify(event.getUser(), channel);
            }
        }
    }

    @EventSubscriber
    public void onUnserLeave(UserLeaveEvent event) {
        final IGuild guild = event.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (guildJSON.has("on") && guildJSON.getBoolean("on") && guildJSON.has("channel")) {
            final long channelID = guildJSON.getLong("channel");
            final IChannel channel = guild.getChannelByID(channelID);
            if (channel != null) {
                userLeaveNotify(event.getUser(), channel);
            }
        }
    }

    @EventSubscriber
    public void onUserBan(UserBanEvent event) {
        final IGuild guild = event.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (guildJSON.has("on") && guildJSON.getBoolean("on") && guildJSON.has("channel")) {
            final long channelID = guildJSON.getLong("channel");
            final IChannel channel = guild.getChannelByID(channelID);
            if (channel != null) {
                userBanNotify(event.getUser(), channel);
            }
        }
    }

    private void userJoinNotify(final IUser user, final IChannel channel) {
        final Instant joinTimeStamp = user.getCreationDate();
        final long joinedDays = joinTimeStamp.until(Instant.now(), ChronoUnit.DAYS);

        // String für Embed
        String embedString = String.format("**Name:** %s#%s %n" +
                "**ID:** %s %n" +
                "**Discord beigetreten:** vor %s Tagen",
                user.getName(), user.getDiscriminator(),
                user.getStringID(),
                joinedDays);

        if (joinedDays <= 1) {
            embedString = embedString + String.format("%n:exclamation: Neuer Nutzer!");
        }

        // Embed
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.appendField(":white_check_mark: Nutzer ist dem Server beigetreten!", embedString, false);
        embedBuilder.withThumbnail(user.getAvatarURL());
        embedBuilder.withFooterText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM. | HH:mm")));
        embedBuilder.withColor(new Color(119, 178, 85));

        final EmbedObject embedObject = embedBuilder.build();

        messageService.sendEmbed(channel, embedObject);
    }

    private void userLeaveNotify(final IUser user, final IChannel channel) {
        // String für Embed
        String embedString = String.format("**Name:** %s#%s %n" +
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

        messageService.sendEmbed(channel, embedObject);
    }

    private void userBanNotify(final IUser user, final IChannel channel) {
        // String für Embed
        String embedString = String.format("**Name:** %s#%s %n" +
                        "**ID:** %s",
                user.getName(), user.getDiscriminator(),
                user.getStringID());

        final EmbedBuilder embedBuilder = new EmbedBuilder();
        final DateTimeFormatter timeStampFormatter = DateTimeFormatter.ofPattern("dd.MM. | HH:mm");

        embedBuilder.withThumbnail(user.getAvatarURL());
        embedBuilder.appendField(":hammer: Nutzer gebannt!", embedString, false);
        embedBuilder.withFooterText(LocalDateTime.now().format(timeStampFormatter));

        final EmbedObject embedObject = embedBuilder.build();

        messageService.sendEmbed(channel, embedObject);
    }

    @CommandSubscriber(command = "setUserlogChannel", help = "Kanal für Userlog ändern",
            permissionLevel = PermissionLevel.ADMIN)
    public void command_setUserlogChannel(final IMessage message, final String channel) {
        final IChannel modlogChannel;
        final List<IChannel> channelMentions = message.getChannelMentions();

        if (GuildUtils.channelExists(message.getGuild(), channel)) {
            // Kanal ID wurde als Parameter angegeben
            modlogChannel = message.getGuild().getChannelByID(Long.parseLong(channel));
        }
        else if (channelMentions.size() == 1) {
            // ein Kanal wurde erwähnt
            modlogChannel = channelMentions.get(0);
        }
        else {
            // Kein Kanal angegeben
            messageService.sendMessage(message.getChannel(), "Kein gültiger Kanal angegeben!");
            return;
        }

        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        guildJSON.put("channel", modlogChannel.getLongID());
        saveUserLogJSON();

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
    }

    @CommandSubscriber(command = "enableUserlog", help = "Userlog aktivieren",
            permissionLevel = PermissionLevel.ADMIN)
    public void command_enableUserlog(final IMessage message) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (!guildJSON.has("channel")) {
            messageService.sendMessage(message.getChannel(), "Es ist noch kein Kanal hinterlegt!");
        }

        guildJSON.put("on", true);
        saveUserLogJSON();

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
    }

    @CommandSubscriber(command = "disableUserlog", help = "Userlog deaktivieren",
            permissionLevel = PermissionLevel.ADMIN)
    public void command_disableUserlog(final IMessage message) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        guildJSON.put("on", false);
        saveUserLogJSON();

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
    }

    @CommandSubscriber(command = "userlogTest", help = "Userlog-Ausgabe testen",
            permissionLevel = PermissionLevel.ADMIN)
    public void command_userlogTest(final IMessage message) {
        final IUser user = message.getAuthor();

        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (!guildJSON.has("channel")) {
            messageService.sendMessage(message.getChannel(), "Fehler! Kein Kanal hinterlegt!");
            return;
        }

        final long channelID = guildJSON.getLong("channel");
        final IChannel channel = guild.getChannelByID(channelID);
        if (channel != null) {
            userJoinNotify(user, channel);
            userLeaveNotify(user, channel);
            userBanNotify(user, channel);
        }
    }

    private JSONObject getJSONForGuild (final IGuild guild) {
        if (userlogJSON.has(guild.getStringID())) {
            return userlogJSON.getJSONObject(guild.getStringID());
        }
        else {
            final JSONObject guildJSON = new JSONObject();
            userlogJSON.put(guild.getStringID(), guildJSON);
            return guildJSON;
        }
    }

    private void saveUserLogJSON() {
        LOG.debug("Saving UserLog file.");

        final String jsonOutput = userlogJSON.toString(4);
        IOUtil.writeToFile(USERLOG_PATH, jsonOutput);

        userlogJSON = new JSONObject(jsonOutput);
    }
}
