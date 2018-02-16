package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.annotations.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.annotations.CommandSubscriber;

import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.EmbedBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@CommandModule(moduleName = "Allgemeine Befehle", commandOnly = true)
public class GeneralCommands {
    private final LocalDateTime startupTimestamp;

    public GeneralCommands () {
        startupTimestamp = LocalDateTime.now();
    }

    @CommandSubscriber(command = "Ping", help = ":ping_pong:", pmAllowed = true, permissionLevel = CommandPermissions.EVERYONE)
    public void command_Ping(final IMessage message) {
        Util.sendMessage(message.getChannel(), "pong");
    }

    @CommandSubscriber(command = "uptime", help = "Zeigt seit wann der Bot online ist", pmAllowed = true, permissionLevel = CommandPermissions.EVERYONE)
    public void command_Uptime(final IMessage message) {
        final DateTimeFormatter timeStampFormatter = DateTimeFormatter.ofPattern("dd.MM. | HH:mm");
        Util.sendMessage(message.getChannel(), "Online seit: " + startupTimestamp.format(timeStampFormatter));
    }

    @CommandSubscriber(command = "git", help = "Quellcode des Bots", pmAllowed = true, permissionLevel = CommandPermissions.EVERYONE)
    public void command_Git(final IMessage message) {
        Util.sendMessage(message.getChannel(), "https://github.com/DACH-Discord/de-DiscordBot/");
    }

    @CommandSubscriber(command = "quote", help = "Zitiert die Nachricht mit der angegebenen ID", pmAllowed = false, permissionLevel = CommandPermissions.EVERYONE)
    public void command_Quote(final IMessage commandMessage, final String id) {
        if (id.isEmpty()) {
            Util.sendMessage(commandMessage.getChannel(), "Keine ID angegeben!");
            return;
        }

        final IUser commandAuthor = commandMessage.getAuthor();
        final IGuild guild = commandMessage.getGuild();
        final long quoteMessageID = Long.parseLong(id);
        final IMessage quoteMessage = guild.getMessageByID(quoteMessageID);

        if (quoteMessage == null) {
            Util.sendMessage(commandMessage.getChannel(), "Nachricht mit der ID `" + quoteMessageID + "` nicht gefunden!");
            return;
        }

        commandMessage.delete();

        final IUser quoteAuthor = quoteMessage.getAuthor();
        final IRole quoteAuthorTopRole = Util.getTopRole(quoteAuthor, quoteMessage.getGuild());

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withAuthorIcon(quoteAuthor.getAvatarURL());
        embedBuilder.withAuthorName(Util.makeUserString(quoteAuthor, guild));
        embedBuilder.withDesc(quoteMessage.getContent());
        embedBuilder.withColor(quoteAuthorTopRole.getColor());

        final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("dd.MM.YYYY, HH:mm");
        final String timestampString = quoteMessage.getTimestamp().format(timestampFormatter);

        embedBuilder.withFooterText(timestampString + " | Zitiert von: " + Util.makeUserString(commandAuthor, guild));

        Util.sendEmbed(commandMessage.getChannel(), embedBuilder.build());
    }
}
