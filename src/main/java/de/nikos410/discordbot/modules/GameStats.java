package de.nikos410.discordbot.modules;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import de.nikos410.discordbot.DiscordBot;
import de.nikos410.discordbot.exception.InitializationException;
import de.nikos410.discordbot.framework.CommandModule;
import de.nikos410.discordbot.util.discord.DiscordIO;
import de.nikos410.discordbot.util.discord.UserUtils;
import de.nikos410.discordbot.util.io.IOUtil;
import de.nikos410.discordbot.framework.annotations.CommandSubscriber;

import org.apache.commons.text.similarity.FuzzyScore;

import org.json.JSONArray;
import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.user.PresenceUpdateEvent;
import sx.blah.discord.handle.obj.*;

public class GameStats extends CommandModule {
    private static final Logger LOG = LoggerFactory.getLogger(GameStats.class);

    private static final Path GAMESTATS_PATH = Paths.get("data/gameStats.json");
    private JSONObject gameStatsJSON;

    @Override
    public String getDisplayName() {
        return "Game Stats";
    }

    @Override
    public String getDescription() {
        return "Hilft dabei, Mitspieler zu finden";
    }

    @Override
    public boolean hasEvents() {
        return true;
    }

    @Override
    public void init() {
        final String jsonContent = IOUtil.readFile(GAMESTATS_PATH);
        if (jsonContent == null) {
            throw new InitializationException("Could not read module data.", GameStats.class);
        }
        this.gameStatsJSON = new JSONObject(jsonContent);
        LOG.info("Loaded GameStats file for {} guilds.", gameStatsJSON.keySet().size());
    }

    @Override
    public void initWhenReady() {
        updateAllUsers();
        LOG.info("Gamestats Module ready.");
    }

    /**
     * Update the status of every user known to the bot.
     */
    private void updateAllUsers() {
        for(IUser user : this.bot.getClient().getUsers()) {
            this.updateUserStatus(user);
        }
        saveJSON();
    }

    @CommandSubscriber(command = "playing", help = "Zeigt alle Nutzer die das angegebene Spiel spielen", pmAllowed = false)
    public void command_playing(final IMessage message, final String game) {
        final IGuild guild = message.getGuild();

        // Similar games
        final List<String> similarGames = findSimilarKeys(game, guild);

        // Users who play the game right now
        List<IUser> playingNowUsers = new ArrayList<>();
        for (IUser user : guild.getUsers()) {
            final String currentUserGame = getCurrentGame(user);

            if (game.equalsIgnoreCase(currentUserGame)) {
                playingNowUsers.add(user);
            }
        }

        // Users who played the game at any point
        List<IUser> playingAnyUsers = new ArrayList<>();

        if (gameStatsJSON.has(guild.getStringID())) {
            final JSONObject guildJSON = gameStatsJSON.getJSONObject(guild.getStringID());
            if (guildJSON.has(game.toLowerCase())) {
                final JSONArray gameArray = guildJSON.getJSONArray(game.toLowerCase());

                for (int i = 0; i < gameArray.length(); i++) {
                    final IUser user = guild.getUserByID(gameArray.getLong(i));

                    // Only add if user isn't playing right now
                    if (user != null && !playingNowUsers.contains(user)) {
                        playingAnyUsers.add(user);
                    }
                }

            }
        }

        sendPlayingResponse(message, game ,playingNowUsers, playingAnyUsers, similarGames);
    }

    private void sendPlayingResponse(final IMessage message, final String game, final List<IUser> playingNowUsers,
                                     final List<IUser> playingAnyUsers, final List<String> similarGames) {
        final IGuild guild = message.getGuild();

        if (playingNowUsers.isEmpty() && playingAnyUsers.isEmpty()) {
            // Nobody has ever played the specified game
            final List<String> responseLines = new ArrayList<>();
            responseLines.add(String.format("Niemand auf diesem Server spielt **_%s_**.", game));
            if (!similarGames.isEmpty()) {
                responseLines.add("**Meintest du...**");
                responseLines.addAll(similarGames);
            }

            DiscordIO.sendMessage(message.getChannel(), responseLines);
            return;
        }

        // The lines that will be sent
        final List<String> responseLines = new ArrayList<>();

        // Add users that play the game right now
        responseLines.add(String.format("**Nutzer, die __jetzt__ _%s_ spielen**", game));
        if (playingNowUsers.isEmpty()) {
            responseLines.add("_Niemand_");
        }
        else {
            playingNowUsers.forEach(e -> responseLines.add(UserUtils.makeUserString(e, guild)));
        }

        // Add users that have played the game in the past
        responseLines.add(String.format("**__Alle anderen__ Nutzer, die _%s_ spielen**", game));
        if (playingAnyUsers.isEmpty()) {
            responseLines.add("_Niemand_");
        }
        else {
            playingAnyUsers.forEach(e -> responseLines.add(UserUtils.makeUserString(e, guild)));
        }

        if (!similarGames.isEmpty()) {
            responseLines.add("**Ähnliche Spiele:**");
            responseLines.addAll(similarGames);
        }

        DiscordIO.sendMessage(message.getChannel(), responseLines);
    }

