package de.nikos410.discordBot.modules;

import java.nio.file.Path;
import java.nio.file.Paths;
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

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.discord.DiscordIO;
import de.nikos410.discordBot.util.discord.GuildOperations;
import de.nikos410.discordBot.util.discord.UserOperations;
import de.nikos410.discordBot.modular.annotations.CommandModule;
import de.nikos410.discordBot.modular.CommandPermissions;
import de.nikos410.discordBot.modular.annotations.CommandSubscriber;

import de.nikos410.discordBot.util.io.IOUtil;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.member.UserBanEvent;
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.*;

@CommandModule(moduleName = "Modzeugs", commandOnly = false)
public class ModStuff {
    private final DiscordBot bot;

    private final static Path MODSTUFF_PATH = Paths.get("data/modstuff.json");
    private JSONObject modstuffJSON;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private HashMap<String, HashMap<String, ScheduledFuture>> mutedUsers = new HashMap<>();

    private Logger log = LoggerFactory.getLogger(ModStuff.class);

    public ModStuff (final DiscordBot bot) {
        this.bot = bot;
        final IDiscordClient client = bot.client;

        final String rolesFileContent = IOUtil.readFile(MODSTUFF_PATH);
        if (rolesFileContent == null) {
            log.error("Could not read modstuff file.");
            System.exit(1);
        }
        this.modstuffJSON = new JSONObject(rolesFileContent);
        log.info(String.format("Loaded modstuff file for %s guilds.", modstuffJSON.keySet().size()));
    }

