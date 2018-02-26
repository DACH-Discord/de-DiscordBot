package de.nikos410.discordBot.modules;


import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.annotations.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.annotations.CommandSubscriber;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.reaction.ReactionAddEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserBanEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserLeaveEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.*;

@CommandModule(moduleName = "Modzeugs", commandOnly = false)
public class ModStuff {
    private final DiscordBot bot;

    private final long modlogChannelID;
    private final long muteRoleID;

    private final static Path MUTED_PATH = Paths.get("data/muted.json");
    private JSONObject mutedJSON;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private List<String> mutedUsers = new LinkedList<>();

    public ModStuff (final DiscordBot bot) {
        this.bot = bot;
        final IDiscordClient client = bot.client;

        this.muteRoleID = bot.configJSON.getLong("muteRole");
        this.modlogChannelID = bot.configJSON.getLong("modLogChannelID");
    }

    @CommandSubscriber(command = "kick", help = "Kickt den angegebenen Nutzer mit der angegeben Nachricht vom Server",
            pmAllowed = false)
    public void command_Kick(final IMessage message, final String kickUserString, String customMessage) {
        if (this.bot.getUserPermissionLevel(message.getAuthor(), message.getGuild()) >= CommandPermissions.MODERATOR) {

            final List<IUser> mentions = message.getMentions();
            if (mentions.size() <1) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: Kein Nutzer angegeben!");
                return;
            }
            else if (mentions.size() > 1) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: In der Nachricht keine Nutzer erwähnen!");
                return;
            }

