package de.nikos410.discordbot.modules;

import de.nikos410.discordbot.exception.InitializationException;
import de.nikos410.discordbot.framework.PermissionLevel;
import de.nikos410.discordbot.framework.annotations.CommandModule;
import de.nikos410.discordbot.framework.annotations.CommandSubscriber;
import de.nikos410.discordbot.util.discord.DiscordIO;
import de.nikos410.discordbot.util.io.IOUtil;
import de.umass.lastfm.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.util.EmbedBuilder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

@CommandModule(moduleName = "Last.fm", commandOnly = true)
public class LastFm {
    private static final Logger LOG = LoggerFactory.getLogger(LastFm.class);

    private static final Path FONT_PATH = Paths.get("data/lastFm/Poly-Regular.otf");
    private static final Path LASTFM_PATH = Paths.get("data/lastFm/lastFm.json");
    private static final Path TEMP_IMG_PATH = Paths.get("data/lastFm/chart.png");

    private final JSONObject lastFmJSON;

    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private String apiKey;

    public LastFm() {
        final String jsonContent = IOUtil.readFile(LASTFM_PATH);
        if (jsonContent == null) {
            throw new InitializationException("Could not read module data.", LastFm.class);
        }
        this.lastFmJSON = new JSONObject(jsonContent);
        LOG.info("Loaded Last.fm config file.");

        try {
            this.apiKey = lastFmJSON.getString("apiKey");
        } catch (JSONException ex) {
            this.apiKey = "";
            LOG.info("Kein Last.fm API-Key gefunden.");
        }

        Caller.getInstance().setUserAgent("de-DiscordBot/1.0");
    }

    @CommandSubscriber(command = "lastFmSetApiKey", help = "Last.fm API-Key setzen - nur für Admins", pmAllowed = false, permissionLevel = PermissionLevel.ADMIN)
    public void command_lastFmSetApiKey(final IMessage message, final String key) {
        lastFmJSON.put("apiKey", key);
        saveJSON();

        this.apiKey = key;

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Last.fm API-Key gesetzt.");
    }