    /**
     * Find games that have names similar to the given one, sorted from the highest similarity to the lowest.
     *
     * @param inputKey The given game name.
     * @param guild The guild on which to look for similar games.
     * @return A list containing a maximum of 5 similar keys.
     */
    private List<String> findSimilarKeys (final String inputKey, final IGuild guild) {
        final String inputKeyLowerCase = inputKey.toLowerCase();

        // Calculate the fuzzy score for all games
        final FuzzyScore scoreCalculator = new FuzzyScore(Locale.GERMANY);
        final List<GameFuzzyScore> scores = new ArrayList<>();

        final JSONObject guildJSON = gameStatsJSON.getJSONObject(guild.getStringID());
        if (guildJSON == null) {
            return new ArrayList<>();
        }

        for (Object obj : guildJSON.keySet()) {
            final String gameName = obj.toString();

            if (!gameName.equals(inputKeyLowerCase)) {
                final int result = scoreCalculator.fuzzyScore(inputKeyLowerCase, gameName);

                if (result > 15) {
                    final GameFuzzyScore score = new GameFuzzyScore(gameName, result);
                    scores.add(score);
                }
            }
        }

        // Return the names of the 5 games with the highest score
        return scores.stream()
                .sorted()
                .limit(5)
                .map(GameFuzzyScore::getName)
                .collect(Collectors.toList());
    }

    private static class GameFuzzyScore implements Comparable {
        private final String name;
        private final int score;

        GameFuzzyScore(final String name, final int score) {
            this.name = name;
            this.score = score;
        }

        public int getScore() {
            return score;
        }

        public String getName() {
            return name;
        }

        @Override
        public int compareTo(Object other) {
            final GameFuzzyScore otherScore = (GameFuzzyScore)other;
            return Integer.compare(otherScore.getScore(), score);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof GameFuzzyScore)) {
                return false;
            }

            final GameFuzzyScore otherScore = (GameFuzzyScore)other;
            return otherScore.getScore() == score;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, score);
        }
    }

    @EventSubscriber
    public void onStatusChange(PresenceUpdateEvent event){
        updateUserStatus(event.getUser());
        saveJSON();
    }

    /**
     * Fetch the current status of a user and update the games list if a new game was detected.
     *
     * @param user The user whose status to update.
     */
    private synchronized void updateUserStatus(final IUser user) {
        // Do not process a bot
        if (user.isBot()) {
            return;
        }

        String gameName = getCurrentGame(user);
        if (gameName == null || gameName.isEmpty()) {
            return;
        }

        gameName = gameName.toLowerCase();

        // User is now playing a game
        final long userID = user.getLongID();

        // Add game info for all guilds this user is on
        for (IGuild guild : this.bot.getClient().getGuilds()) {
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

    private String getCurrentGame(final IUser user) {
        final IPresence presence = user.getPresence();
        final Optional<ActivityType> activity = presence.getActivity();

        if (!activity.isPresent() || !ActivityType.PLAYING.equals(activity.get())) {
            return null;
        }

        return presence.getText().orElse(null);
    }

    /**
     * Add an empty entry for a game to a guild's JSON object. Will not check if entry already exists.
     *
     * @param game The game for which to add the entry.
     * @param guild The guild for which to create the entry.
     */
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

    /**
     * Check whether a game has been played by anyone on a guild.
     *
     * @param game The name of the game.
     * @param guild The guild for which to check.
     * @return True is anyone on the guild has played the game.
     */
    private boolean hasGame(final String game, final IGuild guild) {
        if (gameStatsJSON.has(guild.getStringID())) {
            JSONObject guildJSON = gameStatsJSON.getJSONObject(guild.getStringID());
            return guildJSON.has(game);
        }
        else {
            return false;
        }
    }

    /**
     * Check whether a user has ever played a game
     *
     * @param userID The ID of the user.
     * @param game The name of the game.
     * @param guild The guild the user is on.
     * @return True if the user has ever played the game.
     */
    private boolean doesUserPlay(final long userID, final String game, final IGuild guild) {
        if (gameStatsJSON.has(guild.getStringID())) {
            JSONObject guildJSON = gameStatsJSON.getJSONObject(guild.getStringID());
            if (guildJSON.has(game)) {
                // Game has been played by at least one user of this server

                JSONArray gameArray = guildJSON.getJSONArray(game);
                for (int i = 0; i < gameArray.length(); i++) {
                    if (gameArray.getLong(i) == userID) {
                        // User has played the game
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
