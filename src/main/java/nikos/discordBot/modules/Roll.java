package nikos.discordBot.modules;

import nikos.discordBot.util.Util;
import org.json.JSONObject;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.stream.IntStream;

public class Roll {
    private final static String MODULE_NAME = "Roll";
    private final static String SEPARATOR = "⠀";
    private final static String COMMANDS = "`roll           " + SEPARATOR + "`  Würfeln";
    private final static String SYNTAX = "roll AnzahlWuerfel;[AugenJeWuerfel=6]";
    private final static int DEFAULT_DOT_COUNT = 6;
    private final static Path CONFIG_PATH = Paths.get("config/config.json");

    private final static SecureRandom rng;

    private final String prefix;

    static {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(System.currentTimeMillis());
        rng = new SecureRandom(buffer.array());
    }

    public Roll(IDiscordClient dClient) {
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

        if (messageContent.toLowerCase().startsWith(prefix + "roll")) {
            this.command_Roll(message);
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

        Util.sendEmbed(message.getChannel(), embedObject);
    }

    private void command_Roll(final IMessage commandMessage) throws InterruptedException {
        final String messageContent = commandMessage.getContent();
        final IChannel channel = commandMessage.getChannel();
        final String context = Util.getContext(messageContent);

        final EmbedBuilder outputBuilder = new EmbedBuilder();
        if (context.matches("^[0-9]+;?[0-9]*")) {
            try {
                final String[] args = context.split(";");
                final int diceCount = Integer.parseInt(args[0]);
                final int dotCount = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_DOT_COUNT;
                if (diceCount < 1 || dotCount < 1) {
                    throw new NumberFormatException("Dice count and dot count must be greater than 0!");
                }
                final StringBuilder resultBuilder = new StringBuilder();
                final int sum = IntStream.generate(() -> (rng.nextInt(dotCount) + 1))
                        .limit(diceCount)
                        .reduce(0, (int acc, int number) -> {
                            resultBuilder.append(number);
                            resultBuilder.append("\n");
                            return acc + number;
                        });
                resultBuilder.append(MessageFormat.format("Sum: {0}", sum));
                outputBuilder.appendField(
                        MessageFormat.format("Rolling {0} dice with a maximum of {1} dots!", diceCount, dotCount),
                        resultBuilder.toString(),
                        false
                );
                EmbedObject rollObject = outputBuilder.build();
                Util.sendEmbed(channel, rollObject);
            } catch (NumberFormatException ex) {
                Util.sendMessage(channel, MessageFormat.format("Could not parse input '{0}'. Make sure both dice and dot count are integer numbers > 0!", context));
            }
        } else {
            Util.sendMessage(channel, "Syntax: `" + prefix + SYNTAX + "`");
        }
    }
}
