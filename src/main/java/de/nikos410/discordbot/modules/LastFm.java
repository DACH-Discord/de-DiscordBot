package de.nikos410.discordbot.modules;

import de.nikos410.discordbot.exception.InitializationException;
import de.nikos410.discordbot.framework.PermissionLevel;
import de.nikos410.discordbot.framework.annotations.CommandModule;
import de.nikos410.discordbot.framework.annotations.CommandSubscriber;
import de.nikos410.discordbot.util.discord.DiscordIO;
import de.nikos410.discordbot.util.io.IOUtil;
import de.umass.lastfm.Caller;
import de.umass.lastfm.User;
import de.umass.lastfm.cache.FileSystemCache;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.handle.obj.IMessage;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

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

        this.apiKey = lastFmJSON.getString("apiKey");

        Caller.getInstance().setUserAgent("de-DiscordBot/1.0");
        Caller.getInstance().setCache(new FileSystemCache(CACHE_PATH.toFile()));
    }

    @CommandSubscriber(command = "lastfm", help = "Last.fm Modul", pmAllowed = false)
    public void command_lastfm(final IMessage message, final String argString) {
        final String[] args = argString.split(" ");

        switch (args[0]) {
            case "set":
                if (args[1] != null) {
                    User response = User.getInfo(args[1], apiKey);

                    if (response != null) {
                        try {
                            lastFmJSON.getJSONObject("users").put(message.getAuthor().getStringID(), args[1]);
                        } catch (JSONException ex) {
                            lastFmJSON.put("users", new JSONObject().put(message.getAuthor().getStringID(), args[1]));
                        }
                        saveJSON();
                    } else {
                        DiscordIO.sendMessage(message.getChannel(), ":x: Ungültigen Last.fm-Usernamen angegeben.");
                        return;
                    }
                } else {
                    DiscordIO.sendMessage(message.getChannel(), ":x: Keinen Last.fm-Usernamen angegeben.");
                    return;
                }
                break;
            case "recent":
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
                DiscordIO.sendMessage(message.getChannel(), ":x: Keine Parameter angegeben.");
                return;
        }
    }

    private void saveJSON() {
        LOG.debug("Saving Last.fm config file.");

        final String jsonOutput = lastFmJSON.toString(4);
        IOUtil.writeToFile(LASTFM_PATH, jsonOutput);
    }
}
