package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.exception.InitializationException;
import de.nikos410.discordBot.framework.PermissionLevel;
import de.nikos410.discordBot.framework.annotations.CommandModule;
import de.nikos410.discordBot.framework.annotations.CommandSubscriber;
import de.nikos410.discordBot.util.CommandUtils;
import de.nikos410.discordBot.util.discord.ChannelUtils;
import de.nikos410.discordBot.util.discord.DiscordIO;
import de.nikos410.discordBot.util.discord.GuildUtils;
import de.nikos410.discordBot.util.discord.UserUtils;
import de.nikos410.discordBot.util.io.IOUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        final String rolesFileContent = IOUtil.readFile(MODSTUFF_PATH);
        if (rolesFileContent == null) {
            LOG.error("Could not read modstuff file.");
            throw new InitializationException("Could not read modstuff file.", ModStuff.class) ;
        }
        this.modstuffJSON = new JSONObject(rolesFileContent);
        LOG.info("Loaded modstuff file for {} guilds.", modstuffJSON.keySet().size());
    }

    @CommandSubscriber(command = "kick", help = "Kickt den angegebenen Nutzer mit der angegeben Nachricht vom Server",
            pmAllowed = false, ignoreParameterCount = true)
    public void command_Kick(final IMessage message, final String userString, String customMessage) {
        // Only Moderators and upwards are allowed to use the command.
        // If a user is not a moderator they will be kicked instead.
        if (this.bot.getUserPermissionLevel(message.getAuthor(), message.getGuild()).getLevel() >=
                PermissionLevel.MODERATOR.getLevel()) {

            // Find the user to kick
            final IUser kickUser = UserUtils.getUserFromMessage(message, userString);
            if (kickUser == null) {
                DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: Kein gültiger Nutzer angegeben!");
                return;
            }

            // Set a default message if no message was specified.
            if (customMessage == null || customMessage.isEmpty()) {
                customMessage = "kein";
            }

            final List<String> kickMessage = Arrays.asList("**Du wurdest gekickt!** (Du kannst dem Server jedoch erneut beitreten.)",
                    String.format("Hinweis: _%s_", customMessage));


            DiscordIO.sendMessage(kickUser.getOrCreatePMChannel(), kickMessage);
            message.getGuild().kickUser(kickUser, customMessage);

            message.addReaction(ReactionEmoji.of("\uD83D\uDEAA")); // :door:

            // Modlog
            LOG.info("{} kicked user {}. Message: {}",
                    UserUtils.makeUserString(message.getAuthor(), message.getGuild()),
                    UserUtils.makeUserString(kickUser, message.getGuild()),
                    customMessage);

            final IGuild guild = message.getGuild();
            final IChannel modLogChannel = getModlogChannelForGuild(guild);

            if (modLogChannel != null) {
                final String modLogMessage = String.format("**%s** hat Nutzer **%s** im Kanal %s vom Server **gekickt**.\nHinweis: _%s _",
                        UserUtils.makeUserString(message.getAuthor(), guild),
                        UserUtils.makeUserString(kickUser, guild),
                        message.getChannel().mention(),
                        customMessage);
                DiscordIO.sendMessage(modLogChannel, modLogMessage);
            }
        }
        else {
            message.getGuild().kickUser(message.getAuthor());
            DiscordIO.sendMessage(message.getChannel(), "¯\\_(ツ)_/¯");
        }
    }

    @CommandSubscriber(command = "ban", help = "Bannt den angegebenen Nutzer mit der angegeben Nachricht vom Server",
            pmAllowed = false, ignoreParameterCount = true)
    public void command_Ban(final IMessage message, final String userString, String customMessage) {
        // Only Moderators and upwards are allowed to use the command.
        // If a user is not a moderator they will be kicked instead.
        if (this.bot.getUserPermissionLevel(message.getAuthor(), message.getGuild()).getLevel() >=
                PermissionLevel.MODERATOR.getLevel()) {

            // Find the user to ban
            final IUser banUser = UserUtils.getUserFromMessage(message, userString);
            if (banUser == null) {
                DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: Kein gültiger Nutzer angegeben!");
                return;
            }

            // Set a default message if no message was specified.
            if (customMessage == null || customMessage.isEmpty()) {
                customMessage = "kein";
            }

            final List<String> banMessage = Arrays.asList("**Du wurdest gebannt!**",
                    String.format("Hinweis: _%s_", customMessage));

            DiscordIO.sendMessage(banUser.getOrCreatePMChannel(), banMessage);
            message.getGuild().banUser(banUser, customMessage, 0);

            message.addReaction(ReactionEmoji.of("\uD83D\uDD28")); // :hammer:

            // Modlog
            LOG.info("{} banned user {}. Message: {}",
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
            DiscordIO.sendMessage(message.getChannel(), "¯\\_(ツ)_/¯");
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
            permissionLevel = PermissionLevel.MODERATOR, ignoreParameterCount = true)
    public void command_Mute(final IMessage message, final String userString, final String muteDurationInput) {
        // Find the user to mute
        final IUser muteUser = UserUtils.getUserFromMessage(message, userString);
        if (muteUser == null) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: Nutzer nicht gefunden!");
            return;
        }

        // Check if mute duration was specified
        if (muteDurationInput == null) {
            DiscordIO.sendMessage(message.getChannel(), "Fehler! Es muss eine Mute-Dauer angegeben werden.");
            return;
        }

        // Parse mute duration
        final CommandUtils.DurationParameters durationParameters = CommandUtils.parseDurationParameters(muteDurationInput);
        if (durationParameters == null) {
            DiscordIO.sendMessage(message.getChannel(),
                    "Ungültige Dauer angegeben. Mögliche Einheiten sind: s, m, h, d");
            return;
        }

        final int muteDuration = durationParameters.getDuration();
        final ChronoUnit muteDurationUnit = durationParameters.getDurationUnit();
        String customMessage = durationParameters.getMessage();
        if (customMessage == null || customMessage.isEmpty()) {
            customMessage = "kein";
        }

        final IGuild guild = message.getGuild();

        // Mute the user and schedule unmute
        muteUserForGuild(muteUser, guild, muteDuration, muteDurationUnit, message.getChannel());
        message.addReaction(ReactionEmoji.of("\uD83D\uDD07")); // :mute:

        // Notify the user about the mute
        final List<String> muteMessage = Arrays.asList(String.format("**Du wurdest für %s %s gemuted!",
                    muteDuration,
                    muteDurationUnit.name()),
                String.format("Hinweis: _%s_", customMessage));

        // Do not send a message to a bot user
        if (!muteUser.isBot()) {
            DiscordIO.sendMessage(muteUser.getOrCreatePMChannel(), muteMessage);
        }

        // Modlog
        LOG.info("User {} was muted for {} {}.",
                UserUtils.makeUserString(muteUser, message.getGuild()),
                muteDuration,
                muteDurationUnit.name());

        final IChannel modLogChannel = getModlogChannelForGuild(guild);

        if (modLogChannel != null) {
            final String modLogMessage = String.format("**%s** hat Nutzer **%s** im Kanal %s für %s %s **gemuted**. \nHinweis: _%s _",
                    UserUtils.makeUserString(message.getAuthor(), message.getGuild()),
                    UserUtils.makeUserString(muteUser, message.getGuild()), message.getChannel().mention(),
                    muteDuration, muteDurationUnit.name(), customMessage);
            DiscordIO.sendMessage(modLogChannel, modLogMessage);
        }

        saveMutedUsers();
    }

    @CommandSubscriber(command = "selfmute", help = "Schalte dich selber für die angegebene Zeit stumm",
            pmAllowed = false, ignoreParameterCount = true)
    public void command_Selfmute(final IMessage message, final String muteDurationInput) {
        // The author of the message will be muted
        final IUser muteUser = message.getAuthor();

        // Parse mute duration
        final CommandUtils.DurationParameters durationParameters = CommandUtils.parseDurationParameters(muteDurationInput);
        if (durationParameters == null) {
            DiscordIO.sendMessage(message.getChannel(),
                    "Ungültige Dauer angegeben. Mögliche Einheiten sind: s, m, h, d");
            return;
        }

        final IGuild guild = message.getGuild();

        // Mute the user and schedule unmute
        muteUserForGuild(muteUser, guild, durationParameters.getDuration(), durationParameters.getDurationUnit(), message.getChannel());

        message.addReaction(ReactionEmoji.of("\uD83D\uDD07")); // :mute:
    }

    @CommandSubscriber(command = "unmute", help = "Nutzer entmuten", pmAllowed = false,
            permissionLevel = PermissionLevel.MODERATOR)
    public void command_Unmute(final IMessage message, final String userString) {
        final IUser muteUser = UserUtils.getUserFromMessage(message, userString);

        unmuteUserForGuild(muteUser, message.getGuild(), message.getChannel());

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
    }

    /**
     * Mute a user for a specific duration on a guild by adding the mute role that is configured for the given guild.
     * An existing mute will be overridden, if the new mute lasts longer than the existing one. You can not override an
     * existing mute with one that will end before the existing one does.
     *
     * @param user The user that will be muted.
     * @param guild The guild for which the user will me muted.
     * @param muteDuration The duration until the user will be unmuted.
     * @param muteDurationUnit The time unit for the mute duration.
     * @param channel The channel to send error messages to. Can be null.
     */
    private void muteUserForGuild(final IUser user, final IGuild guild, final int muteDuration, final ChronoUnit muteDurationUnit, final IChannel channel) {
        // Get mute role for this guild
        final IRole muteRole = getMuteRoleForGuild(guild);
        if (muteRole == null) {
            if (channel != null) {
                DiscordIO.sendMessage(channel, ":x: Keine Mute-Rolle konfiguriert. Nutzer kann nicht gemuted werden.");
            }
            else {
                LOG.warn("No mute role configured for guild {} (ID: {}). Cannot mute user.",
                        guild.getName(), guild.getStringID());
            }
            return;
        }

        if (isUserMutedForGuild(user, guild)) {
            // User is already muted
            final ScheduledFuture oldFuture = userMuteFutures.get(guild).get(user);

            // Check which mute lasts longer
            final LocalDateTime oldDateTime = LocalDateTime.now().plusSeconds(oldFuture.getDelay(TimeUnit.SECONDS));
            final LocalDateTime newDateTime = LocalDateTime.now().plus(muteDuration, muteDurationUnit);

            if (newDateTime.isBefore(oldDateTime)) {
                // Existing mute lasts longer than the existing one -> Do nothing
                if (channel != null) {
                    DiscordIO.sendMessage(channel, ":x: Nutzer ist bereits für einen längeren Zeitraum gemuted.");
                }
                return;
            }
            else {
                // New mute lasts longer than the existing one -> override
                userMuteFutures.get(guild).remove(user, oldFuture);
                oldFuture.cancel(false);
            }
        }
        else {
            // User is not muted yet
            user.addRole(muteRole);
            LOG.info("Muted user {} for {} {}.", UserUtils.makeUserString(user, guild), muteDuration, muteDurationUnit.name());
        }

        // Task that will be run to unmute user
        final Runnable unmuteTask = () -> unmuteUserForGuild(user, guild, null);


        // Schedule the unmute task
        final ScheduledFuture newFuture = scheduler.schedule(unmuteTask, muteDuration, CommandUtils.toTimeUnit(muteDurationUnit));

        // Save the mute, so it can be restored after a restart of the bot
        if (userMuteFutures.containsKey(guild)) {
            userMuteFutures.get(guild).put(user, newFuture);
        }
        else {
            final Map<IUser, ScheduledFuture> guildMap = new HashMap<>();
            guildMap.put(user, newFuture);
            userMuteFutures.put(guild, guildMap);
        }

        saveMutedUsers();
    }

    /**
     * Unmutes a user for a guild, by removing the configured mute role.
     *
     * @param user The user to unmute.
     * @param guild The guild the user should be unmuted on.
     * @param channel The channel to send error messages to. Can be null.
     */
    private void unmuteUserForGuild (final IUser user, final IGuild guild, final IChannel channel) {
        // Get mute role for this guild
        final IRole muteRole = getMuteRoleForGuild(guild);
        if (muteRole == null) {
            if (channel != null) {
                DiscordIO.sendMessage(channel, ":x: Keine Mute-Rolle konfiguriert. Nutzer kann nicht entmuted werden.");
            }
            else {
                LOG.warn("No mute role configured for guild {} (ID: {}). Cannot unmute user.",
                        guild.getName(), guild.getStringID());
            }
            return;
        }

        // Only remove the mute role if th user is still a member of the guild
        if (guild.getUsers().contains(user) && user.hasRole(muteRole)) {
            user.removeRole(muteRole);
        }
        userMuteFutures.get(guild).remove(user);

        LOG.info("User {} was unmuted.", UserUtils.makeUserString(user, guild));
        saveMutedUsers();
    }

    /**
     * Check if a user is muted on a guild by checking if the user is in the {@link #userMuteFutures} map for the given guild.
     *
     * @param user The user.
     * @param guild The guild.
     * @return True if the user is muted on the given guild, otherwise false.
     */
    private boolean isUserMutedForGuild (final IUser user, final IGuild guild) {
        return (userMuteFutures.containsKey(guild) && userMuteFutures.get(guild).containsKey(user));
    }

    @CommandSubscriber(command = "channelMute", help = "Nutzer in einem Channel für eine bestimmte Zeit stummschalten",
            pmAllowed = false, permissionLevel = PermissionLevel.MODERATOR, ignoreParameterCount = true)
    public void command_channelMute(final IMessage message, final String userInput, final String channelOrMuteDuration) {
        // Parse user
        final IUser muteUser = UserUtils.getUserFromMessage(message, userInput);
        if (muteUser == null) {
            DiscordIO.sendMessage(message.getChannel(), ":x: Fehler: Nutzer nicht gefunden!");
            return;
        }

        // Parse channel
        if (channelOrMuteDuration == null || !channelOrMuteDuration.contains(" ")) {
            DiscordIO.sendMessage(message.getChannel(),
                    "Befehl ungültig! Format: `channelmute <Nutzer> [Kanal] <Dauer> [Hinweis]`");
                    return;
        }

        final int firstSpaceIndex = channelOrMuteDuration.indexOf(' ');
        IChannel muteChannel = ChannelUtils.getChannelFromMessage(message,
                channelOrMuteDuration.substring(0, firstSpaceIndex));

        // Parse mute duration and message
        final CommandUtils.DurationParameters durationParameters;
        if (muteChannel == null) {
            durationParameters = CommandUtils.parseDurationParameters(channelOrMuteDuration);

            // If channel is null, no channel was specified as a parameter -> Use the channel the command was sent in
            muteChannel = message.getChannel();
        }
        else {
            durationParameters = CommandUtils.parseDurationParameters(channelOrMuteDuration.substring(firstSpaceIndex + 1));
        }

        if (durationParameters == null) {
            DiscordIO.sendMessage(message.getChannel(),
                    "Ungültige Dauer angegeben. Mögliche Einheiten sind: s, m, h, d");
            return;
        }

        final int muteDuration = durationParameters.getDuration();
        final ChronoUnit muteDurationUnit = durationParameters.getDurationUnit();
        String customMessage = durationParameters.getMessage();

        if (customMessage == null || customMessage.isEmpty()) {
            customMessage = "kein";
        }

        // Mute user and schedule unmute
        final String output = muteUserForChannel(muteUser, muteChannel, muteDuration, muteDurationUnit);
        if (output.isEmpty()) {
            message.addReaction(ReactionEmoji.of("\uD83D\uDD07")); // :mute:
        }
        else {
            DiscordIO.sendMessage(message.getChannel(), output);
            return;
        }

        final IGuild guild = message.getGuild();

        final String muteMessage = String.format("**Du wurdest für %s %s für den Kanal %s auf dem Server %s gemuted!** \nHinweis: _%s_",
                muteDuration, muteDurationUnit.name(), muteChannel.getName(), guild.getName(), customMessage);

        // Do not notify a bot user
        if (!muteUser.isBot()) {
            DiscordIO.sendMessage(muteUser.getOrCreatePMChannel(), muteMessage);
        }

        // Modlog
        LOG.info("Nutzer {} wurde für {} {} für den Kanal {} auf dem Server {} gemuted. \nHinweis: {}",
                UserUtils.makeUserString(muteUser, guild), muteDuration,
                muteDurationUnit.name(),
                muteChannel.getName(),
                guild.getName(),
                customMessage);

        final IChannel modLogChannel = getModlogChannelForGuild(guild);

        if (modLogChannel != null) {
            final String modLogMessage = String.format("**%s** hat Nutzer **%s** im Kanal %s für %s %s für den Kanal %s **gemuted**. \nHinweis: _%s _",
                    UserUtils.makeUserString(message.getAuthor(), message.getGuild()),
                    UserUtils.makeUserString(muteUser, message.getGuild()), message.getChannel().mention(),
                    muteDuration, muteDurationUnit.name(), muteChannel.mention(), customMessage);
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
        final ScheduledFuture newFuture = scheduler.schedule(unmuteTask, muteDuration, CommandUtils.toTimeUnit(muteDurationUnit));

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
    public void command_setModlogChannel(final IMessage message, final String channelParameter) {
        final IChannel modlogChannel = ChannelUtils.getChannelFromMessage(message, channelParameter);
        if (modlogChannel == null) {
            // No valid channel was specified
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
    public void command_setMuteRole(final IMessage message, final String roleParameter) {
        final IRole muteRole = GuildUtils.getRoleFromMessage(message, roleParameter);
        if(muteRole == null) {
            // No valid role specified
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
        // Check if the user that joined should still be muted
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
        // Restore all mutes that can be found in the JSON file
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

    /**
     * Restore all mutes for the specified guild that can be found in the JSON file.
     *
     * @param guild The guild for which to restore the mutes
     */
    private void restoreGuildUserMutes(final IGuild guild) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        final JSONArray guildUserMutes = getUserMutesJSONForGuild(guild);
        LOG.debug("Found {} mutes for guild.", guildUserMutes.length());

        final Iterator<Object> muteIterator = guildUserMutes.iterator();

        while (muteIterator.hasNext()) {
            final JSONObject currentUserMute = (JSONObject)muteIterator.next();

            if (currentUserMute.has("user") && currentUserMute.has("mutedUntil")) {
                final long userLongID = currentUserMute.getLong("user");
                final IUser user = guild.getUserByID(userLongID);
                final String unmuteTimestampString = currentUserMute.getString("mutedUntil");
                final LocalDateTime unmuteTimestamp = LocalDateTime.parse(unmuteTimestampString, formatter);

                if (LocalDateTime.now().isBefore(unmuteTimestamp)) {
                    final int delaySeconds = (int)LocalDateTime.now().until(unmuteTimestamp, ChronoUnit.SECONDS);
                    muteUserForGuild(user, guild, delaySeconds, ChronoUnit.SECONDS, null);
                    LOG.info("Restored mute for user '{}' (ID: {}) on guild '{}' (ID: {}). Muted until {}",
                            UserUtils.makeUserString(user, guild), user.getStringID(),
                            guild.getName(), guild.getStringID(),
                            unmuteTimestampString);
                }
                else {
                    LOG.info("Mute for user '{}' (ID: {}) on guild '{}' (ID: {}) was found, but mute duration has elapsed. Removing from JSON.",
                            UserUtils.makeUserString(user, guild), user.getStringID(),
                            guild.getName(), guild.getStringID());
                }
            }
            else {
                LOG.warn(String.format("userMute doesn't contain necessary keys! Skipping. userMute: %s", currentUserMute.toString(4)));
            }
        }

        // Update JSON
        getJSONForGuild(guild).remove("userMutes");
        saveMutedUsers();
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
