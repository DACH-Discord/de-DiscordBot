package de.nikos410.discordBot.modules;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.discord.DiscordIO;
import de.nikos410.discordBot.util.discord.UserUtils;
import de.nikos410.discordBot.util.io.IOUtil;
import de.nikos410.discordBot.framework.annotations.CommandModule;
import de.nikos410.discordBot.framework.annotations.CommandSubscriber;

import org.apache.commons.lang3.StringUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.user.PresenceUpdateEvent;
import sx.blah.discord.handle.obj.*;

@CommandModule(moduleName = "Spiel-Informationen", commandOnly = false)
public class GameStats {
    private final DiscordBot bot;
    private final IDiscordClient client;

    private final static Path GAMESTATS_PATH = Paths.get("data/gameStats.json");
    private final JSONObject gameStatsJSON;

    private final static Logger LOG = LoggerFactory.getLogger(GameStats.class);

    public GameStats (final DiscordBot bot) {
        this.bot = bot;
        this.client = bot.client;

        // Spiel-Liste einlesen
        final String jsonContent = IOUtil.readFile(GAMESTATS_PATH);
        this.gameStatsJSON = new JSONObject(jsonContent);
        LOG.info(String.format("Loaded GameStats file for %s guilds.", gameStatsJSON.keySet().size()));

        // Darauf warten, dass der Client ready ist
        if (!bot.client.isReady()) {
            try {
                TimeUnit.SECONDS.sleep(1);
            }
            catch (InterruptedException e) {
                LOG.warn("Sleep was interrupted!", e);
            }
            finally {
                updateAllUsers();
            }
        }
    }

    private void updateAllUsers() {
        for(IUser user : bot.client.getUsers()) {
            this.updateUserStatus(user);
        }
        saveJSON();
        LOG.info("Gamestats Module ready.");
    }

    @CommandSubscriber(command = "playing", help = "Zeigt alle Nutzer die das angegebene Spiel spielen", pmAllowed = false)
    public void command_Playing(final IMessage message, final String game) {
        if (game.isEmpty()) {  // Kein Spiel angegeben
            DiscordIO.sendMessage(message.getChannel(), "Kein Spiel angegeben!");
            return;
        }

        final IGuild guild = message.getGuild();

        // Ähnliche Spiele
        StringBuilder similarBuilder = new StringBuilder();
        for (String s : findSimilarKeys(game, guild)) {
            similarBuilder.append(s);
            similarBuilder.append('\n');
        }
        String similarGames = similarBuilder.toString();

        /*
         * Nutzer, die gerade das Spiel spielen
         */
        List<IUser> usersPlayingNow = new ArrayList<>();
        for (IUser user : guild.getUsers()) {
            final Optional<String> playing = user.getPresence().getPlayingText();

            if (playing.isPresent() && playing.get().equalsIgnoreCase(game)) {
                usersPlayingNow.add(user);
            }
        }

        /*
         * Nutzer, die jemals das Spiel gespielt haben
         */
        List<IUser> usersPlayingAny = new ArrayList<>();

        if (gameStatsJSON.has(guild.getStringID())) {
            final JSONObject guildJSON = gameStatsJSON.getJSONObject(guild.getStringID());
            if (guildJSON.has(game.toLowerCase())) {
                final JSONArray gameArray = guildJSON.getJSONArray(game.toLowerCase());

                for (int i = 0; i < gameArray.length(); i++) {
                    final IUser user = guild.getUserByID(gameArray.getLong(i));
                    if (user != null) {

                        // Nutzer nur hinzufügen wenn er nicht schon in der anderen Liste enthalten ist
                        if (!usersPlayingNow.contains(user)) {
                            usersPlayingAny.add(user);
                        }
                    }
                }

            }
        }


        /*
         * Ausgabe
         */

        // Keine Nutzer spielen gerade oder haben jemals gespielt
        if (usersPlayingNow.isEmpty() && usersPlayingAny.isEmpty()) {

            DiscordIO.sendMessage(message.getChannel(), String.format("**Niemand auf diesem Server spielt _%s_**.", game));
            if (!similarGames.isEmpty()) {
                DiscordIO.sendMessage(message.getChannel(), String.format("**Meintest du...**\n%s", similarGames));
            }

            return;
        }

        // TODO: Nachricht in einzelnen Zeilen übergeben um saubereren Zeilenumbruch zu gewährleisten

        DiscordIO.sendMessage(message.getChannel(), String.format("**Nutzer, die __jetzt__ _%s_ spielen**\n%s",
                game, userListToString(usersPlayingNow, guild)));

        DiscordIO.sendMessage(message.getChannel(), String.format("**__Alle anderen__ Nutzer, die _%s_ spielen**\n%s",
                game, userListToString(usersPlayingAny, guild)));

        if (!similarGames.isEmpty()) {
            DiscordIO.sendMessage(message.getChannel(), String.format("**Ähnliche Spiele:** \n%s", similarGames));
        }
    }

