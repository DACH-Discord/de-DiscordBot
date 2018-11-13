package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.exception.InitializationException;
import de.nikos410.discordBot.framework.PermissionLevel;
import de.nikos410.discordBot.framework.annotations.CommandModule;
import de.nikos410.discordBot.framework.annotations.CommandSubscriber;
import de.nikos410.discordBot.util.discord.DiscordIO;
import de.nikos410.discordBot.util.discord.GuildUtils;
import de.nikos410.discordBot.util.discord.UserUtils;
import de.nikos410.discordBot.util.io.IOUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserBanEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.impl.events.guild.voice.user.UserVoiceChannelJoinEvent;
import sx.blah.discord.handle.impl.events.guild.voice.user.UserVoiceChannelLeaveEvent;
import sx.blah.discord.handle.impl.events.guild.voice.user.UserVoiceChannelMoveEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.cache.LongMap;

import javax.xml.bind.ValidationException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CommandModule(moduleName = "Modzeugs", commandOnly = false)
public class ModStuff {
    private final DiscordBot bot;

    private final static Path MODSTUFF_PATH = Paths.get("data/modstuff.json");
    private final JSONObject modstuffJSON;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Map<IGuild, Map<IUser, ScheduledFuture>> userMuteFutures = new HashMap<>();
    private final Map<IGuild, Map<IChannel, Map<IUser, ScheduledFuture>>> channelMuteFutures = new HashMap<>();

    private final Map<IGuild, List<String>> voiceLog = new HashMap<>();

    private final static Logger LOG = LoggerFactory.getLogger(ModStuff.class);

    public ModStuff (final DiscordBot bot) {
        this.bot = bot;
        final IDiscordClient client = bot.client;

        final String rolesFileContent = IOUtil.readFile(MODSTUFF_PATH);
        if (rolesFileContent == null) {
            LOG.error("Could not read modstuff file.");
            throw new InitializationException("Could not read modstuff file.", ModStuff.class) ;
        }
        this.modstuffJSON = new JSONObject(rolesFileContent);
        LOG.info("Loaded modstuff file for {} guilds.", modstuffJSON.keySet().size());
    }

    @CommandSubscriber(command = "kick", help = "Kickt den angegebenen Nutzer mit der angegeben Nachricht vom Server",
            pmAllowed = false)
    public void command_Kick(final IMessage message, final String kickUserString, String customMessage) {
        if (this.bot.getUserPermissionLevel(message.getAuthor(), message.getGuild()).getLevel() >=
                PermissionLevel.MODERATOR.getLevel()) {

            final List<IUser> mentions = message.getMentions();
            if (mentions.size() <1) {
                DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: Kein Nutzer angegeben!");
                return;
            }
            else if (mentions.size() > 1) {
                DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: In der Nachricht keine Nutzer erwähnen!");
                return;
            }

            final IUser kickUser = mentions.get(0);
            if (kickUser == null) {
                DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: Nutzer nicht gefunden!");
                return;
            }

            if (customMessage.isEmpty()) {
                customMessage = "kein";
            }

            final String kickMessage = String.format("**Du wurdest gekickt!** (Du kannst dem Server jedoch erneut beitreten.) \nHinweis: _%s_",
                    customMessage);


            DiscordIO.sendMessage(kickUser.getOrCreatePMChannel(), kickMessage);
            message.getGuild().kickUser(kickUser, customMessage);

            message.addReaction(ReactionEmoji.of("\uD83D\uDEAA")); // :door:
            //Util.sendMessage(message.getChannel(), ":door:");

            // Modlog
            LOG.info("{} hat Nutzer {} vom Server gekickt. Hinweis: {}",
                    UserUtils.makeUserString(message.getAuthor(), message.getGuild()),
                    UserUtils.makeUserString(kickUser, message.getGuild()),
                    customMessage);

            final IGuild guild = message.getGuild();
            final IChannel modLogChannel = getModlogChannelForGuild(guild);

            if (modLogChannel != null) {
                final String modLogMessage = String.format("**%s** hat Nutzer **%s** im Kanal %s vom Server **gekickt**. \nHinweis: _%s _",
                        UserUtils.makeUserString(message.getAuthor(), guild),
                        UserUtils.makeUserString(kickUser, guild),
                        message.getChannel().mention(),
                        customMessage);
                DiscordIO.sendMessage(modLogChannel, modLogMessage);
            }
        }
        else {
            message.getGuild().kickUser(message.getAuthor());
            message.addReaction(ReactionEmoji.of("tja", 401835325434888192L));
        }
    }

