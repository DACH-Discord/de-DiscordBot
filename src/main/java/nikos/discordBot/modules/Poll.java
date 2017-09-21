package nikos.discordBot.modules;

import nikos.discordBot.util.Util;
import org.json.JSONObject;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.*;

import java.awt.Color;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class Poll {
    private final static String EMOJI_A = "\uD83C\uDDE6"; // A
    private final static String EMOJI_B = "\uD83C\uDDE7"; // B
    private final static String EMOJI_C = "\uD83C\uDDE8"; // C
    private final static String EMOJI_D = "\uD83C\uDDE9"; // D
    private final static String EMOJI_E = "\uD83C\uDDEA"; // E

    private final static Path CONFIG_PATH = Paths.get("config/config.json");

    private final static String MODULE_NAME = "Poll";
    private final static char SEPARATOR = '⠀';
    private final static String COMMANDS = "`poll           " + SEPARATOR + "`  startet eine Abstimmung";
    private final static String SYNTAX = "poll Frage;Option 1;Option 2;Option n;Dauer (in Sekunden)`";

    private final String prefix;

    public Poll(IDiscordClient dClient) {
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
        embedBuilder.withFooterText("Syntax: " + prefix + SYNTAX);

        final EmbedObject embedObject = embedBuilder.build();

        Util.sendBufferedEmbed(message.getChannel(), embedObject);
    }

    private void command_Poll(final IMessage commandMessage) throws InterruptedException {
        final String messageContent = commandMessage.getContent();
        final IChannel channel = commandMessage.getChannel();
        final String context = Util.getContext(messageContent);

        if (context.matches(".+;.+;.+;[0-9]+") || context.matches(".+;.+;.+;.+;[0-9]+") ||
                context.matches(".+;.+;.+;.+;.+;[0-9]+") || context.matches(".+;.+;.+;.+;.+;.+;[0-9]+")) {

            final String[] pollArgs = context.split(";");

            // Argumente des Befehls auslesen
            final String pollQuestion = pollArgs[0];
            final String[] pollOptions = Arrays.copyOfRange(pollArgs, 1, pollArgs.length-1);
            final int pollOptionsCount = pollOptions.length;
            if (pollOptionsCount > 5) {
                Util.sendMessage(channel, "Maximal 5 Optionen!");
                return;
            }
            final int pollSeconds;
            try {
                pollSeconds = Integer.parseInt(pollArgs[pollArgs.length - 1]);
            }
            catch (NumberFormatException e) {
                Util.sendMessage(channel, "Ungültige Dauer angegeben! (Maximal 24 Stunden)");
                return;
            }

            if (pollSeconds > 86400) {
                Util.sendMessage(channel, "Ungültige Dauer angegeben! (Maximal 24 Stunden)");
                return;
            }

            // Poll Nachricht
            final EmbedBuilder pollBuilder = new EmbedBuilder();
            pollBuilder.appendField(":question: __" + pollArgs[0] + "__", makeOptionString(pollOptions), false);
            pollBuilder.withFooterText("Abstimmen mithilfe der Reactions! Dauer: " + makeTimeString(pollSeconds));
            final EmbedObject pollObject = pollBuilder.build();

            final IMessage pollMessage;
            try {
                pollMessage = channel.sendMessage(pollObject);
            } catch (RateLimitException e) {
                System.err.println("[ERR] Ratelimited!");
                return;
            } catch (MissingPermissionsException e) {
                System.err.println("[ERR] Missing Permissions");
                return;
            } catch (DiscordException e) {
                System.err.println("[ERR] " + e.getMessage());
                e.printStackTrace();
                return;
            }

            addPollReactions(pollMessage, pollOptionsCount);

            // warten
            Thread.sleep(pollSeconds*1000);

            final StringBuilder resultStringBuilder = new StringBuilder();
            try {
                resultStringBuilder.append("__" + pollArgs[1] + "__: **" +
                        (pollMessage.getReactionByName("\uD83C\uDDE6").getCount() - 1) + "** Stimmen" + '\n');
                resultStringBuilder.append("__" + pollArgs[2] + "__: **" +
                        (pollMessage.getReactionByName("\uD83C\uDDE7").getCount() - 1) + "** Stimmen" + '\n');
                if (pollOptionsCount >= 3) {
                    resultStringBuilder.append("__" + pollArgs[3] + "__: **" +
                            (pollMessage.getReactionByName("\uD83C\uDDE8").getCount() - 1) + "** Stimmen" + '\n');
                }
                if (pollOptionsCount >= 4) {
                    resultStringBuilder.append("__" + pollArgs[4] + "__: **" +
                            (pollMessage.getReactionByName("\uD83C\uDDE9").getCount() - 1) + "** Stimmen" + '\n');
                }
                if (pollOptionsCount >= 5) {
                    resultStringBuilder.append("__" + pollArgs[5] + "__: **" +
                            (pollMessage.getReactionByName("\uD83C\uDDEA").getCount() - 1) + "** Stimmen");
                }
            }
            catch (NullPointerException e) {
                resultStringBuilder.append("**Error:** Reaction nicht gefunden!");
                System.err.println("[ERR] Reaction not found!");
            }

            final EmbedBuilder resultBuilder = new EmbedBuilder();
            resultBuilder.appendField("__Ergebnis:__", resultStringBuilder.toString(), false);
            resultBuilder.withFooterText(pollArgs[0]);
            final EmbedObject resultObject = resultBuilder.build();

            Util.sendBufferedEmbed(channel, resultObject);

            try {
                pollMessage.removeAllReactions();
            }
            catch (NullPointerException e) {
                System.err.println("[ERR] Could not remove reactions! " + '\n' + e.getMessage());
                e.printStackTrace();
            }
        }
        else {
            Util.sendMessage(channel, "Syntax: `" + prefix + SYNTAX);
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

    private static String makeOptionString(final String[] pollOptions) {

        final StringBuilder optionStringBuilder = new StringBuilder();
        for (int i = 0; i < pollOptions.length; i++) {
            String letterEmoji = "-";
            switch (i) {
                case 0: letterEmoji = ":regional_indicator_a:"; break;
                case 1: letterEmoji = ":regional_indicator_b:"; break;
                case 2: letterEmoji = ":regional_indicator_c:"; break;
                case 3: letterEmoji = ":regional_indicator_d:"; break;
                case 4: letterEmoji = ":regional_indicator_e:"; break;
            }

            optionStringBuilder.append(letterEmoji);
            optionStringBuilder.append(" ");
            optionStringBuilder.append(pollOptions[i]);
            optionStringBuilder.append('\n');
        }

        return optionStringBuilder.toString();
    }

    private static void addPollReactions(final IMessage message, final int optionCount) throws InterruptedException {
            for (int i = 0; i < optionCount; i++) {
                final String emoji;

                switch (i) {
                    case 0: emoji = EMOJI_A;
                            break;
                    case 1: emoji = EMOJI_B;
                            break;
                    case 2: emoji = EMOJI_C;
                            break;
                    case 3: emoji = EMOJI_D;
                            break;
                    case 4: emoji = EMOJI_E;
                            break;
                    default: emoji = "";
                }

                RequestBuffer.request( () -> {
                    message.addReaction(emoji);
                } );
            }
    }

}
