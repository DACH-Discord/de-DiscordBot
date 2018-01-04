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
import sx.blah.discord.handle.impl.events.guild.member.UserJoinEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;

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
            pmAllowed = false, permissionLevel = CommandPermissions.EVERYONE)
    public void command_Kick(final IMessage message) {
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

            String customMessage = Util.getContext(message.getContent(), 2);
            if (customMessage.isEmpty()) {
                customMessage = "kein";
            }

            final String kickMessage = "**Du wurdest gekickt!** (Du kannst dem Server jedoch erneut beitreten)" +
                    "\nHinweis: _" + customMessage + '_';

            Util.sendPM(kickUser, kickMessage);
            message.getGuild().kickUser(kickUser);
            Util.sendMessage(message.getChannel(), ":door::arrow_left:");

            // Modlog
            IChannel modLogChannel = message.getGuild().getChannelByID(this.modlogChannelID);
            final String modLogMessage = String.format("**%s** hat Nutzer **%s** vom Server **gekickt**. \nHinweis: _%s_", Util.makeUserString(message.getAuthor(), message.getGuild()),
                    Util.makeUserString(kickUser, message.getGuild()),  customMessage);
            Util.sendMessage(modLogChannel, modLogMessage);
        }
        else {
            message.getGuild().kickUser(message.getAuthor());
            Util.sendMessage(message.getChannel(), ":tja:");
        }
    }

    @CommandSubscriber(command = "ban", help = "Bannt den angegebenen Nutzer mit der angegeben Nachricht vom Server",
            pmAllowed = false, permissionLevel = CommandPermissions.EVERYONE)
    public void command_Ban(final IMessage message) {
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

            String customMessage = Util.getContext(message.getContent(), 2);
            if (customMessage.isEmpty()) {
                customMessage = "kein";
            }

            final String banMessage = "**Du wurdest gebannt!** \nHinweis: _" + customMessage + '_';

            Util.sendPM(banUser, banMessage);
            message.getGuild().banUser(banUser);
            Util.sendMessage(message.getChannel(), ":door::arrow_left: :hammer:");

            // Modlog
            IChannel modLogChannel = message.getGuild().getChannelByID(this.modlogChannelID);
            final String modLogMessage = String.format("**%s** hat Nutzer **%s** vom Server **gebannt**. \nHinweis: _%s_", Util.makeUserString(message.getAuthor(), message.getGuild()),
                    Util.makeUserString(banUser, message.getGuild()),  customMessage);
            Util.sendMessage(modLogChannel, modLogMessage);
        }
        else {
            message.getGuild().kickUser(message.getAuthor());
            Util.sendMessage(message.getChannel(), ":tja:");
        }
    }

    @CommandSubscriber(command = "mute", help = "Einen Nutzer für eine bestimmte Zeit muten", pmAllowed = false,
            permissionLevel = CommandPermissions.MODERATOR)
    public void command_Mute(final IMessage message) {
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

        String muteDurationInput = Util.getContext(message.getContent(), 2);

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

        Util.sendMessage(message.getChannel(), "Nutzer für " + muteDuration + ' ' + muteDurationUnit + " gemuted.");

        if (customMessage.isEmpty()) {
            customMessage = "kein";
        }

        final String muteMessage = "**Du wurdest für " + muteDuration + ' ' + muteDurationUnit +
                " gemuted!** \nHinweis: _" + customMessage + '_';

        if (!muteUser.isBot()) {
            Util.sendPM(muteUser, muteMessage);
        }

        System.out.println("Muted user " + muteUser.getName() + '#' + muteUser.getDiscriminator() + " for " + muteDuration +
        ' ' + muteDurationUnit);

        // Modlog
        IChannel modLogChannel = message.getGuild().getChannelByID(this.modlogChannelID);
        final String modLogMessage = String.format("**%s** hat Nutzer **%s** für %s %s **gemuted**. \nHinweis: _%s_", Util.makeUserString(message.getAuthor(), message.getGuild()),
                Util.makeUserString(muteUser, message.getGuild()), muteDuration, muteDurationUnit, customMessage);
        Util.sendMessage(modLogChannel, modLogMessage);
    }

    @EventSubscriber
    public void onUserJoin(final UserJoinEvent event) {
        final IUser user = event.getUser();
        if (mutedUsers.contains(user.getStringID())) {
            IRole muteRole = event.getGuild().getRoleByID(muteRoleID);
            user.addRole(muteRole);
        }
    }
}