    @CommandSubscriber(command = "ban", help = "Bannt den angegebenen Nutzer mit der angegeben Nachricht vom Server",
            pmAllowed = false)
    public void command_Ban(final IMessage message, final String banUserString, String customMessage) {
        if (this.bot.getUserPermissionLevel(message.getAuthor(), message.getGuild()).getLevel() >=
                PermissionLevel.MODERATOR.getLevel()) {

            final List<IUser> mentions = message.getMentions();
            if (mentions.size() <1) {
                DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: Kein Nutzer angegeben!");
            }
            else if (mentions.size() > 1) {
                DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: In der Nachricht keine Nutzer erwähnen!");
            }

            final IUser banUser = mentions.get(0);
            if (banUser == null) {
                DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: Nutzer nicht gefunden!");
                return;
            }

            if (customMessage.isEmpty()) {
                customMessage = "kein";
            }

            final String banMessage = String.format("**Du wurdest gebannt!** \nHinweis: _%s_", customMessage);

            DiscordIO.sendMessage(banUser.getOrCreatePMChannel(), banMessage);
            message.getGuild().banUser(banUser, customMessage, 0);

            //Util.sendMessage(message.getChannel(), ":hammer:");
            message.addReaction(ReactionEmoji.of("\uD83D\uDD28")); // :hammer:

            // Modlog
            LOG.info("{} hat Nutzer {} vom Server gebannt. Hinweis: {}",
                    UserUtils.makeUserString(message.getAuthor(), message.getGuild()),
                    UserUtils.makeUserString(banUser, message.getGuild()),
                    customMessage);

            final IGuild guild = message.getGuild();
            final IChannel modLogChannel = getModlogChannelForGuild(guild);

            if (modLogChannel != null) {
                final String modLogMessage = String.format("**%s** hat Nutzer **%s** im Kanal %s vom Server **gebannt**. \nHinweis: _%s _",
                        UserUtils.makeUserString(message.getAuthor(), guild),
                        UserUtils.makeUserString(banUser, guild),
                        message.getChannel().mention(),
                        customMessage);
                DiscordIO.sendMessage(modLogChannel, modLogMessage);
            }
        }
        else {
            message.getGuild().kickUser(message.getAuthor(), customMessage);
            message.addReaction(ReactionEmoji.of("tja", 401835325434888192L));
        }
    }

    @EventSubscriber
    public void onUserBanned (final UserBanEvent event) {
        final IGuild guild = event.getGuild();
        final IChannel modLogChannel = getModlogChannelForGuild(guild);

        if (modLogChannel != null) {
            final String modLogMessage = String.format("**%s** wurde vom Server **gebannt**.",
                    UserUtils.makeUserString(event.getUser(), event.getGuild()));
            DiscordIO.sendMessage(modLogChannel, modLogMessage);
        }
    }