    @CommandSubscriber(command = "lastfm", help = "Last.fm Modul", pmAllowed = false)
    public void command_lastfm(final IMessage message, final String argString) {
        if (!apiKey.equals("")) {
            final String[] args = argString.trim().split(" ");

            switch (args[0]) {
                case "set":
                    try {
                        setUsername(message, args[1]);
                    } catch (ArrayIndexOutOfBoundsException ex) {
                        DiscordIO.sendMessage(message.getChannel(), ":x: Keinen Last.fm-Usernamen angegeben.");
                    }
                    break;
                case "now":
                    getNowPlaying(message);
                    break;
                case "recent":
                    getRecentTracks(message, 10);
                    break;
                case "topartists":
                    getTopArtists(message, 10);
                    break;
                case "topalbums":
                    getTopAlbums(message, 10);
                    break;
                case "toptracks":
                    getTopTracks(message, 10);
                    break;
                case "weeklyartists":
                    getWeeklyArtists(message, 10);
                    break;
                case "weeklyalbums":
                    getWeeklyAlbums(message, 10);
                    break;
                case "weeklytracks":
                    getWeeklyTracks(message, 10);
                    break;
                case "collage":
                    break;
                case "help":
                    break;
                default:
                    DiscordIO.sendMessage(message.getChannel(), ":x: Keine gültigen Parameter angegeben.");
            }
        } else {
            DiscordIO.sendMessage(message.getChannel(), ":x: Kein Last.fm API-Key vorhanden.");
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
        embedBuilder.withAuthorName(String.format("Aktuell gespielter Track von %s", message.getAuthor().getDisplayName(message.getGuild())));

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

    private void getRecentTracks(final IMessage message, final int limit) {
        String username = lastFmJSON.getJSONObject("users").getString(message.getAuthor().getStringID());

        Collection<Track> response = User.getRecentTracks(username, apiKey).getPageResults();

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(185, 0, 0);
        embedBuilder.withAuthorIcon(message.getAuthor().getAvatarURL());
        embedBuilder.withAuthorName(String.format("Kürzlich gespielte Tracks von %s", message.getAuthor().getDisplayName(message.getGuild())));

        int i = 1;

        StringBuilder chart = new StringBuilder();

        for (Track track : response) {
            if (i == limit + 1)
                break;

            chart.append(String.format("`%s` **%s** - *%s*\n", i, track.getArtist(), track.getName()));
            i++;
        }

        embedBuilder.withDesc(chart.toString());

        DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    private void getTopArtists(final IMessage message, final int limit) {
        String username = lastFmJSON.getJSONObject("users").getString(message.getAuthor().getStringID());

        Collection<Artist> response = User.getTopArtists(username, apiKey);

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(185, 0, 0);
        embedBuilder.withAuthorIcon(message.getAuthor().getAvatarURL());
        embedBuilder.withAuthorName(String.format("Top-Künstler von %s", message.getAuthor().getDisplayName(message.getGuild())));

        int i = 1;

        StringBuilder chart = new StringBuilder();

        for (Artist artist : response) {
            if (i == limit + 1)
                break;

            chart.append(String.format("`%s` **%s** (%s mal gespielt)\n", i, artist.getName(), artist.getPlaycount()));
            i++;
        }

        embedBuilder.withDesc(chart.toString());

        DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    private void getTopAlbums(final IMessage message, final int limit) {
        String username = lastFmJSON.getJSONObject("users").getString(message.getAuthor().getStringID());

        Collection<Album> response = User.getTopAlbums(username, apiKey);

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(185, 0, 0);
        embedBuilder.withAuthorIcon(message.getAuthor().getAvatarURL());
        embedBuilder.withAuthorName(String.format("Top-Alben von %s", message.getAuthor().getDisplayName(message.getGuild())));

        int i = 1;

        StringBuilder chart = new StringBuilder();

        for (Album album : response) {
            if (i == limit + 1)
                break;

            chart.append(String.format("`%s` **%s** - *%s* (%s mal gespielt)\n", i, album.getArtist(), album.getName(), album.getPlaycount()));
            i++;
        }

        embedBuilder.withDesc(chart.toString());

        DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    private void getTopTracks(final IMessage message, final int limit) {
        String username = lastFmJSON.getJSONObject("users").getString(message.getAuthor().getStringID());

        Collection<Track> response = User.getTopTracks(username, apiKey);

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(185, 0, 0);
        embedBuilder.withAuthorIcon(message.getAuthor().getAvatarURL());
        embedBuilder.withAuthorName(String.format("Top-Titel von %s", message.getAuthor().getDisplayName(message.getGuild())));

        int i = 1;

        StringBuilder chart = new StringBuilder();

        for (Track track : response) {
            if (i == limit + 1)
                break;

            chart.append(String.format("`%s` **%s** - *%s* (%s mal gespielt)\n", i, track.getArtist(), track.getName(), track.getPlaycount()));
            i++;
        }

        embedBuilder.withDesc(chart.toString());

        DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    private void getWeeklyArtists(final IMessage message, final int limit) {
        String username = lastFmJSON.getJSONObject("users").getString(message.getAuthor().getStringID());

        Collection<Artist> response = User.getTopArtists(username, Period.WEEK, apiKey);

        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(7);

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(185, 0, 0);
        embedBuilder.withAuthorIcon(message.getAuthor().getAvatarURL());
        embedBuilder.withAuthorName(String.format("Top-Künstler von %s\n(Woche vom %s zum %s)", message.getAuthor().getDisplayName(message.getGuild()), fromDate.format(dateFormat), toDate.format(dateFormat)));

        int i = 1;

        StringBuilder chart = new StringBuilder();

        for (Artist artist : response) {
            if (i == limit + 1)
                break;

            chart.append(String.format("`%s` **%s** (%s mal gespielt)\n", i, artist.getName(), artist.getPlaycount()));
            i++;
        }

        embedBuilder.withDesc(chart.toString());

        DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    private void getWeeklyAlbums(final IMessage message, final int limit) {
        String username = lastFmJSON.getJSONObject("users").getString(message.getAuthor().getStringID());

        Collection<Album> response = User.getTopAlbums(username, Period.WEEK, apiKey);

        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(7);

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(185, 0, 0);
        embedBuilder.withAuthorIcon(message.getAuthor().getAvatarURL());
        embedBuilder.withAuthorName(String.format("Top-Alben von %s\n(Woche vom %s zum %s)", message.getAuthor().getDisplayName(message.getGuild()), fromDate.format(dateFormat), toDate.format(dateFormat)));

        int i = 1;

        StringBuilder chart = new StringBuilder();

        for (Album album : response) {
            if (i == limit + 1)
                break;

            chart.append(String.format("`%s` **%s** - *%s* (%s mal gespielt)\n", i, album.getArtist(), album.getName(), album.getPlaycount()));
            i++;
        }

        embedBuilder.withDesc(chart.toString());

        DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    private void getWeeklyTracks(final IMessage message, final int limit) {
        String username = lastFmJSON.getJSONObject("users").getString(message.getAuthor().getStringID());

        Collection<Track> response = User.getTopTracks(username, Period.WEEK, apiKey);

        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(7);

        EmbedBuilder embedBuilder = new EmbedBuilder();

        embedBuilder.withColor(185, 0, 0);
        embedBuilder.withAuthorIcon(message.getAuthor().getAvatarURL());
        embedBuilder.withAuthorName(String.format("Top-Titel von %s\n(Woche vom %s zum %s)", message.getAuthor().getDisplayName(message.getGuild()), fromDate.format(dateFormat), toDate.format(dateFormat)));

        int i = 1;

        StringBuilder chart = new StringBuilder();

        for (Track track : response) {
            if (i == limit + 1)
                break;

            chart.append(String.format("`%s` **%s** - *%s* (%s mal gespielt)\n", i, track.getArtist(), track.getName(), track.getPlaycount()));
            i++;
        }

        embedBuilder.withDesc(chart.toString());

        DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
    }

    private void generateCollage(final IMessage message, final String target, final String dimensions) {

    }
    private void saveJSON() {
        LOG.info("Saving Last.fm config file.");

        final String jsonOutput = lastFmJSON.toString(4);
        IOUtil.writeToFile(LASTFM_PATH, jsonOutput);
    }
}
