package de.nikos410.discordbot.modules;

import de.nikos410.discordbot.exception.InitializationException;
import de.nikos410.discordbot.framework.PermissionLevel;
import de.nikos410.discordbot.framework.annotations.CommandModule;
import de.nikos410.discordbot.framework.annotations.CommandSubscriber;
import de.nikos410.discordbot.util.discord.DiscordIO;
import de.nikos410.discordbot.util.io.IOUtil;
import de.umass.lastfm.*;
import de.umass.lastfm.cache.FileSystemCache;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

@CommandModule(moduleName = "Last.fm", commandOnly = true)
public class LastFm {
    private static final Logger LOG = LoggerFactory.getLogger(LastFm.class);

    private static final Path FONT_PATH = Paths.get("data/Poly-Regular.otf");
    private static final Path LASTFM_PATH = Paths.get("data/lastFm.json");
    private static final Path CACHE_PATH = Paths.get("data/.last-fm-cache");
    private final JSONObject lastFmJSON;

    private final String apiKey;

    public LastFm () {
        final String jsonContent = IOUtil.readFile(LASTFM_PATH);
        if (jsonContent == null) {
            throw new InitializationException("Could not read module data.", LastFm.class);
        }
        this.lastFmJSON = new JSONObject(jsonContent);
        LOG.info("Loaded Last.fm config file.");

        try {
            this.apiKey = lastFmJSON.getString("apiKey");
        } catch (JSONException ex) {
            lastFmJSON.put("apiKey", "");
            saveJSON();
            LOG.info("Bitte Last.fm API Key in data/lastFm.json eintragen.");
            throw ex;
        }

        Caller.getInstance().setUserAgent("de-DiscordBot/1.0");
        Caller.getInstance().setCache(new FileSystemCache(CACHE_PATH.toFile()));
    }

    @CommandSubscriber(command = "lastfm", help = "Last.fm Modul", pmAllowed = false)
    public void command_lastfm(final IMessage message, final String argString) {
        final String[] args = argString.trim().split(" ");

        switch (args[0]) {
            case "set":
                if (args[1] != null) {
                    setUsername(message, args[1]);
                } else {
                    DiscordIO.sendMessage(message.getChannel(), ":x: Keinen Last.fm-Usernamen angegeben.");
                    return;
                }
                break;
            case "now":
                getNowPlaying(message);
                break;
            case "recent":
                getRecentTracks(message);
                break;
            case "topartists":
                break;
            case "topalbums":
                break;
            case "toptracks":
                break;
            case "weeklyartists":
                break;
            case "weeklyalbums":
                break;
            case "weeklytracks":
                break;
            case "collage":
                break;
            case "help":
                break;
            default:
                DiscordIO.sendMessage(message.getChannel(), ":x: Keine gültigen Parameter angegeben.");
        }
    }

    private void setUsername(final IMessage message, final String username) {
        User response = User.getInfo(username, apiKey);

        if (response != null) {
            try {
                lastFmJSON.getJSONObject("users").put(message.getAuthor().getStringID(), username);
            } catch (JSONException ex) {
                lastFmJSON.put("users", new JSONObject().put(message.getAuthor().getStringID(), username));
            }
            saveJSON();

            DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Last.fm-Username gesetzt.");
        } else {
            DiscordIO.sendMessage(message.getChannel(), ":x: Ungültigen Last.fm-Usernamen angegeben oder fehlerhafter API-Key.");
        }
    }

    private void getNowPlaying(final IMessage message) {
        String username = lastFmJSON.getJSONObject("users").getString(message.getAuthor().getStringID());

        Collection<Track> response = User.getRecentTracks(username, apiKey).getPageResults();

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(185, 0, 0);
        embedBuilder.withAuthorIcon(message.getAuthor().getAvatarURL());
        embedBuilder.withAuthorName(String.format("Aktuell gespielter Track von %s", message.getAuthor().getNicknameForGuild(message.getGuild())));

        int i = 1;

        for (Track track : response) {
            if (i == 2)
                break;

            embedBuilder.appendField("Künstler", track.getArtist(), true);
            embedBuilder.appendField("Titel", track.getName(), true);
            embedBuilder.appendField("Album", track.getAlbum(), false);
            embedBuilder.withThumbnail(track.getImageURL(ImageSize.LARGE));
            i++;
        }

        DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    private void getRecentTracks(final IMessage message) {
        String username = lastFmJSON.getJSONObject("users").getString(message.getAuthor().getStringID());

        Collection<Track> response = User.getRecentTracks(username, apiKey).getPageResults();

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(185, 0, 0);
        embedBuilder.withAuthorIcon(message.getAuthor().getAvatarURL());
        embedBuilder.withAuthorName(String.format("Kürzlich gespielte Tracks von %s", message.getAuthor().getNicknameForGuild(message.getGuild())));

        int i = 1;

        for (Track track : response) {
            if (i == 11)
                break;

            embedBuilder.appendField("", String.format("`%d` **%s** - %s", i, track.getArtist(), track.getName()), false);
            i++;
        }

        DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    private void saveJSON() {
        LOG.debug("Saving Last.fm config file.");

        final String jsonOutput = lastFmJSON.toString(4);
        IOUtil.writeToFile(LASTFM_PATH, jsonOutput);
    }
}
