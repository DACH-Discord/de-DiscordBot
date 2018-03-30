package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.annotations.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.annotations.CommandSubscriber;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.member.UserBanEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.*;

@CommandModule(moduleName = "Modzeugs", commandOnly = false)
public class ModStuff {
    private final DiscordBot bot;

    private final long modlogChannelID;
    private final long muteRoleID;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private HashMap<String, ScheduledFuture> mutedUsers = new HashMap<>();

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

        Pattern pattern = Pattern.compile("(\\d+)\\s?([smhd])\\s?(.*)");
        Matcher matcher = pattern.matcher(muteDurationInput);

        if (!matcher.matches()) {
            Util.sendMessage(message.getChannel(), "Ungültige Eingabe! Mögliche Zeitformate sind s, m, h und d.");
            return;
        }

        IRole muteRole = message.getGuild().getRoleByID(muteRoleID);
        muteUser.addRole(muteRole);
        System.out.println("Muted user " + Util.makeUserString(muteUser, message.getGuild()) + ".");

        // Wird ausgeführt, um Nutzer wieder zu entmuten
        Runnable unmuteTask = () -> {
            mutedUsers.remove(muteUser.getStringID());
            muteUser.removeRole(muteRole);
            System.out.println("Unmuted user " + Util.makeUserString(muteUser, message.getGuild()) + ".");
        };

        final int muteDuration = Integer.parseInt(matcher.group(1));
        final String muteDurationUnitString = matcher.group(2);
        ChronoUnit muteDurationUnit = parseChronoUnit(muteDurationUnitString);

        if (mutedUsers.containsKey(muteUser.getStringID())) {
            // Überprüfen, ob angegebener Zeitpunkt nach dem bisherigen Zeitpunkt liegt
            ScheduledFuture oldFuture = mutedUsers.get(muteUser.getStringID());

            LocalDateTime oldDateTime = LocalDateTime.now().plusSeconds(oldFuture.getDelay(TimeUnit.SECONDS));
            LocalDateTime newDateTime = LocalDateTime.now().plus(muteDuration, muteDurationUnit);

            if (newDateTime.isBefore(oldDateTime)) {
                // neuer Zeitpunkt ist vor altem -> nichts tun (längerer Mute bleibt bestehen)
                Util.sendMessage(message.getChannel(), "Nutzer ist bereits für einen längeren Zeitraum gemuted!");
                return;
            }
            else {
                // neuer Zeitpunkt ist nach altem -> neu schedulen
                mutedUsers.remove(muteUser.getStringID(), oldFuture);
                oldFuture.cancel(false);
            }
        }

        ScheduledFuture newFuture = scheduler.schedule(unmuteTask, muteDuration, chronoUnitToTimeUnit(muteDurationUnit));
        mutedUsers.put(muteUser.getStringID(), newFuture);

        message.addReaction(ReactionEmoji.of("\uD83D\uDD07")); // :mute:

        String customMessage = matcher.group(3);

        if (customMessage.isEmpty()) {
            customMessage = "kein";
        }

        final String muteMessage = "**Du wurdest für " + muteDuration + ' ' + muteDurationUnitString +
                " gemuted!** \nHinweis: _" + customMessage + " _";

        if (!muteUser.isBot()) {
            Util.sendPM(muteUser, muteMessage);
        }

        // Modlog
        IChannel modLogChannel = message.getGuild().getChannelByID(this.modlogChannelID);
        final String modLogMessage = String.format("**%s** hat Nutzer **%s** für %s %s **gemuted**. \nHinweis: _%s _",
                Util.makeUserString(message.getAuthor(), message.getGuild()),
                Util.makeUserString(muteUser, message.getGuild()), muteDuration, muteDurationUnitString, customMessage);
        Util.sendMessage(modLogChannel, modLogMessage);
    }

    @CommandSubscriber(command = "selfmute", help = "Schalte dich selber für die angegebene Zeit stumm", pmAllowed = false)
    public void command_Selfmute(final IMessage message, final String muteDurationInput) {
        IUser muteUser = message.getAuthor();

        Pattern pattern = Pattern.compile("(\\d+)\\s?([smhd])\\s?(.*)");
        Matcher matcher = pattern.matcher(muteDurationInput);

        if (!matcher.matches()) {
            Util.sendMessage(message.getChannel(), "Ungültige Eingabe! Mögliche Zeitformate sind s, m, h und d.");
            return;
        }

        IRole muteRole = message.getGuild().getRoleByID(muteRoleID);
        muteUser.addRole(muteRole);
        System.out.println("User " + Util.makeUserString(muteUser, message.getGuild()) + " selfmuted.");

        // Wird ausgeführt, um Nutzer wieder zu entmuten
        Runnable unmuteTask = () -> {
            mutedUsers.remove(muteUser.getStringID());
            muteUser.removeRole(muteRole);
            System.out.println("Unmuted user " + Util.makeUserString(muteUser, message.getGuild()) + ". (was selfmuted)");
        };

        final int muteDuration = Integer.parseInt(matcher.group(1));
        final String muteDurationUnitString = matcher.group(2);
        ChronoUnit muteDurationUnit = parseChronoUnit(muteDurationUnitString);

        if (mutedUsers.containsKey(muteUser.getStringID())) {
            // Überprüfen, ob angegebener Zeitpunkt nach dem bisherigen Zeitpunkt liegt
            ScheduledFuture oldFuture = mutedUsers.get(muteUser.getStringID());

            LocalDateTime oldDateTime = LocalDateTime.now().plusSeconds(oldFuture.getDelay(TimeUnit.SECONDS));
            LocalDateTime newDateTime = LocalDateTime.now().plus(muteDuration, muteDurationUnit);

            if (newDateTime.isBefore(oldDateTime)) {
                // neuer Zeitpunkt ist vor altem -> nichts tun (längerer Mute bleibt bestehen)
                Util.sendMessage(message.getChannel(), "Nutzer ist bereits für einen längeren Zeitraum gemuted!");
                return;
            }
            else {
                // neuer Zeitpunkt ist nach altem -> neu schedulen
                mutedUsers.remove(muteUser.getStringID(), oldFuture);
                oldFuture.cancel(false);
            }
        }

        ScheduledFuture newFuture = scheduler.schedule(unmuteTask, muteDuration, chronoUnitToTimeUnit(muteDurationUnit));
        mutedUsers.put(muteUser.getStringID(), newFuture);

        message.addReaction(ReactionEmoji.of("\uD83D\uDD07")); // :mute:
    }

    @EventSubscriber
    public void onUserJoin(final UserJoinEvent event) {
        final IUser user = event.getUser();
        if (mutedUsers.containsKey(user.getStringID())) {
            IRole muteRole = event.getGuild().getRoleByID(muteRoleID);
            user.addRole(muteRole);
        }
    }

    private static ChronoUnit parseChronoUnit (String chronoUnitString) {
        switch (chronoUnitString.toLowerCase()) {
            case "s": return ChronoUnit.SECONDS;
            case "m": return ChronoUnit.MINUTES;
            case "h": return ChronoUnit.HOURS;
            case "d": return ChronoUnit.DAYS;

            default: return ChronoUnit.SECONDS;
        }
    }

    private TimeUnit chronoUnitToTimeUnit (ChronoUnit chronoUnit) {
        switch (chronoUnit) {
            case SECONDS: return TimeUnit.SECONDS;
            case MINUTES: return TimeUnit.MINUTES;
            case HOURS: return TimeUnit.HOURS;
            case DAYS: return TimeUnit.DAYS;

            default: throw new UnsupportedOperationException("Unsupported ChronoUnit");
        }
    }
}
