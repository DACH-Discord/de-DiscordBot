package nikos.discordBot.modules;

import nikos.discordBot.util.Util;
import org.json.JSONObject;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.RequestBuffer;

import java.awt.Color;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Poll {
    private static IDiscordClient client;

    private final static Path CONFIG_PATH = Paths.get("config/config.json");

    private final static String MODULE_NAME = "Spiele";
    private final static char SEPARATOR = '⠀';
    private final static String COMMANDS = "`poll           " + SEPARATOR + "`  startet eine Abstimmung";

    private final String prefix;

    public Poll(IDiscordClient dClient) {
        client = dClient;

        // Prefix und Owner ID aus Config-Datei auslesen
        final String configFileContent = Util.readFile(CONFIG_PATH);
        if (configFileContent == null) {
            throw new RuntimeException("[ERR] Config-Datei konnte nicht gelesen werden!");
        }
        final JSONObject json = new JSONObject(configFileContent);
        this.prefix = json.getString("prefix");
    }

    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) throws InterruptedException {
        final IMessage message = event.getMessage();
        final String messageContent = message.getContent();

        if (messageContent.toLowerCase().startsWith(prefix + "poll")) {
            this.command_Poll(message);
        }
        if (messageContent.equalsIgnoreCase(prefix + "help")) {
            this.command_Help(message);
        }
    }

    private void command_Help(final IMessage message) throws InterruptedException {
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(new Color(114, 137, 218));
        embedBuilder.appendField(MODULE_NAME, COMMANDS, false);
        embedBuilder.withFooterText("Syntax: " + prefix + "poll Frage;Option 1;Option 2;Option n;Dauer in Sekunden`");

        final EmbedObject embedObject = embedBuilder.build();

        Util.sendEmbed(message.getChannel(), embedObject);
    }

    private void command_Poll(final IMessage commandMessage) throws InterruptedException {
        final String messageContent = commandMessage.getContent();
        final IChannel channel = commandMessage.getChannel();
        final String context = Util.getContext(messageContent);

        if (context.matches(".+;.+;.+;[0-9]+") || context.matches(".+;.+;.+;.+;[0-9]+") ||
                context.matches(".+;.+;.+;.+;.+;[0-9]+") || context.matches(".+;.+;.+;.+;.+;.+;[0-9]+")) {

            final String[] pollArgs = context.split(";");
            final int optionsCount = pollArgs.length-2;
            final int pollSeconds = Integer.parseInt(pollArgs[pollArgs.length-1]);
            final int pollMilliSeconds = (pollSeconds * 1000);

            if (optionsCount > 5) {
                Util.sendMessage(channel, "Maximal 5 Optionen!");
                return;
            }

            if (pollSeconds > 86400) {
                Util.sendMessage(channel, "Maximal 24 Stunden!");
                return;
            }
            final String timeString = makeTimeString(pollSeconds);

            final EmbedBuilder pollBuilder = new EmbedBuilder();
            pollBuilder.appendField(":question: __" + pollArgs[0] + "__", makeOptionString(pollArgs, optionsCount), false);
            pollBuilder.withFooterText("Abstimmen mithilfe der Emotes! Dauer: " + timeString);
            final EmbedObject pollObject = pollBuilder.build();

            final IMessage pollMessage = Util.sendEmbed(channel, pollObject);

            addPollReactions(pollMessage, optionsCount);

            Thread.sleep(pollMilliSeconds);

            pollMessage.removeAllReactions();

            String voteResult = "";

            voteResult = voteResult + "__" + pollArgs[1] + "__: **" +
                         (pollMessage.getReactionByName("\uD83C\uDDE6").getCount()-1) + "** Stimmen" + '\n';
            voteResult = voteResult + "__" + pollArgs[2] + "__: **" +
                         (pollMessage.getReactionByName("\uD83C\uDDE7").getCount()-1) + "** Stimmen" + '\n';
            if (optionsCount >= 3) {
                voteResult = voteResult + "__" + pollArgs[3] + "__: **" +
                             (pollMessage.getReactionByName("\uD83C\uDDE8").getCount()-1) + "** Stimmen" + '\n';
            }
            if (optionsCount >= 4) {
                voteResult = voteResult + "__" + pollArgs[4] + "__: **" +
                             (pollMessage.getReactionByName("\uD83C\uDDE9").getCount()-1) + "** Stimmen" + '\n';
            }
            if (optionsCount >= 5) {
                voteResult = voteResult + "__" + pollArgs[5] + "__: **" +
                             (pollMessage.getReactionByName("\uD83C\uDDEA").getCount()-1) + "** Stimmen" ;
            }

            final EmbedBuilder resultBuilder = new EmbedBuilder();
            resultBuilder.appendField("__Ergebnis:__", voteResult, false);
            resultBuilder.withFooterText(pollArgs[0]);
            final EmbedObject resultObject = resultBuilder.build();

            Util.sendEmbed(channel, resultObject);
        }
        else {
            Util.sendMessage(channel, "Syntax: `" + prefix + "poll Frage;Option 1;Option 2;Option n;Dauer in Sekunden`");
        }
    }

    private static String makeTimeString(final int totalSeconds) {
        final int seconds = totalSeconds % 60;
        final int minutes = (totalSeconds % 3600) / 60;
        final int hours = totalSeconds / 3600;

        final StringBuilder builder = new StringBuilder();
        if (hours > 0) {
            builder.append(hours + " Stunden");

            if (minutes > 0 || seconds > 0) {
                builder.append(", ");
            }
        }
        if (minutes > 0) {
            builder.append(minutes + " Minuten");

            if (seconds > 0) {
                builder.append(", ");
            }
        }
        if (seconds > 0) {
            builder.append(seconds + " Sekunden");
        }

        return builder.toString();
    }

    private static String makeOptionString(final String[] pollArgs, final int optionCount) {
        String options = "";

        for (int i = 1; i <= optionCount; i++) {
            String letterEmoji = "-";
            switch (i) {
                case 1: letterEmoji = ":regional_indicator_a:"; break;
                case 2: letterEmoji = ":regional_indicator_b:"; break;
                case 3: letterEmoji = ":regional_indicator_c:"; break;
                case 4: letterEmoji = ":regional_indicator_d:"; break;
                case 5: letterEmoji = ":regional_indicator_e:"; break;
            }

            String option = pollArgs[i];
            options = options + letterEmoji + "  " + option + '\n';
        }

        return options;
    }

    private static void addPollReactions(final IMessage message, final int optionCount) throws InterruptedException {
            for (int i = 0; i < optionCount; i++) {

                final String emoji;
                switch (i) {

                    case 0: emoji = "\uD83C\uDDE6"; // A
                            break;
                    case 1: emoji = "\uD83C\uDDE7"; // B
                            break;
                    case 2: emoji = "\uD83C\uDDE8"; // C
                            break;
                    case 3: emoji = "\uD83C\uDDE9"; // D
                            break;
                    case 4: emoji = "\uD83C\uDDEA"; // E
                            break;
                    default: emoji = "";
                }

                message.addReaction(emoji);

                Thread.sleep(80);
            }
    }
}
