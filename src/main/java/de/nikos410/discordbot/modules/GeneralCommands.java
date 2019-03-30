package de.nikos410.discordbot.modules;

import de.nikos410.discordbot.framework.CommandModule;
import de.nikos410.discordbot.framework.annotations.CommandSubscriber;
import de.nikos410.discordbot.util.discord.UserUtils;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

public class GeneralCommands extends CommandModule {
    private final LocalDateTime startupTimestamp;

    public GeneralCommands() {
        startupTimestamp = LocalDateTime.now();
    }

    @Override
    public String getDisplayName() {
        return "Allgemeines";
    }

    @Override
    public String getDescription() {
        return "Allgemeine Befehle";
    }

    @CommandSubscriber(command = "Ping", help = ":ping_pong:")
    public void command_ping(final IMessage message) {
        messageService.sendMessage(message.getChannel(), "pong");
    }

    @CommandSubscriber(command = "uptime", help = "Zeigt seit wann der Bot online ist")
    public void command_uptime(final IMessage message) {
        final DateTimeFormatter timeStampFormatter = DateTimeFormatter.ofPattern("dd.MM. | HH:mm");
        messageService.sendMessage(message.getChannel(), String.format("Online seit: %s", startupTimestamp.format(timeStampFormatter)));
    }

    @CommandSubscriber(command = "git", help = "Quellcode des Bots")
    public void command_git(final IMessage message) {
        messageService.sendMessage(message.getChannel(), "https://github.com/DACH-Discord/de-DiscordBot/");
    }

    @CommandSubscriber(command = "quote", help = "Zitiert die Nachricht mit der angegebenen ID.", pmAllowed = false, passContext = false)
    public void command_quote(final IMessage commandMessage, final String id) {
        if (id.isEmpty()) {
            messageService.sendMessage(commandMessage.getChannel(), "Keine ID angegeben!");
            return;
        }

        final IUser commandAuthor = commandMessage.getAuthor();
        final IGuild guild = commandMessage.getGuild();

        if (!id.matches("^[0-9]{18}$")) {
            messageService.sendMessage(commandMessage.getChannel(), "Keine gültige ID eingegeben!");
            return;
        }
        final long quoteMessageID = Long.parseLong(id);
        final Optional<IMessage> optQuoteMessage = guild.getChannels().parallelStream()
                .map(c -> c.fetchMessage(quoteMessageID))
                .filter(Objects::nonNull).findFirst();

        if (!optQuoteMessage.isPresent()) {
            messageService.sendMessage(commandMessage.getChannel(), String.format("Nachricht mit der ID `%s` nicht gefunden!", quoteMessageID));
            return;
        }

        final IMessage quoteMessage = optQuoteMessage.get();

        commandMessage.delete();

        final IUser quoteAuthor = quoteMessage.getAuthor();
        final IRole quoteAuthorTopRole = UserUtils.getTopRole(quoteAuthor, quoteMessage.getGuild());

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withAuthorIcon(quoteAuthor.getAvatarURL());
        embedBuilder.withAuthorName(UserUtils.makeUserString(quoteAuthor, guild));
        embedBuilder.withDesc(quoteMessage.getContent());
        embedBuilder.withColor(quoteAuthorTopRole.getColor());

        final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("dd.MM.YYYY, HH:mm");
        final LocalDateTime timestamp = LocalDateTime.ofInstant(quoteMessage.getTimestamp(), ZoneOffset.UTC);
        final String timestampString = timestamp.format(timestampFormatter);

        embedBuilder.withFooterText(String.format("%s | Zitiert von: %s", timestampString, UserUtils.makeUserString(commandAuthor, guild)));

        messageService.sendEmbed(commandMessage.getChannel(), embedBuilder.build());
    }
}
