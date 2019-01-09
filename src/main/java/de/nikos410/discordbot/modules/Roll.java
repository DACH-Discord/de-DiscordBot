package de.nikos410.discordbot.modules;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.stream.IntStream;

import de.nikos410.discordbot.framework.CommandModule;
import de.nikos410.discordbot.util.discord.DiscordIO;
import de.nikos410.discordbot.framework.annotations.CommandSubscriber;

import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

public class Roll extends CommandModule {
    private static final int DEFAULT_DOT_COUNT = 6;

    private static final SecureRandom rng;
    static {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(System.currentTimeMillis());
        rng = new SecureRandom(buffer.array());
    }

    @Override
    public String getDisplayName() {
        return "Würfel";
    }

    @Override
    public String getDescription() {
        return "Würfeln mit Einstellungsmöglichkeiten.";
    }

    @Override
    public boolean hasEvents() {
        return true;
    }

    @Override
    public void init() {}

    @CommandSubscriber(command = "roll",help = "Würfeln. Syntax: `roll AnzahlWuerfel;[AugenJeWuerfel=6]`")
    public void command_roll(final IMessage commandMessage, final String diceArgsInput) {
        final IChannel channel = commandMessage.getChannel();

        final EmbedBuilder outputBuilder = new EmbedBuilder();
        if (diceArgsInput.matches("^[0-9]+;?[0-9]*")) {
            try {
                final String[] args = diceArgsInput.split(";");
                final int diceCount = Integer.parseInt(args[0]);
                final int dotCount = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_DOT_COUNT;
                if (diceCount < 1 || dotCount < 1) {
                    throw new NumberFormatException("Würfelanzahl und maximale Augenzahl muss größer als 0 sein!");
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
                        MessageFormat.format("Würfelt {0} Würfel mit einer maximalen Augenzahl von {1}!", diceCount, dotCount),
                        resultBuilder.toString(),
                        false
                );
                EmbedObject rollObject = outputBuilder.build();
                DiscordIO.sendEmbed(channel, rollObject);
            }
            catch (NumberFormatException ex) {
                DiscordIO.sendMessage(channel, MessageFormat.format("Konnte Eingabe '{0}' nicht verarbeiten." +
                        "Bitte sicherstellen, dass sowohl die Würfelanzahl als auch die maximale Augenzahl Integer-Zahlen > 0 sind!", diceArgsInput));
            }
        }
        else {
            DiscordIO.sendMessage(channel, "Syntax: `roll AnzahlWürfel;[AugenJeWürfel=6]`");
        }
    }
}