    @CommandSubscriber(command = "mute", help = "Einen Nutzer für eine bestimmte Zeit muten", pmAllowed = false,
            permissionLevel = PermissionLevel.MODERATOR)
    public void command_Mute(final IMessage message, final String muteUserString, final String muteDurationInput) {
        // Nutzer der gemuted werden soll auslesen
        final List<IUser> mentions = message.getMentions();
        if (mentions.size() < 1) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: Kein Nutzer angegeben!");
            return;
        }
        else if (mentions.size() > 1) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: mehrere Nutzer erwähnt");
            return;
        }

        final IUser muteUser = mentions.get(0);
        if (muteUser == null) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: Nutzer nicht gefunden!");
            return;
        }

        // Mute Dauer auslesen
        final Pattern pattern = Pattern.compile("(\\d+)\\s*([smhd])\\s*(.*)");
        final Matcher matcher = pattern.matcher(muteDurationInput);

        if (!matcher.matches()) {
            DiscordIO.sendMessage(message.getChannel(), "Ungültige Eingabe! Mögliche Zeitformate sind s, m, h und d.");
            return;
        }

        final int muteDuration = Integer.parseInt(matcher.group(1));
        final String muteDurationUnitString = matcher.group(2);
        ChronoUnit muteDurationUnit = parseChronoUnit(muteDurationUnitString);

        final IGuild guild = message.getGuild();

        // Nutzer muten und unmuten schedulen
        // Es wird nur ein String returned wenn der mute nicht erfolgt ist
        final String output = muteUserForGuild(muteUser, guild, muteDuration, muteDurationUnit);
        if (output.isEmpty()) {
            message.addReaction(ReactionEmoji.of("\uD83D\uDD07")); // :mute:
        }
        else {
            DiscordIO.sendMessage(message.getChannel(), output);
            return;
        }

        // Hinweis auslesen
        String customMessage = matcher.group(3);

        if (customMessage.isEmpty()) {
            customMessage = "kein";
        }

        final String muteMessage = String.format("**Du wurdest für %s %s gemuted!** \nHinweis: _%s_",
                muteDuration, muteDurationUnitString, customMessage);

        // Einen Bot nicht benachrichtigen
        if (!muteUser.isBot()) {
            DiscordIO.sendMessage(muteUser.getOrCreatePMChannel(), muteMessage);
        }

        // Modlog
        LOG.info("Nutzer {} wurde für {} gemuted.", UserUtils.makeUserString(muteUser, message.getGuild()), muteDurationInput);

        final IChannel modLogChannel = getModlogChannelForGuild(guild);

        if (modLogChannel != null) {
            final String modLogMessage = String.format("**%s** hat Nutzer **%s** im Kanal %s für %s %s **gemuted**. \nHinweis: _%s _",
                    UserUtils.makeUserString(message.getAuthor(), message.getGuild()),
                    UserUtils.makeUserString(muteUser, message.getGuild()), message.getChannel().mention(),
                    muteDuration, muteDurationUnitString, customMessage);
            DiscordIO.sendMessage(modLogChannel, modLogMessage);
        }

        saveMutedUsers();
    }

    @CommandSubscriber(command = "selfmute", help = "Schalte dich selber für die angegebene Zeit stumm", pmAllowed = false)
    public void command_Selfmute(final IMessage message, final String muteDurationInput) {
        // Autor der Nachricht soll gemuted werden
        final IUser muteUser = message.getAuthor();

        // Mute Dauer auslesen
        final Pattern pattern = Pattern.compile("(\\d+)\\s*([smhd])\\s*(.*)");
        final Matcher matcher = pattern.matcher(muteDurationInput);

        if (!matcher.matches()) {
            DiscordIO.sendMessage(message.getChannel(), "Ungültige Eingabe! Mögliche Zeitformate sind s, m, h und d.");
            return;
        }

        final int muteDuration = Integer.parseInt(matcher.group(1));
        final String muteDurationUnitString = matcher.group(2);
        ChronoUnit muteDurationUnit = parseChronoUnit(muteDurationUnitString);

        // Nutzer können sich für maximal einen Tag selber muten
        final LocalDateTime muteEnd = LocalDateTime.now().plus(muteDuration, muteDurationUnit);
        if (muteEnd.isAfter(LocalDateTime.now().plusDays(1))) {
            // Länger als 1 Tag
            DiscordIO.sendMessage(message.getChannel(), "Du kannst dich für maximal einen Tag muten!");
            return;
        }

        final IGuild guild = message.getGuild();

        // Nutzer muten und unmuten schedulen
        // Es wird nur ein String returned wenn der mute nicht erfolgt ist
        final String output = muteUserForGuild(muteUser, guild, muteDuration, muteDurationUnit);
        if (output.isEmpty()) {
            message.addReaction(ReactionEmoji.of("\uD83D\uDD07")); // :mute:
        }
        else {
            DiscordIO.sendMessage(message.getChannel(), output);
        }

        saveMutedUsers();
    }

    @CommandSubscriber(command = "unmute", help = "Nutzer entmuten", pmAllowed = false,
            permissionLevel = PermissionLevel.MODERATOR)
    public void command_Unmute(final IMessage message) {
        final List<IUser> mentions = message.getMentions();
        if (mentions.size() <1) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: Kein Nutzer angegeben!");
            return;
        }
        else if (mentions.size() > 1) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: mehrere Nutzer erwähnt");
            return;
        }

        final IUser muteUser = mentions.get(0);
        final IRole muteRole = getMuteRoleForGuild(message.getGuild());

        if (muteRole == null) {
            DiscordIO.sendMessage(message.getChannel(), "Keine gültige Mute Rolle konfiguriert!");
            return;
        }

        muteUser.removeRole(muteRole);

        if (userMuteFutures.containsKey(message.getGuild()) &&
                userMuteFutures.get(message.getGuild()).containsKey(muteUser)) {
            ScheduledFuture future = userMuteFutures.get(message.getGuild()).remove(muteUser);
            future.cancel(false);
        }

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
    }

    private String muteUserForGuild(final IUser user, final IGuild guild, final int muteDuration, final ChronoUnit muteDurationUnit) {
        // Mute Rolle für diesen Server auslesen
        final IRole muteRole = getMuteRoleForGuild(guild);
        if (muteRole == null) {
            return "Keine gültige Mute Rolle konfiguriert!";
        }

        // Prüfen ob Nutzer bereits gemuted ist
        if (isUserMutedForGuild(user, guild)) {
            // Nutzer ist bereits gemuted
            // Überprüfen, ob angegebener Zeitpunkt nach dem bisherigen Zeitpunkt liegt
            final ScheduledFuture oldFuture = userMuteFutures.get(guild).get(user);

            // Unmute Zeitpunkte in LocalDateTime
            final LocalDateTime oldDateTime = LocalDateTime.now().plusSeconds(oldFuture.getDelay(TimeUnit.SECONDS));
            final LocalDateTime newDateTime = LocalDateTime.now().plus(muteDuration, muteDurationUnit);

            // Prüfen welcher Unmute Zeitpunkt zuerst eintritt
            if (newDateTime.isBefore(oldDateTime)) {
                // neuer Zeitpunkt ist vor altem -> nichts tun (längerer Mute bleibt bestehen)
                return "Nutzer ist bereits für einen längeren Zeitraum gemuted!";
            }
            else {
                // neuer Zeitpunkt ist nach altem -> neu schedulen
                userMuteFutures.get(guild).remove(user, oldFuture);
                oldFuture.cancel(false);
            }
        }
        else {
            // Nutzer ist noch nicht gemuted
            user.addRole(muteRole);
            LOG.info("Muted user {}.", UserUtils.makeUserString(user, guild));
        }

        // Wird ausgeführt, um Nutzer wieder zu entmuten
        final Runnable unmuteTask = () -> {
            // Only remove the mute role if th user is still a member of the guild
            if (guild.getUsers().contains(user)) {
                user.removeRole(muteRole);
            }
            userMuteFutures.get(guild).remove(user);

            LOG.info("Nutzer {} wurde entmuted.", UserUtils.makeUserString(user, guild));
            saveMutedUsers();
        };

        // Unmute schedulen
        final ScheduledFuture newFuture = scheduler.schedule(unmuteTask, muteDuration, chronoUnitToTimeUnit(muteDurationUnit));

        if (userMuteFutures.containsKey(guild)) {
            userMuteFutures.get(guild).put(user, newFuture);
        }
        else {
            final Map<IUser, ScheduledFuture> guildMap = new HashMap<>();
            guildMap.put(user, newFuture);
            userMuteFutures.put(guild, guildMap);
        }

        return "";
    }

    private boolean isUserMutedForGuild (final IUser user, final IGuild guild) {
        return (userMuteFutures.containsKey(guild) && userMuteFutures.get(guild).containsKey(user));
    }

    @CommandSubscriber(command = "channelMute", help = "Nutzer in einem Channel für eine bestimmte Zeit stummschalten", pmAllowed = false, permissionLevel = PermissionLevel.MODERATOR)
    public void command_channelMute(final IMessage message, final String user, final String channelOrMuteDurationInput, final String muteDurationInput) {
        // Nutzer auslesen
        final List<IUser> userMentions = message.getMentions();
        if (userMentions.size() < 1) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: Kein Nutzer angegeben!");
            return;
        }
        else if (userMentions.size() > 1) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: mehrere Nutzer erwähnt");
            return;
        }

        final IUser muteUser = userMentions.get(0);
        if (muteUser == null) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: Nutzer nicht gefunden!");
            return;
        }

        // Kanal auslesen
        final List<IChannel> channelMentions = message.getChannelMentions();
        final IChannel muteChannel;

        if (channelMentions.size() > 1) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: mehrere Kanäle erwähnt");
            return;
        }
        else if (channelMentions.size() < 1) {
            // Kein Kanal erwähnt -> Kanal der Nachricht verwenden
            muteChannel = message.getChannel();
        }
        else {
            // Kanal erwähnt -> diesen verwenden
            muteChannel = channelMentions.get(0);
        }

        // Mute Dauer auslesen
        final Pattern pattern = Pattern.compile("(\\d+)\\s*([smhd])\\s*(.*)");

        final Matcher matcher;
        if (channelMentions.size() < 1) {
            matcher = pattern.matcher(channelOrMuteDurationInput);
        }
        else {
            matcher = pattern.matcher(muteDurationInput);
        }

        if (!matcher.matches()) {
            DiscordIO.sendMessage(message.getChannel(), "Ungültige Eingabe! Mögliche Zeitformate sind s, m, h und d.");
            return;
        }

        final int muteDuration = Integer.parseInt(matcher.group(1));
        final String muteDurationUnitString = matcher.group(2);
        ChronoUnit muteDurationUnit = parseChronoUnit(muteDurationUnitString);

        // Nutzer muten und unmuten schedulen
        // Es wird nur ein String returned wenn der mute nicht erfolgt ist
        final String output = muteUserForChannel(muteUser, muteChannel, muteDuration, muteDurationUnit);
        if (output.isEmpty()) {
            message.addReaction(ReactionEmoji.of("\uD83D\uDD07")); // :mute:
        }
        else {
            DiscordIO.sendMessage(message.getChannel(), output);
            return;
        }

        // Hinweis auslesen
        String customMessage = matcher.group(3);

        if (customMessage.isEmpty()) {
            customMessage = "kein";
        }

        final IGuild guild = message.getGuild();

        final String muteMessage = String.format("**Du wurdest für %s %s für den Kanal %s auf dem Server %s gemuted!** \nHinweis: _%s_",
                muteDuration, muteDurationUnitString, muteChannel.getName(), guild.getName(), customMessage);

        // Einen Bot nicht benachrichtigen
        if (!muteUser.isBot()) {
            DiscordIO.sendMessage(muteUser.getOrCreatePMChannel(), muteMessage);
        }

        // Modlog
        LOG.info("Nutzer {} wurde für {} {} für den Kanal {} auf dem Server {} gemuted. \nHinweis: {}",
                UserUtils.makeUserString(muteUser, guild), muteDuration,
                muteDurationUnitString,
                muteChannel.getName(),
                guild.getName(),
                customMessage);

        final IChannel modLogChannel = getModlogChannelForGuild(guild);

        if (modLogChannel != null) {
            final String modLogMessage = String.format("**%s** hat Nutzer **%s** im Kanal %s für %s %s für den Kanal %s **gemuted**. \nHinweis: _%s _",
                    UserUtils.makeUserString(message.getAuthor(), message.getGuild()),
                    UserUtils.makeUserString(muteUser, message.getGuild()), message.getChannel().mention(),
                    muteDuration, muteDurationUnitString, muteChannel.mention(), customMessage);
            DiscordIO.sendMessage(modLogChannel, modLogMessage);
        }
    }

    private String muteUserForChannel (final IUser user, final IChannel channel, final int muteDuration, final ChronoUnit muteDurationUnit) {
        final LongMap<PermissionOverride> oldOverrides = channel.getUserOverrides();

        // Prüfen ob bereits Overrides für den Nutzer existieren
        if (oldOverrides.containsKey(user.getLongID())) {
            // Bisherige Overrides für den Nutzer auslesen
            final PermissionOverride oldUserOverrides = oldOverrides.get(user.getLongID());

            // Bisherige Overrides für den Nutzer kopieren
            final EnumSet<Permissions> userAllow = oldUserOverrides.allow().clone();
            final EnumSet<Permissions> userDeny = oldUserOverrides.deny().clone();

            // Rechte zum Senden entfernen
            userAllow.remove(Permissions.SEND_MESSAGES);
            userDeny.add(Permissions.SEND_MESSAGES);

            // Rechte aktualisieren
            channel.overrideUserPermissions(user, userAllow, userDeny);
        }
        else {
            // Rechte zum Senden entfernen
            channel.overrideUserPermissions(user, EnumSet.noneOf(Permissions.class), EnumSet.of(Permissions.SEND_MESSAGES));
        }

        final IGuild guild = channel.getGuild();

        // Prüfen ob Nutzer bereits gemuted ist
        if (isUserMutedForChannel(user, channel)) {
            // Nutzer ist bereits gemuted
            // Überprüfen, ob angegebener Zeitpunkt nach dem bisherigen Zeitpunkt liegt
            final ScheduledFuture oldFuture = channelMuteFutures.get(guild).get(channel).get(user);

            // Unmute Zeitpunkte in LocalDateTime
            final LocalDateTime oldDateTime = LocalDateTime.now().plusSeconds(oldFuture.getDelay(TimeUnit.SECONDS));
            final LocalDateTime newDateTime = LocalDateTime.now().plus(muteDuration, muteDurationUnit);

            // Prüfen welcher Unmute Zeitpunkt zuerst eintritt
            if (newDateTime.isBefore(oldDateTime)) {
                // neuer Zeitpunkt ist vor altem -> nichts tun (längerer Mute bleibt bestehen)
                return "Nutzer ist bereits für einen längeren Zeitraum gemuted!";
            }
            else {
                // neuer Zeitpunkt ist nach altem -> neu schedulen
                channelMuteFutures.get(guild).get(channel).remove(user, oldFuture);
                oldFuture.cancel(false);
            }
        }
        else {
            // Nutzer ist noch nicht gemuted
            LOG.info("Muted user {}.", UserUtils.makeUserString(user, guild));
        }

        // Wird ausgeführt, um Nutzer wieder zu entmuten
        final Runnable unmuteTask = () -> {
            final LongMap<PermissionOverride> currentOverrides = channel.getUserOverrides();

            if (currentOverrides.containsKey(user.getLongID())) {
                // Aktuelle Permissions für den Nutzer auslesen
                final PermissionOverride currentUserOverride = currentOverrides.get(user.getLongID());

                final EnumSet<Permissions> currentUserAllowed = currentUserOverride.allow();
                final EnumSet<Permissions> currentUserDenied = currentUserOverride.deny();

                if (oldOverrides.containsKey(user.getLongID())) {
                    final PermissionOverride oldUserOverride = oldOverrides.get(user.getLongID());

                    // alte Berechtigungen
                    final EnumSet<Permissions> oldUserAllowed = oldUserOverride.allow();
                    final EnumSet<Permissions> oldUserDenied = oldUserOverride.deny();

                    // Die SEND_MESSAGES Permission auf den Zustand vor de Mute setzen
                    currentUserAllowed.remove(Permissions.SEND_MESSAGES);
                    currentUserDenied.remove(Permissions.SEND_MESSAGES);

                    if (oldUserAllowed.contains(Permissions.SEND_MESSAGES)) {
                        currentUserAllowed.add(Permissions.SEND_MESSAGES);
                    }

                    if (oldUserDenied.contains(Permissions.SEND_MESSAGES)) {
                        currentUserDenied.add(Permissions.SEND_MESSAGES);
                    }
                }
                else {
                    // Keine alten Overrides für den Nutzer bekannt
                    currentUserAllowed.remove(Permissions.SEND_MESSAGES);
                    currentUserDenied.remove(Permissions.SEND_MESSAGES);
                }

                // Wenn Override leer ist entfernen
                if (currentUserAllowed.size() == 0 && currentUserDenied.size() == 0) {
                    channel.removePermissionsOverride(user);
                }
                else {
                    channel.overrideUserPermissions(user, currentUserAllowed, currentUserDenied);
                }
            }
            else {
                // Override existiert nicht mehr, wurde vmtl. von Hand entfernt
                LOG.info("Can't unmute user {} for channel {}. Override does not exist.",
                        UserUtils.makeUserString(user, guild), channel.getName());
            }

            channelMuteFutures.get(guild).get(channel).remove(user);

            LOG.info("Nutzer {} wurde entmuted.", UserUtils.makeUserString(user, guild));

            saveMutedUsers();
        };

        // Unmute schedulen
        final ScheduledFuture newFuture = scheduler.schedule(unmuteTask, muteDuration, chronoUnitToTimeUnit(muteDurationUnit));

        final Map<IChannel, Map<IUser, ScheduledFuture>> guildMap;
        if (channelMuteFutures.containsKey(guild)) {
            guildMap = channelMuteFutures.get(guild);
        }
        else {
            guildMap = new HashMap<>();
            channelMuteFutures.put(guild, guildMap);
        }

        final Map<IUser, ScheduledFuture> channelMap;
        if (guildMap.containsKey(channel)) {
            channelMap = guildMap.get(channel);
        }
        else {
            channelMap = new HashMap<>();
            guildMap.put(channel, channelMap);
        }

        channelMap.put(user, newFuture);

        return "";
    }



    private boolean isUserMutedForChannel (final IUser user, final IChannel channel) {
        final IGuild guild = channel.getGuild();
        if (channelMuteFutures.containsKey(guild)) {
            final Map<IChannel, Map<IUser, ScheduledFuture>> guildMap = channelMuteFutures.get(guild);

            if (guildMap.containsKey(channel)) {
                final Map<IUser, ScheduledFuture> channelMap = guildMap.get(channel);

                return channelMap.containsKey(user);
            }
        }

        return false;
    }

    @CommandSubscriber(command = "voicelog", help = "Die letzten 20 Aktivitäten in Sprachkanälen auflisten",
    pmAllowed = false, permissionLevel = PermissionLevel.MODERATOR, ignoreParameterCount = true)
    public void command_voicelog(final IMessage message, final String listCountArg) {
        final int listCount;

        if (listCountArg == null) {
            listCount = 20;
        }
        else {
            try {
                listCount = Integer.parseInt(listCountArg);
            }
            catch (NumberFormatException e) {
                DiscordIO.sendMessage(message.getChannel(), ":x: Die angegebene Anzahl ist keine gültige Zahl!");
                return;
            }
        }

        final List<String> voiceLog = getVoiceLogForGuild(message.getGuild());

        final StringBuilder stringBuilder = new StringBuilder();
        boolean entriesSkipped = false;

        for (int i = voiceLog.size()-1; i > (voiceLog.size() - listCount - 1) && i >= 0; i--) {
            final String lineToAdd = voiceLog.get(i);
            if (stringBuilder.length() + lineToAdd.length() <= 1024) {
                stringBuilder.append(voiceLog.get(i));
                stringBuilder.append('\n');
            }
            else {
                entriesSkipped = true;
            }
        }

        final EmbedBuilder responseBuilder = new EmbedBuilder();
        final String content = stringBuilder.length() > 0 ? stringBuilder.toString() : "_keine_";
        responseBuilder.appendField(String.format("Die letzten %s Voice-Interaktionen (von neu nach alt)", listCount), content, false);
        if (entriesSkipped) {
            responseBuilder.withFooterText("Einer oder mehrere Einträge wurden ignoriert, weil die maximale Textlänge erreicht wurde.");
        }

        DiscordIO.sendEmbed(message.getChannel(), responseBuilder.build());
    }

    @EventSubscriber
    public void onUserMove (final UserVoiceChannelMoveEvent event) {
        LOG.debug("Logged voice move event.");
        final IUser user = event.getUser();
        final IVoiceChannel newChannel = event.getNewChannel();
        final IGuild guild = event.getGuild();
        final String eventString = String.format("**%s** :arrow_forward: ` %s`", UserUtils.makeUserString(user, guild), newChannel.getName());
        getVoiceLogForGuild(guild).add(eventString);
    }

    @EventSubscriber
    public void onUserConnect(final UserVoiceChannelJoinEvent event) {
        LOG.debug("Logged voice connect event.");
        final IUser user = event.getUser();
        final IVoiceChannel channel = event.getVoiceChannel();
        final IGuild guild = event.getGuild();
        final String eventString = String.format("**%s** :arrow_forward: `%s`", UserUtils.makeUserString(user, guild), channel.getName());
        getVoiceLogForGuild(guild).add(eventString);
    }

    @EventSubscriber
    public void onUserDisconnect(final UserVoiceChannelLeaveEvent event) {
        LOG.debug("Logged voice disconnect event.");
        final IUser user = event.getUser();
        final IVoiceChannel channel = event.getVoiceChannel();
        final IGuild guild = event.getGuild();
        final String eventString = String.format("**%s** :small_red_triangle_down: `%s`", UserUtils.makeUserString(user, guild), channel.getName());
        getVoiceLogForGuild(guild).add(eventString);
    }

    @CommandSubscriber(command = "setModlogChannel", help = "Kanal in dem die Modlog Nachrichten gesendet werden einstellen",
            pmAllowed = false, passContext = false, permissionLevel = PermissionLevel.ADMIN)
    public void command_setModlogChannel(final IMessage message, final String channel) {
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
            DiscordIO.sendMessage(message.getChannel(), "Kein gültiger Kanal angegeben!");
            return;
        }

        final IGuild guild = message.getGuild();
        final JSONObject guildJSON;
        if (modstuffJSON.has(guild.getStringID())) {
            guildJSON = modstuffJSON.getJSONObject(guild.getStringID());
        }
        else {
            guildJSON = new JSONObject();
            modstuffJSON.put(guild.getStringID(), guildJSON);
        }

        guildJSON.put("modlogChannel", modlogChannel.getLongID());
        saveJSON();

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
    }

    @CommandSubscriber(command = "setMuteRole", help = "Mute Rolle einstellen einstellen",
            pmAllowed = false, passContext = false, permissionLevel = PermissionLevel.ADMIN)
    public void command_setMuteRole(final IMessage message, final String role) {
        final IRole muteRole;
        final List<IRole> roleMentions = message.getRoleMentions();

        if (GuildUtils.roleExists(message.getGuild(), role)) {
            // Rollen ID wurde als Parameter angegeben
            muteRole = message.getGuild().getRoleByID(Long.parseLong(role));
        }
        else if (roleMentions.size() == 1) {
            // eine Rolle wurde erwähnt
            muteRole = roleMentions.get(0);
        }
        else {
            // Keine Rolle angegeben
            DiscordIO.sendMessage(message.getChannel(), "Keine gültige Rolle angegeben!");
            return;
        }

        final IGuild guild = message.getGuild();
        final JSONObject guildJSON;
        if (modstuffJSON.has(guild.getStringID())) {
            guildJSON = modstuffJSON.getJSONObject(guild.getStringID());
        }
        else {
            guildJSON = new JSONObject();
            modstuffJSON.put(guild.getStringID(), guildJSON);
        }

        guildJSON.put("muteRole", muteRole.getLongID());
        saveJSON();

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
    }

    @EventSubscriber
    public void onUserJoin(final UserJoinEvent event) {
        final IUser user = event.getUser();
        if (userMuteFutures.containsKey(event.getGuild()) && userMuteFutures.get(event.getGuild()).containsKey(user)) {
            final IRole muteRole = getMuteRoleForGuild(event.getGuild());
            if (muteRole == null) {
                return;
            }

            user.addRole(muteRole);
        }
    }

    @EventSubscriber
    public void onStartup(final ReadyEvent event) {
        LOG.info("Restoring muted users.");

        for (final String guildStringID : modstuffJSON.keySet()) {
            LOG.debug("Processing JSON for guild with ID '{}'.", guildStringID);

            final long guildLongID = Long.parseLong(guildStringID);
            final IGuild guild = event.getClient().getGuildByID(guildLongID);
            LOG.debug("Found guild '{}'.", guild.getName());

            restoreGuildUserMutes(guild);
        }

        LOG.info("Restored all mutes.");
    }

    private void restoreGuildUserMutes(final IGuild guild) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        final JSONArray guildUserMutes = getUserMutesJSONForGuild(guild);
        LOG.debug("Found {} mutes for guild.", guildUserMutes.length());

        for (int i = 0; i < guildUserMutes.length(); i++) {
            final JSONObject currentUserMute = guildUserMutes.getJSONObject(i);
            if (currentUserMute.has("user") && currentUserMute.has("mutedUntil")) {
                final long userLongID = currentUserMute.getLong("user");
                final IUser user = guild.getUserByID(userLongID);
                final String unmuteTimestampString = currentUserMute.getString("mutedUntil");
                final LocalDateTime unmuteTimestamp = LocalDateTime.parse(unmuteTimestampString, formatter);

                if (LocalDateTime.now().isBefore(unmuteTimestamp)) {
                    final int delaySeconds = (int)LocalDateTime.now().until(unmuteTimestamp, ChronoUnit.SECONDS);
                    muteUserForGuild(user, guild, delaySeconds, ChronoUnit.SECONDS);
                    LOG.info("Restored mute for user '{}' (ID: {}) for guild '{}' (ID: {}). Muted until {}",
                            UserUtils.makeUserString(user, guild), user.getStringID(),
                            guild.getName(), guild.getStringID(),
                            unmuteTimestampString);
                }
            }
            else {
                LOG.warn(String.format("userMute at index %s doesn't contain necessary keys! Skipping.", i));
            }
        }
    }

    private IRole getMuteRoleForGuild(final IGuild guild) {
        final JSONObject guildJSON = getJSONForGuild(guild);
        if (guildJSON.has("muteRole")) {
            final long muteRoleID = guildJSON.getLong("muteRole");
            final IRole muteRole = guild.getRoleByID(muteRoleID);
            if (muteRole != null) {
                return muteRole;
            }
            else {
                LOG.warn(String.format("Auf dem Server %s (ID: %s) wurde keine Rolle mit der ID %s gefunden!", guild.getName(), guild.getStringID(), muteRoleID));
                return null;
            }
        }
        else {
            LOG.warn(String.format("Keine Mute Rolle für Server %s (ID: %s) angegeben.",
                    guild.getName(), guild.getStringID()));
            return null;
        }
    }

    private IChannel getModlogChannelForGuild(final IGuild guild) {
        final JSONObject guildJSON = getJSONForGuild(guild);
        if (guildJSON.has("modlogChannel")) {
            final long modlogChannelID = guildJSON.getLong("modlogChannel");
            final IChannel modlogChannel = guild.getChannelByID(modlogChannelID);
            if (modlogChannel != null) {
                return modlogChannel;
            }
            else {
                LOG.warn(String.format("Auf dem Server %s (ID: %s) wurde kein Channel mit der ID %s gefunden!",
                        guild.getName(), guild.getStringID(), modlogChannelID));
                return null;
            }
        }
        else {
            LOG.warn(String.format("Kein Modlog Channel für Server %s (ID: %s) angegeben.",
                    guild.getName(), guild.getStringID()));
            return null;
        }
    }

    private List<String> getVoiceLogForGuild(final IGuild guild) {
        if (voiceLog.containsKey(guild)) {
            final List<String> log = voiceLog.get(guild);
            final int maxSize = 100;
            if (log.size() > maxSize) {
                final List<String> newLog = log.subList(log.size() - maxSize, log.size());
                voiceLog.put(guild, newLog);
                return newLog;
            }
            else {
                return log;
            }
        }
        else {
            final List<String> guildVoiceLog = new ArrayList<>();
            voiceLog.put(guild, guildVoiceLog);
            return guildVoiceLog;
        }
    }

    private JSONArray getUserMutesJSONForGuild(final IGuild guild) {
        final JSONObject guildJSON = getJSONForGuild(guild);
        if (guildJSON.has("userMutes")) {
            return guildJSON.getJSONArray("userMutes");
        }
        else {
            final JSONArray jsonArray = new JSONArray();
            guildJSON.put("userMutes", jsonArray);
            return jsonArray;
        }
    }

    private JSONArray getChannelMutesJSONForGuild(final IGuild guild) {
        final JSONObject guildJSON = getJSONForGuild(guild);
        if (guildJSON.has("channelMutes")) {
            return guildJSON.getJSONArray("userMutes");
        }
        else {
            final JSONArray jsonArray = new JSONArray();
            guildJSON.put("channelMutes", jsonArray);
            return jsonArray;
        }
    }

    private JSONObject getJSONForGuild(final IGuild guild) {
        return getJSONForGuild(guild, true);
    }

    private JSONObject getJSONForGuild(final IGuild guild, final boolean createIfNull) {
        if (modstuffJSON.has(guild.getStringID())) {
            // JSON for guild exists
            return modstuffJSON.getJSONObject(guild.getStringID());

        }
        else {
            // JSON for guild doesn't exist
            if (createIfNull) {
                final JSONObject guildJSON = new JSONObject();
                modstuffJSON.put(guild.getStringID(), guildJSON);
                return guildJSON;
            }
            else {
                LOG.warn(String.format("No JSON Entry found for guild '%s' (ID: %s)",
                        guild.getName(), guild.getStringID()));
                return null;
            }
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

    private void saveMutedUsers() {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (Entry<IGuild, Map<IUser, ScheduledFuture>> guildEntry : userMuteFutures.entrySet()) {
            final JSONArray guildUserMutesJSON = getUserMutesJSONForGuild(guildEntry.getKey());
            // Clear Array
            for (int i = 0; i < guildUserMutesJSON.length(); i++) {
                guildUserMutesJSON.remove(i);
            }
            final Map<IUser, ScheduledFuture> guildUserMutesMap = guildEntry.getValue();

            for (Entry<IUser, ScheduledFuture> userEntry : guildUserMutesMap.entrySet()) {
                final JSONObject entryObject = new JSONObject();
                entryObject.put("user", userEntry.getKey().getLongID());

                final ScheduledFuture unmutefuture = userEntry.getValue();
                final long delay = unmutefuture.getDelay(TimeUnit.SECONDS);
                final LocalDateTime unmuteTimestamp = LocalDateTime.now().plusSeconds(delay);
                entryObject.put("mutedUntil", unmuteTimestamp.format(formatter));
                guildUserMutesJSON.put(entryObject);
            }
        }

        saveJSON();
    }

    private void saveJSON() {
        LOG.debug("Saving modstuff file.");

        final String jsonOutput = this.modstuffJSON.toString(4);
        IOUtil.writeToFile(MODSTUFF_PATH, jsonOutput);
    }
}
