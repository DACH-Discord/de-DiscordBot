package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.util.discord.*;
import de.nikos410.discordBot.framework.annotations.CommandModule;
import de.nikos410.discordBot.framework.annotations.CommandSubscriber;

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
    public void command_Ping(final IMessage message) {
        DiscordIO.sendMessage(message.getChannel(), "pong");
    }

    @CommandSubscriber(command = "uptime", help = "Zeigt seit wann der Bot online ist")
    public void command_Uptime(final IMessage message) {
        final DateTimeFormatter timeStampFormatter = DateTimeFormatter.ofPattern("dd.MM. | HH:mm");
        DiscordIO.sendMessage(message.getChannel(), String.format("Online seit: %s", startupTimestamp.format(timeStampFormatter)));
    }

    @CommandSubscriber(command = "git", help = "Quellcode des Bots")
    public void command_Git(final IMessage message) {
        DiscordIO.sendMessage(message.getChannel(), "https://github.com/DACH-Discord/de-DiscordBot/");
    }

    @CommandSubscriber(command = "quote", help = "Zitiert die Nachricht mit der angegebenen ID.", pmAllowed = false, passContext = false)
    public void command_Quote(final IMessage commandMessage, final String id) {
        if (id.isEmpty()) {
            DiscordIO.sendMessage(commandMessage.getChannel(), "Keine ID angegeben!");
            return;
        }

        final IUser commandAuthor = commandMessage.getAuthor();
        final IGuild guild = commandMessage.getGuild();
        final long quoteMessageID = Long.parseLong(id);
        final IMessage quoteMessage = guild.getMessageByID(quoteMessageID);

        if (quoteMessage == null) {
            DiscordIO.sendMessage(commandMessage.getChannel(), String.format("Nachricht mit der ID `%s` nicht gefunden!", quoteMessageID));
            return;
        }

        commandMessage.delete();

        final IUser quoteAuthor = quoteMessage.getAuthor();
        final IRole quoteAuthorTopRole = UserOperations.getTopRole(quoteAuthor, quoteMessage.getGuild());

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withAuthorIcon(quoteAuthor.getAvatarURL());
        embedBuilder.withAuthorName(UserOperations.makeUserString(quoteAuthor, guild));
        embedBuilder.withDesc(quoteMessage.getContent());
        embedBuilder.withColor(quoteAuthorTopRole.getColor());

        final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("dd.MM.YYYY, HH:mm");
        final String timestampString = quoteMessage.getTimestamp().format(timestampFormatter);

        embedBuilder.withFooterText(String.format("%s | Zitiert von: %s", timestampString, UserOperations.makeUserString(commandAuthor, guild)));

        DiscordIO.sendEmbed(commandMessage.getChannel(), embedBuilder.build());
    }
}
