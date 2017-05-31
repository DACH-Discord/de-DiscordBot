package nikos.discordBot.modules;

import nikos.discordBot.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.user.PresenceUpdateEvent;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.EmbedBuilder;

import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Games {
    private static IDiscordClient client;

    private final static Path GAMESTATS_PATH = Paths.get("data/gameStats.json");
    private final static Path CONFIG_PATH = Paths.get("config/config.json");

    private final static String MODULE_NAME = "Spiele";
    private final static char SEPARATOR = '⠀';
    private final static String COMMANDS = "`playing <Spiel>" + SEPARATOR + "`  Zeigt alle Nutzer, die gerade <Spiel> spielen";

    private final String prefix;

    private JSONObject gameStatsJSON;

    public Games(IDiscordClient dClient) {
        client = dClient;

        // Prefix aus Config-Datei auslesen
        final String configFileContent = Util.readFile(CONFIG_PATH);

        final JSONObject json = new JSONObject(configFileContent);
        this.prefix = json.getString("prefix");

        // Spiel-Liste einlesen
        final String jsonContent = Util.readFile(GAMESTATS_PATH);
        this.gameStatsJSON = new JSONObject(jsonContent);
    }

    @EventSubscriber
    public void onStartUP(ReadyEvent event) {
        List<IUser> users = client.getUsers();

        for(IUser user : users) {
            this.updateUserStatus(user);
        }
    }

    @EventSubscriber
    public void onStatusChange(PresenceUpdateEvent event){
        updateUserStatus(event.getUser());
    }

    @EventSubscriber
    public void onMessageRecieved(MessageReceivedEvent event) throws InterruptedException {
        final IMessage message = event.getMessage();
        final String messageContent = message.getContent().toLowerCase();

        if (messageContent.startsWith(prefix + "help")) {
            this.command_Help(message);
        }
        else if (messageContent.startsWith(prefix + "playing")) {
            this.command_Playing(message);
        }
    }

    private synchronized void updateUserStatus(final IUser user) {
        if (user.isBot()) {
            return;
        }

        final IPresence presence = user.getPresence();
        final StatusType status = presence.getStatus();
        if (status.equals(StatusType.ONLINE)) {
            // User ist (jetzt) online
            final Optional<String> gameStatus = presence.getPlayingText();
            if (gameStatus.isPresent()) {
                // User spielt (jetzt) ein Spiel
                final String gameName = gameStatus.get().toLowerCase();
                final String userID = user.getID();

                if (!doesGameExist(gameName)) {
                    this.addGame(gameName);
                }
                if (!doesUserPlay(userID, gameName)) {
                    this.addUser(gameName, userID);
                }
            }
        }
    }

    private void addUser(final String gameName, final String userID) {
        JSONArray gameArray = gameStatsJSON.getJSONArray(gameName);
        gameArray.put(userID);
        saveJSON();
    }

    private void addGame(final String game) {
        JSONArray gameArray = new JSONArray();
        gameStatsJSON.put(game, gameArray);
        saveJSON();
    }

    private boolean doesGameExist(final String game) {
            return gameStatsJSON.has(game);
    }

    private boolean doesUserPlay(final String userID, final String game) {
        if (gameStatsJSON.has(game)) {
            // Spiel ist vorhanden

            JSONArray gameArray = gameStatsJSON.getJSONArray(game);
            for (int i = 0; i < gameArray.length(); i++) {
                if (gameArray.getString(i).equals(userID)) {
                    // User ist in Array Vorhanden
                    return true;
                }
            }
        }
        return false;
    }

    private void saveJSON() {
        final String jsonOutput = gameStatsJSON.toString(4);
        Util.writeToFile(GAMESTATS_PATH, jsonOutput);

        gameStatsJSON = new JSONObject(jsonOutput);
    }



    /**********
     * COMMANDS
     **********/

    private void command_Help(final IMessage message) throws InterruptedException {
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(new Color(114, 137, 218));
        embedBuilder.appendField(MODULE_NAME, COMMANDS, false);
        embedBuilder.withFooterText("+++NEU+++ Jetzt mit Fuzzy Matching!");

        final EmbedObject embedObject = embedBuilder.build();

        Util.sendEmbed(message.getChannel(), embedObject);
    }

    private void command_Playing(final IMessage message) {
        final String content = message.getContent();
        final String game = Util.getContext(content);

        if (game.isEmpty()) {  // Spiel angegeben
            Util.sendMessage(message.getChannel(), "Kein Spiel angegeben!");
            return;
        }

        IGuild guild = message.getGuild();
        final List<IUser> users = guild.getUsers();

        final int levDistTreshold = 2 + StringUtils.countMatches(game, " ");

        /*
         * Nutzer, die gerade das Spiel spielen
         */
        String usersPlayingNow = "";
        for (IUser user : users) {
            final Optional<String> playing = user.getPresence().getPlayingText();

            if (playing.isPresent() && StringUtils.getLevenshteinDistance( game.toLowerCase(),
                    playing.get().toLowerCase() ) < levDistTreshold)  {
                usersPlayingNow = usersPlayingNow + user.getName() + '#' + user.getDiscriminator() + '\n';
            }
        }

        /*
         * Nutzer, die jemals das Spiel gespielt haben
         */

        // Alle Spiele in der Liste, die zu dem Input passen
        List<String> gameKeys = new ArrayList<>(1);
        for (Object obj : gameStatsJSON.keySet()) {
            final String gameKey = obj.toString();
            if (StringUtils.getLevenshteinDistance(gameKey.toLowerCase(), game.toLowerCase()) < levDistTreshold) {
                gameKeys.add(gameKey.toLowerCase());
            }
        }

        String usersPlayingAny = "";
        for (String gameKey : gameKeys) {

            if (gameStatsJSON.has(gameKey)) {
                JSONArray gameArray = gameStatsJSON.getJSONArray(gameKey);

                for (int i = 0; i < gameArray.length(); i++) {
                    final IUser user = client.getUserByID(gameArray.getString(i));
                    if (user != null) {
                        final String userOutput = user.getName() + '#' + user.getDiscriminator();
                        if (!usersPlayingAny.contains(userOutput)) {    // Keine doppelten Namen
                            usersPlayingAny = usersPlayingAny + userOutput + '\n';
                        }
                    }
                }
            }

        }


        /*
         * Ausgabe
         */
        if (usersPlayingNow.isEmpty() && usersPlayingAny.isEmpty()) {
            // Keine Nutzer spielen gerade oder haben jemals gespielt
            Util.sendMessage(message.getChannel(), "Niemand auf diesem Server spielt **" + game + "**.");
            return;
        }

        if (usersPlayingNow.isEmpty()) {
            usersPlayingNow = "_niemand_";
        }

        Util.sendMessage(message.getChannel(), "**Nutzer, die __jetzt__ _" + game + "_ spielen**" + '\n' + usersPlayingNow);
        Util.sendMessage(message.getChannel(), "**__Alle__ Nutzer, die _" + game + "_ spielen**" + '\n' + usersPlayingAny);
    }
}