    @SuppressWarnings("deprecation")
    private List<String> findSimilarKeys (final String inputKey, final IGuild guild) {
        final int levDistTreshold = 2 + StringUtils.countMatches(inputKey, " ");

        // Alle Spiele in der Liste, die zu dem Input passen
        List<String> similarKeys = new ArrayList<>();

        if (gameStatsJSON.has(guild.getStringID())) {
            JSONObject guildJSON = gameStatsJSON.getJSONObject(guild.getStringID());
            for (Object obj : guildJSON.keySet()) {
                final String gameKey = obj.toString();
                if (StringUtils.getLevenshteinDistance(gameKey.toLowerCase(), inputKey.toLowerCase()) <= levDistTreshold &&
                        !gameKey.equalsIgnoreCase(inputKey)) {
                    similarKeys.add(gameKey.toLowerCase());
                }
            }
        }

        return similarKeys;
    }

    private String userListToString (final List<IUser> userList, final IGuild guild) {
        StringBuilder stringBuilder = new StringBuilder();

        for (IUser user : userList) {
            stringBuilder.append(UserUtils.makeUserString(user, guild));
            stringBuilder.append('\n');
        }

        return stringBuilder.toString().isEmpty() ? "_niemand_" : stringBuilder.toString();
    }

    @EventSubscriber
    public void onStatusChange(PresenceUpdateEvent event){
        updateUserStatus(event.getUser());
        saveJSON();
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
                final long userID = user.getLongID();

                // Für alle Server eintragen auf denen der Nutzer Mitglied ist
                for (IGuild guild : client.getGuilds()) {
                    if (guild.getUsers().contains(user)) {
                        if (!hasGame(gameName, guild)) {
                            this.addGame(gameName, guild);
                        }
                        if (!doesUserPlay(userID, gameName, guild)) {
                            this.addUser(gameName, userID, guild);
                        }
                    }
                }
            }
        }
    }

    private void addUser(final String gameName, final long userID, final IGuild guild) {
        final JSONObject guildJSON;
        if (gameStatsJSON.has(guild.getStringID())) {
            guildJSON = gameStatsJSON.getJSONObject(guild.getStringID());
        }
        else {
            guildJSON = new JSONObject();
            gameStatsJSON.put(guild.getStringID(), guildJSON);
        }

        final JSONArray gameArray;
        if (guildJSON.has(gameName)) {
            gameArray = guildJSON.getJSONArray(gameName);
        }
        else {
            gameArray = new JSONArray();
        }
        gameArray.put(userID);
    }

    private void addGame(final String game, final IGuild guild) {
        final JSONObject guildJSON;
        if (gameStatsJSON.has(guild.getStringID())) {
            guildJSON = gameStatsJSON.getJSONObject(guild.getStringID());
        }
        else {
            guildJSON = new JSONObject();
            gameStatsJSON.put(guild.getStringID(), guildJSON);
        }

        final JSONArray gameArray = new JSONArray();
        guildJSON.put(game, gameArray);
    }

    private boolean hasGame(final String game, final IGuild guild) {
        if (gameStatsJSON.has(guild.getStringID())) {
            JSONObject guildJSON = gameStatsJSON.getJSONObject(guild.getStringID());
            return guildJSON.has(game);
        }
        else {
            return false;
        }
    }

    private boolean doesUserPlay(final long userID, final String game, final IGuild guild) {
        if (gameStatsJSON.has(guild.getStringID())) {
            JSONObject guildJSON = gameStatsJSON.getJSONObject(guild.getStringID());
            if (guildJSON.has(game)) {
                // Spiel ist vorhanden

                JSONArray gameArray = guildJSON.getJSONArray(game);
                for (int i = 0; i < gameArray.length(); i++) {
                    if (gameArray.getLong(i) == userID) {
                        // User ist in Array Vorhanden
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private synchronized void saveJSON() {
        LOG.debug("Saving GameStats file");

        final String jsonOutput = gameStatsJSON.toString(4);
        IOUtil.writeToFile(GAMESTATS_PATH, jsonOutput);
    }
}