    @CommandSubscriber(command = "kick", help = "Kickt den angegebenen Nutzer mit der angegeben Nachricht vom Server",
            pmAllowed = false)
    public void command_Kick(final IMessage message, final String kickUserString, String customMessage) {
        if (this.bot.getUserPermissionLevel(message.getAuthor(), message.getGuild()) >= CommandPermissions.MODERATOR) {

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
            log.info(String.format("%s hat Nutzer %s vom Server gekickt. Hinweis: %s",
                    UserOperations.makeUserString(message.getAuthor(), message.getGuild()),
                    UserOperations.makeUserString(kickUser, message.getGuild()),
                    customMessage));

            final IGuild guild = message.getGuild();
            final IChannel modLogChannel = getModlogChannel(guild);

            if (modLogChannel != null) {
                final String modLogMessage = String.format("**%s** hat Nutzer **%s** im Kanal %s vom Server **gekickt**. \nHinweis: _%s _",
                        UserOperations.makeUserString(message.getAuthor(), guild),
                        UserOperations.makeUserString(kickUser, guild),
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
        if (this.bot.getUserPermissionLevel(message.getAuthor(), message.getGuild()) >= CommandPermissions.MODERATOR) {

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
            log.info(String.format("%s hat Nutzer %s vom Server gebannt. Hinweis: %s",
                    UserOperations.makeUserString(message.getAuthor(), message.getGuild()),
                    UserOperations.makeUserString(banUser, message.getGuild()),
                    customMessage));

            final IGuild guild = message.getGuild();
            final IChannel modLogChannel = getModlogChannel(guild);

            if (modLogChannel != null) {
                final String modLogMessage = String.format("**%s** hat Nutzer **%s** im Kanal %s vom Server **gebannt**. \nHinweis: _%s _",
                        UserOperations.makeUserString(message.getAuthor(), guild),
                        UserOperations.makeUserString(banUser, guild),
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
        final IChannel modLogChannel = getModlogChannel(guild);

        if (modLogChannel != null) {
            final String modLogMessage = String.format("**%s** wurde vom Server **gebannt**.",
                    UserOperations.makeUserString(event.getUser(), event.getGuild()));
            DiscordIO.sendMessage(modLogChannel, modLogMessage);
        }
    }

    @CommandSubscriber(command = "mute", help = "Einen Nutzer für eine bestimmte Zeit muten", pmAllowed = false,
            permissionLevel = CommandPermissions.MODERATOR)
    public void command_Mute(final IMessage message, final String muteUserString, final String muteDurationInput) {

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

        Pattern pattern = Pattern.compile("(\\d+)\\s?([smhd])\\s?(.*)");
        Matcher matcher = pattern.matcher(muteDurationInput);

        if (!matcher.matches()) {
            DiscordIO.sendMessage(message.getChannel(), "Ungültige Eingabe! Mögliche Zeitformate sind s, m, h und d.");
            return;
        }

        final IGuild guild = message.getGuild();
        final IRole muteRole = getMuteRole(guild);

        if (muteRole == null) {
            DiscordIO.sendMessage(message.getChannel(), "Keine gültige Mute Rolle konfiguriert!");
            return;
        }

        // Wird ausgeführt, um Nutzer wieder zu entmuten
        final Runnable unmuteTask = () -> {
            muteUser.removeRole(muteRole);
            mutedUsers.get(guild.getStringID()).remove(muteUser.getStringID());

            log.info(String.format("Nutzer %s wurde entmuted.", UserOperations.makeUserString(muteUser, message.getGuild())));
        };

        final int muteDuration = Integer.parseInt(matcher.group(1));
        final String muteDurationUnitString = matcher.group(2);
        ChronoUnit muteDurationUnit = parseChronoUnit(muteDurationUnitString);

        // Prüfen ob Nutzer bereits gemuted ist
        if (mutedUsers.containsKey(guild.getStringID()) && mutedUsers.get(guild.getStringID()).containsKey(muteUser.getStringID())) {
            // Überprüfen, ob angegebener Zeitpunkt nach dem bisherigen Zeitpunkt liegt
            ScheduledFuture oldFuture = mutedUsers.get(guild.getStringID()).get(muteUser.getStringID());

            LocalDateTime oldDateTime = LocalDateTime.now().plusSeconds(oldFuture.getDelay(TimeUnit.SECONDS));
            LocalDateTime newDateTime = LocalDateTime.now().plus(muteDuration, muteDurationUnit);

            if (newDateTime.isBefore(oldDateTime)) {
                // neuer Zeitpunkt ist vor altem -> nichts tun (längerer Mute bleibt bestehen)
                DiscordIO.sendMessage(message.getChannel(), "Nutzer ist bereits für einen längeren Zeitraum gemuted!");
                return;
            }
            else {
                // neuer Zeitpunkt ist nach altem -> neu schedulen
                mutedUsers.get(guild.getStringID()).remove(muteUser.getStringID(), oldFuture);
                oldFuture.cancel(false);
            }
        }
        else {
            muteUser.addRole(muteRole);
            log.info(String.format("Muted user %s.", UserOperations.makeUserString(muteUser, message.getGuild())));
        }

        ScheduledFuture newFuture = scheduler.schedule(unmuteTask, muteDuration, chronoUnitToTimeUnit(muteDurationUnit));

        if (mutedUsers.containsKey(guild.getStringID())) {
            mutedUsers.get(guild.getStringID()).put(muteUser.getStringID(), newFuture);
        }
        else {
            final HashMap<String, ScheduledFuture> guildMap = new HashMap<>();
            guildMap.put(muteUser.getStringID(), newFuture);
            mutedUsers.put(guild.getStringID(), guildMap);
        }

        message.addReaction(ReactionEmoji.of("\uD83D\uDD07")); // :mute:

        String customMessage = matcher.group(3);

        if (customMessage.isEmpty()) {
            customMessage = "kein";
        }

        final String muteMessage = String.format("**Du wurdest für %s %S gemuted!** \nHinweis: _%s_",
                muteDuration, muteDurationUnitString, customMessage);

        if (!muteUser.isBot()) {
            DiscordIO.sendMessage(muteUser.getOrCreatePMChannel(), muteMessage);
        }

        // Modlog

        log.info(String.format("Nutzer %s wurde für %s gemuted.", UserOperations.makeUserString(muteUser, message.getGuild()), muteDurationInput));

        final IChannel modLogChannel = getModlogChannel(guild);

        if (modLogChannel != null) {
            final String modLogMessage = String.format("**%s** hat Nutzer **%s** im Kanal %s für %s %s **gemuted**. \nHinweis: _%s _",
                    UserOperations.makeUserString(message.getAuthor(), message.getGuild()),
                    UserOperations.makeUserString(muteUser, message.getGuild()), message.getChannel().mention(),
                    muteDuration, muteDurationUnitString, customMessage);
            DiscordIO.sendMessage(modLogChannel, modLogMessage);
        }
    }

    @CommandSubscriber(command = "unmute", help = "Nutzer entmuten", pmAllowed = false,
            permissionLevel = CommandPermissions.MODERATOR)
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
        final IRole muteRole = getMuteRole(message.getGuild());

        if (muteRole == null) {
            DiscordIO.sendMessage(message.getChannel(), "Keine gültige Mute Rolle konfiguriert!");
            return;
        }

        muteUser.removeRole(muteRole);

        if (mutedUsers.containsKey(message.getGuild().getStringID()) &&
                mutedUsers.get(message.getGuild().getStringID()).containsKey(muteUser.getStringID())) {
            ScheduledFuture future = mutedUsers.get(message.getGuild().getStringID()).get(muteUser.getStringID());
            future.cancel(false);
        }

        message.addReaction(ReactionEmoji.of("✅")); // :white_check_mark:
    }

    @CommandSubscriber(command = "selfmute", help = "Schalte dich selber für die angegebene Zeit stumm", pmAllowed = false)
    public void command_Selfmute(final IMessage message, final String muteDurationInput) {
        IUser muteUser = message.getAuthor();

        Pattern pattern = Pattern.compile("(\\d+)\\s?([smhd])\\s?(.*)");
        Matcher matcher = pattern.matcher(muteDurationInput);

        if (!matcher.matches()) {
            DiscordIO.sendMessage(message.getChannel(), "Ungültige Eingabe! Mögliche Zeitformate sind s, m, h und d.");
            return;
        }

        final IRole muteRole = getMuteRole(message.getGuild());

        if (muteRole == null) {
            DiscordIO.sendMessage(message.getChannel(), "Keine gültige Mute Rolle konfiguriert!");
            return;
        }

        // Wird ausgeführt, um Nutzer wieder zu entmuten
        Runnable unmuteTask = () -> {
            muteUser.removeRole(muteRole);
            mutedUsers.get(message.getGuild().getStringID()).remove(muteUser.getStringID());

            log.info(String.format("Unmuted user %s. (Was selfmuted)", UserOperations.makeUserString(muteUser, message.getGuild())));

        };

        final int muteDuration = Integer.parseInt(matcher.group(1));
        final String muteDurationUnitString = matcher.group(2);
        final ChronoUnit muteDurationUnit = parseChronoUnit(muteDurationUnitString);

        final LocalDateTime muteEnd = LocalDateTime.now().plus(muteDuration, muteDurationUnit);
        if (muteEnd.isAfter(LocalDateTime.now().plusDays(1))) {
            // Länger als 1 Tag
            DiscordIO.sendMessage(message.getChannel(), "Du kannst dich für maximal einen Tag muten!");
            return;
        }

        final IGuild guild = message.getGuild();
        if (mutedUsers.containsKey(guild.getStringID()) && mutedUsers.get(guild.getStringID()).containsKey(muteUser.getStringID())) {
            // Überprüfen, ob angegebener Zeitpunkt nach dem bisherigen Zeitpunkt liegt
            ScheduledFuture oldFuture = mutedUsers.get(guild.getStringID()).get(muteUser.getStringID());

            LocalDateTime oldMuteEnd = LocalDateTime.now().plusSeconds(oldFuture.getDelay(TimeUnit.SECONDS));

            if (muteEnd.isBefore(oldMuteEnd)) {
                // neuer Zeitpunkt ist vor altem -> nichts tun (längerer Mute bleibt bestehen)
                DiscordIO.sendMessage(message.getChannel(), "Nutzer ist bereits für einen längeren Zeitraum gemuted!");
                return;
            }
            else {
                // neuer Zeitpunkt ist nach altem -> neu schedulen
                mutedUsers.get(guild.getStringID()).remove(muteUser.getStringID(), oldFuture);
                oldFuture.cancel(false);
            }
        }
        else {
            muteUser.addRole(muteRole);
            log.info(String.format("User %s selfmuted.", UserOperations.makeUserString(muteUser, message.getGuild())));
        }

        ScheduledFuture newFuture = scheduler.schedule(unmuteTask, muteDuration, chronoUnitToTimeUnit(muteDurationUnit));

        if (mutedUsers.containsKey(guild.getStringID())) {
            mutedUsers.get(guild.getStringID()).put(muteUser.getStringID(), newFuture);
        }
        else {
            final HashMap<String, ScheduledFuture> guildMap = new HashMap<>();
            guildMap.put(muteUser.getStringID(), newFuture);
            mutedUsers.put(guild.getStringID(), guildMap);
        }

        message.addReaction(ReactionEmoji.of("\uD83D\uDD07")); // :mute:
    }

    @CommandSubscriber(command = "setModlogChannel", help = "Kanal in dem die Modlog Nachrichten gesendet werden einstellen",
            pmAllowed = false, passContext = false, permissionLevel = CommandPermissions.ADMIN)
    public void command_setModlogChannel(final IMessage message, final String channel) {
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
            pmAllowed = false, passContext = false, permissionLevel = CommandPermissions.ADMIN)
    public void command_setMuteRole(final IMessage message, final String role) {
        final IRole muteRole;
        final List<IRole> roleMentions = message.getRoleMentions();

        if (GuildOperations.hasRoleByID(message.getGuild(), role)) {
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
        if (mutedUsers.containsKey(event.getGuild().getStringID()) && mutedUsers.get(event.getGuild().getStringID()).containsKey(user.getStringID())) {
            final IRole muteRole = getMuteRole(event.getGuild());
            if (muteRole == null) {
                return;
            }

            user.addRole(muteRole);
        }
    }

    private IRole getMuteRole (final IGuild guild) {
        if (modstuffJSON.has(guild.getStringID())) {
            final JSONObject guildJSON = modstuffJSON.getJSONObject(guild.getStringID());
            if (guildJSON.has("muteRole")) {
                final long muteRoleID = guildJSON.getLong("muteRole");
                final IRole muteRole = guild.getRoleByID(muteRoleID);
                if (muteRole != null) {
                    return muteRole;
                }
                else {
                    log.warn(String.format("Auf dem Server %s (ID: %s) wurde keine Rolle mit der ID %s gefunden!", guild.getName(), guild.getStringID(), muteRoleID));
                    return null;
                }
            }
            else {
                log.warn(String.format("Keine Mute Rolle für Server %s (ID: %s) angegeben.",
                        guild.getName(), guild.getStringID()));
                return null;
            }
        }
        else {
            log.warn(String.format("Mute Rolle nicht gefunden! Kein Eintrag für Server %s (ID: %s).",
                    guild.getName(), guild.getStringID()));
            return null;
        }
    }

    private IChannel getModlogChannel (final IGuild guild) {
        if (modstuffJSON.has(guild.getStringID())) {
            final JSONObject guildJSON = modstuffJSON.getJSONObject(guild.getStringID());
            if (guildJSON.has("modlogChannel")) {
                final long modlogChannelID = guildJSON.getLong("modlogChannel");
                final IChannel modlogChannel = guild.getChannelByID(modlogChannelID);
                if (modlogChannel != null) {
                    return modlogChannel;
                }
                else {
                    log.warn(String.format("Auf dem Server %s (ID: %s) wurde kein Channel mit der ID %s gefunden!",
                            guild.getName(), guild.getStringID(), modlogChannelID));
                    return null;
                }
            }
            else {
                log.warn(String.format("Kein Modlog Channel für Server %s (ID: %s) angegeben.",
                        guild.getName(), guild.getStringID()));
                return null;
            }
        }
        else {
            log.warn(String.format("Modlog Channel nicht gefunden! Kein Eintrag für Server %s (ID: %s).",
                    guild.getName(), guild.getStringID()));
            return null;
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

    public void saveJSON() {
        log.debug("Saving modstuff file.");

        final String jsonOutput = this.modstuffJSON.toString(4);
        IOUtil.writeToFile(MODSTUFF_PATH, jsonOutput);
    }
}
