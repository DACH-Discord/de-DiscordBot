package de.nikos410.discordbot.modules;

import de.nikos410.discordbot.util.discord.*;
import de.nikos410.discordbot.framework.annotations.CommandModule;
import de.nikos410.discordbot.framework.annotations.CommandSubscriber;

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

    @CommandSubscriber(command = "Ping", help = ":ping_pong:")
    public void command_ping(final IMessage message) {
        DiscordIO.sendMessage(message.getChannel(), "pong");
    }

    @CommandSubscriber(command = "uptime", help = "Zeigt seit wann der Bot online ist")
    public void command_uptime(final IMessage message) {
        final DateTimeFormatter timeStampFormatter = DateTimeFormatter.ofPattern("dd.MM. | HH:mm");
        DiscordIO.sendMessage(message.getChannel(), String.format("Online seit: %s", startupTimestamp.format(timeStampFormatter)));
    }

    @CommandSubscriber(command = "git", help = "Quellcode des Bots")
    public void command_git(final IMessage message) {
        DiscordIO.sendMessage(message.getChannel(), "https://github.com/DACH-Discord/de-DiscordBot/");
    }

    @CommandSubscriber(command = "quote", help = "Zitiert die Nachricht mit der angegebenen ID.", pmAllowed = false, passContext = false)
    public void command_quote(final IMessage commandMessage, final String id) {
        if (id.isEmpty()) {
            DiscordIO.sendMessage(commandMessage.getChannel(), "Keine ID angegeben!");
            return;
        }

        final IUser commandAuthor = commandMessage.getAuthor();
        final IGuild guild = commandMessage.getGuild();

        if (!id.matches("^[0-9]{18}$")) {
            DiscordIO.sendMessage(commandMessage.getChannel(), "Keine gültige ID eingegeben!");
            return;
        }
        final long quoteMessageID = Long.parseLong(id);
        final IMessage quoteMessage = guild.getMessageByID(quoteMessageID);

        if (quoteMessage == null) {
            DiscordIO.sendMessage(commandMessage.getChannel(), String.format("Nachricht mit der ID `%s` nicht gefunden!", quoteMessageID));
            return;
        }

        commandMessage.delete();

        final IUser quoteAuthor = quoteMessage.getAuthor();
        final IRole quoteAuthorTopRole = UserUtils.getTopRole(quoteAuthor, quoteMessage.getGuild());

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withAuthorIcon(quoteAuthor.getAvatarURL());
        embedBuilder.withAuthorName(UserUtils.makeUserString(quoteAuthor, guild));
        embedBuilder.withDesc(quoteMessage.getContent());
        embedBuilder.withColor(quoteAuthorTopRole.getColor());

        final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("dd.MM.YYYY, HH:mm");
        final String timestampString = quoteMessage.getTimestamp().format(timestampFormatter);

        embedBuilder.withFooterText(String.format("%s | Zitiert von: %s", timestampString, UserUtils.makeUserString(commandAuthor, guild)));

        DiscordIO.sendEmbed(commandMessage.getChannel(), embedBuilder.build());
    }
}
