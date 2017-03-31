package nikos.discordBot.modules;

import nikos.discordBot.util.Util;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.util.Image;
import sx.blah.discord.util.RequestBuffer;

import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Timer;
import java.util.TimerTask;


public class AprilFools {
    private static IDiscordClient client;

    private IGuild aprilFoolsGuild;
    private boolean enabled = false;

    private Timer timer;

    public AprilFools (IDiscordClient dclient) {
        client = dclient;
    }

    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) {
        final IMessage message = event.getMessage();
        final String messageContent = message.getContent();

        if (messageContent.equalsIgnoreCase("%%aprilfools init")) {
            this.aprilFoolsGuild = message.getGuild();
            if (this.aprilFoolsGuild == null) {
                Util.sendMessage(message.getChannel(), "Error! Server nicht gefunden!");
            }
            else {
                System.out.println("Server: " + aprilFoolsGuild.getName());
                timer = new Timer();
                timer.scheduleAtFixedRate(new AprilFoolsTask(), 0, 10000);
            }
        }
    }

    private void aprilFools() {
        // name ändern
        this.aprilFoolsGuild.changeName("Alternative für Deutschland");

        // servericon ändern
        final Path imagePath = Paths.get("files/AfD_square.png");
        Image img = Image.forFile(imagePath.toFile());
        this.aprilFoolsGuild.changeIcon(img);

        // Stammgast ändern
        final IRole stammgast = aprilFoolsGuild.getRolesByName("Stammgast").get(0);
        if (stammgast == null) {
            System.err.println("[Error] Stammgast nicht gefunden");
        }
        else {
            stammgast.changeName("Petrypedes");
            stammgast.changeColor(new Color(0,158,224));
        }

        // text channels
        // ✓ offizielles
        this.renameChannel("295969587374063618", "afd-news");
        // ✓ allgemein
        this.renameChannel("217015995385118721", "afd-allgemein");
        // ✓ stammtisch
        this.renameChannel("227058629184978944", "schnapsfuechse");
        // ✓ politik
        this.renameChannel("274566587615543297", "afd");
        // ✓ spiele
        this.renameChannel("230069265925931008", "volkswettkampf");
        // ✓ eve
        this.renameChannel("285747368492531723", "eva");
        // ✓ techsupport
        this.renameChannel("232232534614736906", "volksreparatur");
        // ✓ film fernsehen
        this.renameChannel("250652441018630144", "volkstheater");
        // ✓ hobby
        this.renameChannel("294985677299253249", "volkshobel");
        // ✓ musik
        this.renameChannel("217231872114032641", "volksmusik");
        // ✓ kochen
        this.renameChannel("256362274044903424", "dierollederdeutschenfrau");
        // ✓ sport
        this.renameChannel("239806513420435457", "koerpertuechtigung");
        // ✓ geschichte
        this.renameChannel("288757127902920705", "alternativefakten");
        // ✓ weebs
        this.renameChannel("271371865337888768", "jugendvonheute");
        // ✓ the_schulz
        this.renameChannel("251817011468828673", "the_frauke");
        // ✓ kreiswichs
        this.renameChannel("257957957214535680", "heilmasturbation");
        // ✓ mett
        this.renameChannel("237631393532739585", "hackepeter");
        // userlog
        this.renameChannel("286160679789002752", "buerger-log");

        // voice channels
        // Laberecke
        this.renameVoiceChannel("228618653828907020", "AfD-Talk");
        // Musik
        this.renameVoiceChannel("245985388236242945", "Volksmusik");

        // Daddeln low
        this.renameVoiceChannel("217024894888706048", "Süddeutscher Wettkampf");
        // Daddeln mid
        this.renameVoiceChannel("217252788009304065", "Mitteldeutscher Wettkampf");
        // Daddeln hi
        this.renameVoiceChannel("217254444759384067", "Hochdeuter Wettkampf");

        // afk
        this.renameVoiceChannel("217016088154603520", "Alternative Fürs Kacken");

        // weebs löschen

        final IRole weebs = aprilFoolsGuild.getRolesByName("Weebs").get(0);
        if (weebs == null) {
            System.err.println("[Error] Weebs nicht gefunden");
        }
        else {
            weebs.delete();
        }
    }

    private synchronized void renameChannel(String channelID, String newName) {
        RequestBuffer.request(() -> {
            final IChannel channel = aprilFoolsGuild.getChannelByID(channelID);
            if (channel == null) {
                System.out.println("[ERROR] Channel with ID " + channelID + " not found!");
            }
            else {
                channel.changeName(newName.toLowerCase());
                channel.changeTopic("");
            }
        });
    }

    private void renameVoiceChannel(String channelID, String newName) {
        RequestBuffer.request(() -> {
            final IChannel channel = aprilFoolsGuild.getVoiceChannelByID(channelID);
            if (channel == null) {
                System.out.println("[ERROR] Channel with ID " + channelID + " not found!");
            }
            else {
                channel.changeName(newName);
            }
        });
    }

    private class AprilFoolsTask extends TimerTask {
        @Override
        public void run() {
            System.out.println("timer");
            if (LocalDateTime.now().getDayOfMonth() == 1) {
                System.out.println("APRIL FOOLS");
                aprilFools();
                timer.cancel();
                timer.purge();
            }
        }
    }

}
