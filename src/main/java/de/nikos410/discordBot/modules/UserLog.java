package de.nikos410.discordBot.modules;

import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import de.nikos410.discordBot.util.discord.DiscordIO;
import de.nikos410.discordBot.util.discord.GuildOperations;
import de.nikos410.discordBot.util.io.IOUtil;
import de.nikos410.discordBot.framework.annotations.CommandModule;
import de.nikos410.discordBot.framework.CommandPermissions;
import de.nikos410.discordBot.framework.annotations.CommandSubscriber;

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

@CommandModule(moduleName = "Userlog", commandOnly = false)
public class UserLog {
    private final static Path USERLOG_PATH = Paths.get("data/userLog.json");

    private JSONObject userlogJSON;

    private final static Logger LOG = LoggerFactory.getLogger(UserLog.class);

    public UserLog () {
        final String jsonContent = IOUtil.readFile(USERLOG_PATH);
        userlogJSON = new JSONObject(jsonContent);
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

        DiscordIO.sendEmbed(channel, embedObject);
    }

    private void userLeaveNotify(final IUser user, final IChannel channel) {
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

        DiscordIO.sendEmbed(channel, embedObject);
    }

    private void userBanNotify(final IUser user, final IChannel channel) {
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

        DiscordIO.sendEmbed(channel, embedObject);
    }

    @CommandSubscriber(command = "setUserlogChannel", help = "Kanal für Userlog ändern", permissionLevel = CommandPermissions.ADMIN)
    public void command_SetUserlogChannel(final IMessage message, final String channel) {
        final IChannel modlogChannel;
        final List<IChannel> channelMentions = message.getChannelMentions();

        if (GuildOperations.hasChannelByID(message.getGuild(), channel)) {
            // Kanal ID wurde als Parameter angegeben
            modlogChannel = message.getGuild().getChannelByID(Long.parseLong(channel));
        }
        else if (channelMentions.size() == 1) {
            // ein Kanal wurde erwähnt
            modlogChannel = channelMentions.get(0);
        }
        else {
            // Kein Kanal angegeben
            DiscordIO.sendMessage(message.getChannel(), "Kein gültiger Kanal angegeben!");
            return;
        }

        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        guildJSON.put("channel", modlogChannel.getLongID());
        saveUserLogJSON();

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
    }

    @CommandSubscriber(command = "enableUserlog", help = "Userlog aktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_EnableUserlog(final IMessage message) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (!guildJSON.has("channel")) {
            DiscordIO.sendMessage(message.getChannel(), "Es ist noch kein Kanal hinterlegt!");
        }

        guildJSON.put("on", true);
        saveUserLogJSON();

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
    }

    @CommandSubscriber(command = "disableUserlog", help = "Userlog deaktivieren", permissionLevel = CommandPermissions.ADMIN)
    public void command_DisableUserlog(final IMessage message) {
        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        guildJSON.put("on", false);
        saveUserLogJSON();

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
    }

    @CommandSubscriber(command = "userlogTest", help = "Userlog-Ausgabe testen", permissionLevel = CommandPermissions.ADMIN)
    public void command_UserlogTest(final IMessage message) {
        final IUser user = message.getAuthor();

        final IGuild guild = message.getGuild();
        final JSONObject guildJSON = getJSONForGuild(guild);

        if (!guildJSON.has("channel")) {
            DiscordIO.sendMessage(message.getChannel(), "Fehler! Kein Kanal hinterlegt!");
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
