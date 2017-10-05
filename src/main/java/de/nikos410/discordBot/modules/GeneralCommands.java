package de.nikos410.discordBot.modules;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.CommandSubscriber;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RateLimitException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@CommandModule(moduleName = "Allgemeine Befehle", commandOnly = true)
public class GeneralCommands {
    private final DiscordBot bot;

    private final LocalDateTime startupTimestamp;

    public GeneralCommands (final DiscordBot bot) {
        this.bot = bot;

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
        Util.sendMessage(message.getChannel(), "https://github.com/Nikos410/de-DiscordBot/");
    }

    @CommandSubscriber(command = "quote", help = "Zitiert die Nachricht mit der angegebenen ID", pmAllowed = false, permissionLevel = CommandPermissions.EVERYONE)
    public void command_Quote(final IMessage commandMessage) {
        if (!commandMessage.getContent().contains(" ")) {
            Util.sendMessage(commandMessage.getChannel(), "Keine ID angegeben!");
            return;
        }

        final IUser commandUser = commandMessage.getAuthor();
        final IGuild commandGuild = commandMessage.getGuild();
        final long quoteID = Long.parseLong(Util.getContext(commandMessage.getContent()));
        final IMessage quoteMessage = commandGuild.getMessageByID(quoteID);
        if (quoteMessage == null) {
            Util.sendMessage(commandMessage.getChannel(), "Nachricht mit der ID `" + quoteID + "` nicht gefunden!");
            return;
        }
        final IUser quoteAuthor = quoteMessage.getAuthor();
        final IRole quoteAuthorTopRole = Util.getTopRole(quoteAuthor, quoteMessage.getGuild());

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withAuthorIcon(quoteAuthor.getAvatarURL());
        embedBuilder.withAuthorName(quoteAuthor.getDisplayName(quoteMessage.getGuild()));
        embedBuilder.withDesc(quoteMessage.getContent());
        embedBuilder.withColor(quoteAuthorTopRole.getColor());

        final DateTimeFormatter timeStampFormatter = DateTimeFormatter.ofPattern("dd.MM.YYYY | HH:mm");
        embedBuilder.withFooterText(quoteMessage.getTimestamp().format(timeStampFormatter));


        EmbedObject embedObject = embedBuilder.build();

        Util.sendBufferedEmbed(commandMessage.getChannel(), embedObject);
    }
}