            final IUser kickUser = mentions.get(0);
            if (kickUser == null) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: Nutzer nicht gefunden!");
                return;
            }

            if (customMessage.isEmpty()) {
                customMessage = "kein";
            }

            final String kickMessage = "**Du wurdest gekickt!** (Du kannst dem Server jedoch erneut beitreten)" +
                    "\nHinweis: _" + customMessage + " _";

            Util.sendPM(kickUser, kickMessage);
            message.getGuild().kickUser(kickUser, customMessage);

            message.addReaction(ReactionEmoji.of("\uD83D\uDEAA")); // :door:
            //Util.sendMessage(message.getChannel(), ":door:");

            // Modlog
            IChannel modLogChannel = message.getGuild().getChannelByID(this.modlogChannelID);
            final String modLogMessage = String.format("**%s** hat Nutzer **%s** vom Server **gekickt**. \nHinweis: _%s _", Util.makeUserString(message.getAuthor(), message.getGuild()),
                    Util.makeUserString(kickUser, message.getGuild()),  customMessage);
            Util.sendMessage(modLogChannel, modLogMessage);
        }
        else {
            message.getGuild().kickUser(message.getAuthor());
            message.addReaction(ReactionEmoji.of("tja", 401835325434888192L));
        }
    }

    @CommandSubscriber(command = "ban", help = "Bannt den angegebenen Nutzer mit der angegeben Nachricht vom Server",
            pmAllowed = false)
    public void command_Ban(final IMessage message, final String banUserString, String customMessage) {
        if (this.bot.getUserPermissionLevel(message.getAuthor(), message.getGuild()) >= CommandPermissions.MODERATOR) {

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

            if (customMessage.isEmpty()) {
                customMessage = "kein";
            }

            final String banMessage = "**Du wurdest gebannt!** \nHinweis: _" + customMessage + " _";

            Util.sendPM(banUser, banMessage);
            message.getGuild().banUser(banUser, customMessage);

            //Util.sendMessage(message.getChannel(), ":hammer:");
            message.addReaction(ReactionEmoji.of("\uD83D\uDD28")); // :hammer:

            // Modlog
            IChannel modLogChannel = message.getGuild().getChannelByID(this.modlogChannelID);
            final String modLogMessage = String.format("**%s** hat Nutzer **%s** vom Server **gebannt**. \nHinweis: _%s _", Util.makeUserString(message.getAuthor(), message.getGuild()),
                    Util.makeUserString(banUser, message.getGuild()),  customMessage);
            Util.sendMessage(modLogChannel, modLogMessage);
        }
        else {
            message.getGuild().kickUser(message.getAuthor());
            message.addReaction(ReactionEmoji.of("tja", 401835325434888192L));
        }
    }

    @EventSubscriber
    public void onUserBanned (final UserBanEvent event) {
        IChannel modLogChannel = event.getGuild().getChannelByID(this.modlogChannelID);

        final String modLogMessage = String.format("**%s** wurde vom Server **gebannt**.",
                Util.makeUserString(event.getUser(), event.getGuild()));
        Util.sendMessage(modLogChannel, modLogMessage);
    }

    @CommandSubscriber(command = "mute", help = "Einen Nutzer für eine bestimmte Zeit muten", pmAllowed = false,
            permissionLevel = CommandPermissions.MODERATOR)
    public void command_Mute(final IMessage message, final String muteUserString, final String muteDurationInput) {

        final List<IUser> mentions = message.getMentions();
        if (mentions.size() <1) {
            Util.sendMessage(message.getChannel(), ":x: Fehler: Kein Nutzer angegeben!");
            return;
        }
        else if (mentions.size() > 1) {
            Util.sendMessage(message.getChannel(), ":x: Fehler: mehrere Nutzer erwähnt");
            return;
        }

        final IUser muteUser = mentions.get(0);
        if (muteUser == null) {
            Util.sendMessage(message.getChannel(), ":x: Fehler: Nutzer nicht gefunden!");
            return;
        }

        IRole muteRole = message.getGuild().getRoleByID(muteRoleID);
        muteUser.addRole(muteRole);
        mutedUsers.add(muteUser.getStringID());

        Runnable unmuteTask = () -> {
            mutedUsers.remove(muteUser.getStringID());
            muteUser.removeRole(muteRole);
            System.out.println("Unmuted user " + muteUser.getName() + '#' + muteUser.getDiscriminator() + ".");
        };

        Pattern pattern = Pattern.compile("(\\d+)\\s?([smhd])\\s?(.*)");
        Matcher matcher = pattern.matcher(muteDurationInput);

        if (!matcher.matches()) {
            Util.sendMessage(message.getChannel(), "Ungültige Eingabe! Mögliche Zeitformate sind s, m, h und d.");
            return;
        }
        final int muteDuration = Integer.parseInt(matcher.group(1));
        final String muteDurationUnit = matcher.group(2);
        String customMessage = matcher.group(3);

        TimeUnit muteDurationTimeUnit = null;
        switch (muteDurationUnit) {
            case "S": muteDurationTimeUnit = TimeUnit.SECONDS;
                break;
            case "s": muteDurationTimeUnit = TimeUnit.SECONDS;
                break;

            case "M": muteDurationTimeUnit = TimeUnit.MINUTES;
                break;
            case "m": muteDurationTimeUnit = TimeUnit.MINUTES;
                break;

            case "H": muteDurationTimeUnit = TimeUnit.HOURS;
                break;
            case "h": muteDurationTimeUnit = TimeUnit.HOURS;
                break;

            case "D": muteDurationTimeUnit = TimeUnit.DAYS;
                break;
            case "d": muteDurationTimeUnit = TimeUnit.DAYS;
                break;

            default: muteDurationTimeUnit = TimeUnit.SECONDS;
        }

        scheduler.schedule(unmuteTask, muteDuration, muteDurationTimeUnit);

        //Util.sendMessage(message.getChannel(), "Nutzer für " + muteDuration + ' ' + muteDurationUnit + " gemuted.");
        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:

        if (customMessage.isEmpty()) {
            customMessage = "kein";
        }

        final String muteMessage = "**Du wurdest für " + muteDuration + ' ' + muteDurationUnit +
                " gemuted!** \nHinweis: _" + customMessage + " _";

        if (!muteUser.isBot()) {
            Util.sendPM(muteUser, muteMessage);
        }

        // Modlog
        IChannel modLogChannel = message.getGuild().getChannelByID(this.modlogChannelID);
        final String modLogMessage = String.format("**%s** hat Nutzer **%s** für %s %s **gemuted**. \nHinweis: _%s _",
                Util.makeUserString(message.getAuthor(), message.getGuild()),
                Util.makeUserString(muteUser, message.getGuild()), muteDuration, muteDurationUnit, customMessage);
        Util.sendMessage(modLogChannel, modLogMessage);
    }

    @CommandSubscriber(command = "selfmute", help = "Mute dich selber für die angegebene Zeit", pmAllowed = false)
    public void command_Selfmute(final IMessage message, final String muteDurationInput) {
        final IUser muteUser = message.getAuthor();

        final IRole muteRole = message.getGuild().getRoleByID(muteRoleID);

        Runnable unmuteTask = () -> {
            mutedUsers.remove(muteUser.getStringID());
            muteUser.removeRole(muteRole);
            System.out.println("Unmuted user " + muteUser.getName() + '#' + muteUser.getDiscriminator() + ".");
        };

        Pattern pattern = Pattern.compile("(\\d+)\\s?([smhd]).*");
        Matcher matcher = pattern.matcher(muteDurationInput);

        if (!matcher.matches()) {
            Util.sendMessage(message.getChannel(), "Ungültige Eingabe! Mögliche Zeitformate sind s, m, h und d.");
            return;
        }
        final int muteDuration = Integer.parseInt(matcher.group(1));
        final String muteDurationUnit = matcher.group(2);

        TimeUnit muteDurationTimeUnit = null;
        switch (muteDurationUnit) {
            case "S": muteDurationTimeUnit = TimeUnit.SECONDS;
                break;
            case "s": muteDurationTimeUnit = TimeUnit.SECONDS;
                break;

            case "M": muteDurationTimeUnit = TimeUnit.MINUTES;
                break;
            case "m": muteDurationTimeUnit = TimeUnit.MINUTES;
                break;

            case "H": muteDurationTimeUnit = TimeUnit.HOURS;
                break;
            case "h": muteDurationTimeUnit = TimeUnit.HOURS;
                break;

            case "D": muteDurationTimeUnit = TimeUnit.DAYS;
                break;
            case "d": muteDurationTimeUnit = TimeUnit.DAYS;
                break;

            default: muteDurationTimeUnit = TimeUnit.SECONDS;
        }

        muteUser.addRole(muteRole);
        mutedUsers.add(muteUser.getStringID());

        scheduler.schedule(unmuteTask, muteDuration, muteDurationTimeUnit);

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:

        System.out.println("User " + muteUser.getName() + '#' + muteUser.getDiscriminator() + " muted themself for " + muteDuration +
                ' ' + muteDurationUnit);

    }

    @EventSubscriber
    public void onUserJoin(final UserJoinEvent event) {
        final IUser user = event.getUser();
        if (mutedUsers.contains(user.getStringID())) {
            IRole muteRole = event.getGuild().getRoleByID(muteRoleID);
            user.addRole(muteRole);
        }
    }

    @EventSubscriber
    public void emojiInfoEvent (final ReactionAddEvent event) {
        System.out.println(event.getReaction().getEmoji().getName());
        System.out.println(event.getReaction().getEmoji().getStringID());
    }
}
