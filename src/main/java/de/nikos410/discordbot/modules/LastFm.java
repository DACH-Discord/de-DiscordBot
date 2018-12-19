package de.nikos410.discordbot.modules;

import de.nikos410.discordbot.DiscordBot;
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
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;

@CommandModule(moduleName = "Last.fm", commandOnly = true)
public class LastFm {
    private static final Logger LOG = LoggerFactory.getLogger(LastFm.class);

    private static final Path ERROR_IMG_PATH = Paths.get("data/lastFm/error-icon.png");
    private static final Path LASTFM_PATH = Paths.get("data/lastFm/lastFm.json");
    private static final Path TEMP_IMG_PATH = Paths.get("data/lastFm/chart.png");

    private final DiscordBot bot;

    private final JSONObject lastFmJSON;

    private final ArrayList<Integer[]> coords3x3 = new ArrayList<>();
    private final ArrayList<Integer[]> coords4x4 = new ArrayList<>();
    private final ArrayList<Integer[]> coords5x5 = new ArrayList<>();

    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private String apiKey;

    public LastFm(final DiscordBot bot) {
        this.bot = bot;

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

        // Coordinate initialization for collage generation
        coords3x3.add(new Integer[]{100, 400, 1801, 445});
        coords3x3.add(new Integer[]{667, 400, 1801, 553});
        coords3x3.add(new Integer[]{1234, 400, 1801, 661});
        coords3x3.add(new Integer[]{100, 967, 1801, 1012});
        coords3x3.add(new Integer[]{667, 967, 1801, 1120});
        coords3x3.add(new Integer[]{1234, 967, 1801, 1228});
        coords3x3.add(new Integer[]{100, 1534, 1801, 1579});
        coords3x3.add(new Integer[]{667, 1534, 1801, 1687});
        coords3x3.add(new Integer[]{1234, 1534, 1801, 1795});

        coords4x4.add(new Integer[]{100, 400, 1801, 445});
        coords4x4.add(new Integer[]{525, 400, 1801, 523});
        coords4x4.add(new Integer[]{950, 400, 1801, 601});
        coords4x4.add(new Integer[]{1375, 400, 1801, 679});
        coords4x4.add(new Integer[]{100, 825, 1801, 870});
        coords4x4.add(new Integer[]{525, 825, 1801, 948});
        coords4x4.add(new Integer[]{950, 825, 1801, 1026});
        coords4x4.add(new Integer[]{1375, 825, 1801, 1104});
        coords4x4.add(new Integer[]{100, 1250, 1801, 1295});
        coords4x4.add(new Integer[]{525, 1250, 1801, 1373});
        coords4x4.add(new Integer[]{950, 1250, 1801, 1451});
        coords4x4.add(new Integer[]{1375, 1250, 1801, 1529});
        coords4x4.add(new Integer[]{100, 1675, 1801, 1720});
        coords4x4.add(new Integer[]{525, 1675, 1801, 1798});
        coords4x4.add(new Integer[]{950, 1675, 1801, 1874});
        coords4x4.add(new Integer[]{1375, 1675, 1801, 1954});

        coords5x5.add(new Integer[]{100, 400, 1801, 445});
        coords5x5.add(new Integer[]{440, 400, 1801, 503});
        coords5x5.add(new Integer[]{780, 400, 1801, 561});
        coords5x5.add(new Integer[]{1120, 400, 1801, 619});
        coords5x5.add(new Integer[]{1460, 400, 1801, 678});
        coords5x5.add(new Integer[]{100, 740, 1801, 785});
        coords5x5.add(new Integer[]{440, 740, 1801, 843});
        coords5x5.add(new Integer[]{780, 740, 1801, 901});
        coords5x5.add(new Integer[]{1120, 740, 1801, 959});
        coords5x5.add(new Integer[]{1460, 740, 1801, 1017});
        coords5x5.add(new Integer[]{100, 1080, 1801, 1125});
        coords5x5.add(new Integer[]{440, 1080, 1801, 1183});
        coords5x5.add(new Integer[]{780, 1080, 1801, 1241});
        coords5x5.add(new Integer[]{1120, 1080, 1801, 1299});
        coords5x5.add(new Integer[]{1460, 1080, 1801, 1357});
        coords5x5.add(new Integer[]{100, 1420, 1801, 1465});
        coords5x5.add(new Integer[]{440, 1420, 1801, 1523});
        coords5x5.add(new Integer[]{780, 1420, 1801, 1581});
        coords5x5.add(new Integer[]{1120, 1420, 1801, 1639});
        coords5x5.add(new Integer[]{1460, 1420, 1801, 1697});
        coords5x5.add(new Integer[]{100, 1760, 1801, 1805});
        coords5x5.add(new Integer[]{440, 1760, 1801, 1863});
        coords5x5.add(new Integer[]{780, 1760, 1801, 1921});
        coords5x5.add(new Integer[]{1120, 1760, 1801, 1979});
        coords5x5.add(new Integer[]{1460, 1760, 1801, 2037});
    }

    @CommandSubscriber(command = "lastFmSetApiKey", help = "Last.fm API-Key setzen - nur für Admins", pmAllowed = false, permissionLevel = PermissionLevel.ADMIN)
    public void command_lastFmSetApiKey(final IMessage message, final String key) {
        lastFmJSON.put("apiKey", key);
        saveJSON();

        this.apiKey = key;

        DiscordIO.sendMessage(message.getChannel(), ":white_check_mark: Last.fm API-Key gesetzt.");
    }

    @CommandSubscriber(command = "lastfm", help = "Last.fm Modul - Parameter 'help' für Hilfe", pmAllowed = false)
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
                case "recenttracks":
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
                    try {
                        sendCollage(message, args[1], args[2]);
                    } catch (ArrayIndexOutOfBoundsException ex) {
                        DiscordIO.sendMessage(message.getChannel(), ":x: Falsche Parameter angegeben.\n\nSyntax:\n[p]lastfm collage <albums|artists> <3x3|4x4|5x5>");
                    }
                    break;
                case "help":
                    String msg = "```Last.fm Modul - Hilfe\n\n"
                    + String.format("'%slastfm set <last.fm username>' um deinen Last.fm-Nutzernamen zu setzen.\n\n", bot.configJSON.getString("prefix"))
                    + "Verfügbare Parameter:\n\n"
                    + "now\n"
                    + "recenttracks\n"
                    + "topartists\n"
                    + "topalbums\n"
                    + "toptracks\n"
                    + "weeklyartists\n"
                    + "weeklyalbums\n"
                    + "weeklytracks\n"
                    + "collage <artists|albums> <3x3|4x4|5x5>"
                    + "```";

                    DiscordIO.sendMessage(message.getChannel(), msg);
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

    private void sendCollage(final IMessage message, final String target, final String dimensions) {
        String username = lastFmJSON.getJSONObject("users").getString(message.getAuthor().getStringID());

        switch (target) {
            case "albums":
                Collection<Album> responseAlbum = User.getTopAlbums(username, Period.WEEK, apiKey);
                generateAndSendAlbumCollage(message, dimensions, responseAlbum);
                break;
            case "artists":
                Collection<Artist> responseArtist = User.getTopArtists(username, Period.WEEK, apiKey);
                generateAndSendArtistCollage(message, dimensions, responseArtist);
                break;
            default:
                DiscordIO.sendMessage(message.getChannel(), ":x: Falsche Parameter angegeben.\n\nSyntax:\n[p]lastfm collage <albums|artists> <3x3|4x4|5x5>");
                break;
        }
    }

    private void generateAndSendAlbumCollage(final IMessage message, final String dimensions, final Collection<Album> apiResponse) {
        message.getChannel().setTypingStatus(true);

        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(7);

        File imgFile = TEMP_IMG_PATH.toFile();

        java.awt.Image errorImg;

        try {
            errorImg = ImageIO.read(ERROR_IMG_PATH.toFile());
        } catch (IOException ex) {
            LOG.error("Couldn't find error image.", ex);
            return;
        }

        BufferedImage img;
        Graphics2D g;

        int i;

        switch (dimensions) {
            case "3x3":
                if (imgFile.exists())
                    imgFile.delete();

                img = new BufferedImage(3200, 2300, BufferedImage.TYPE_INT_ARGB);

                g = img.createGraphics();

                g.setPaint(new Color(0,0,0));
                g.fillRect(0,0, img.getWidth(), img.getHeight());

                g.setPaint(new Color(255,255,255));
                g.setFont(new Font("Arial", Font.PLAIN, 96));
                g.drawString(String.format("Album-Charts von %s (%s - %s)", message.getAuthor().getDisplayName(message.getGuild()), fromDate.format(dateFormat), toDate.format(dateFormat)), 100, 200);

                i = 0;

                g.setFont(new Font("Arial", Font.PLAIN, 48));

                for (Album album : apiResponse) {
                    if (i == 9)
                        break;

                    java.awt.Image albumImg;

                    try {
                        albumImg = ImageIO.read(new URL(album.getImageURL(ImageSize.LARGE)));
                    } catch (Exception ex) {
                        LOG.info("Bad url while fetching album image for collage generation - putting in error image instead", ex);
                        albumImg = errorImg;
                    }

                    g.drawImage(albumImg, coords3x3.get(i)[0], coords3x3.get(i)[1], 517, 517, null);
                    g.drawString(String.format("%s - %s", album.getArtist(), album.getName()), coords3x3.get(i)[2], coords3x3.get(i)[3]);

                    i++;
                }

                try {
                    ImageIO.write(img, "png", imgFile);
                } catch (IOException ex) {
                    LOG.error("ERROR while trying to write finished collage.", ex);
                    message.getChannel().setTypingStatus(false);
                    break;
                }

                DiscordIO.sendFile(message.getChannel(), message.getAuthor().mention(), TEMP_IMG_PATH.toFile());
                break;
            case "4x4":
                if (imgFile.exists())
                    imgFile.delete();

                img = new BufferedImage(3200, 2300, BufferedImage.TYPE_INT_ARGB);

                g = img.createGraphics();

                g.setPaint(new Color(0,0,0));
                g.fillRect(0,0, img.getWidth(), img.getHeight());

                g.setPaint(new Color(255,255,255));
                g.setFont(new Font("Arial", Font.PLAIN, 96));
                g.drawString(String.format("Album-Charts von %s (%s - %s)", message.getAuthor().getDisplayName(message.getGuild()), fromDate.format(dateFormat), toDate.format(dateFormat)), 100, 200);

                i = 0;

                g.setFont(new Font("Arial", Font.PLAIN, 48));

                for (Album album : apiResponse) {
                    if (i == 16)
                        break;

                    java.awt.Image albumImg;

                    try {
                        albumImg = ImageIO.read(new URL(album.getImageURL(ImageSize.LARGE)));
                    } catch (Exception ex) {
                        LOG.info("Bad url while fetching album image for collage generation - putting in error image instead", ex);
                        albumImg = errorImg;
                    }

                    g.drawImage(albumImg, coords4x4.get(i)[0], coords4x4.get(i)[1], 375, 375, null);
                    g.drawString(String.format("%s - %s", album.getArtist(), album.getName()), coords4x4.get(i)[2], coords4x4.get(i)[3]);

                    i++;
                }

                try {
                    ImageIO.write(img, "png", imgFile);
                } catch (IOException ex) {
                    LOG.error("ERROR while trying to write finished collage.", ex);
                    message.getChannel().setTypingStatus(false);
                    break;
                }

                DiscordIO.sendFile(message.getChannel(), message.getAuthor().mention(), TEMP_IMG_PATH.toFile());
                break;
            case "5x5":
                if (imgFile.exists())
                    imgFile.delete();

                img = new BufferedImage(3200, 2300, BufferedImage.TYPE_INT_ARGB);

                g = img.createGraphics();

                g.setPaint(new Color(0,0,0));
                g.fillRect(0,0, img.getWidth(), img.getHeight());

                g.setPaint(new Color(255,255,255));
                g.setFont(new Font("Arial", Font.PLAIN, 96));
                g.drawString(String.format("Album-Charts von %s (%s - %s)", message.getAuthor().getDisplayName(message.getGuild()), fromDate.format(dateFormat), toDate.format(dateFormat)), 100, 200);

                i = 0;

                g.setFont(new Font("Arial", Font.PLAIN, 48));

                for (Album album : apiResponse) {
                    if (i == 25)
                        break;

                    java.awt.Image albumImg;

                    try {
                        albumImg = ImageIO.read(new URL(album.getImageURL(ImageSize.LARGE)));
                    } catch (Exception ex) {
                        LOG.info("Bad url while fetching album image for collage generation - putting in error image instead", ex);
                        albumImg = errorImg;
                    }

                    g.drawImage(albumImg, coords5x5.get(i)[0], coords5x5.get(i)[1], 290, 290, null);
                    g.drawString(String.format("%s - %s", album.getArtist(), album.getName()), coords5x5.get(i)[2], coords5x5.get(i)[3]);

                    i++;
                }

                try {
                    ImageIO.write(img, "png", imgFile);
                } catch (IOException ex) {
                    LOG.error("ERROR while trying to write finished collage.", ex);
                    message.getChannel().setTypingStatus(false);
                    break;
                }

                DiscordIO.sendFile(message.getChannel(), message.getAuthor().mention(), TEMP_IMG_PATH.toFile());
                break;
            default:
                DiscordIO.sendMessage(message.getChannel(), ":x: Falsche Parameter angegeben.\n\nSyntax:\n[p]lastfm collage <albums|artists> <3x3|4x4|5x5>");
                break;
        }
    }

    private void generateAndSendArtistCollage(final IMessage message, final String dimensions, final Collection<Artist> apiResponse) {
        message.getChannel().setTypingStatus(true);

        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(7);

        File imgFile = TEMP_IMG_PATH.toFile();

        java.awt.Image errorImg;

        try {
            errorImg = ImageIO.read(ERROR_IMG_PATH.toFile());
        } catch (IOException ex) {
            LOG.error("Couldn't find error image.", ex);
            return;
        }

        BufferedImage img;
        Graphics2D g;

        int i;

        switch (dimensions) {
            case "3x3":
                if (imgFile.exists())
                    imgFile.delete();

                img = new BufferedImage(3200, 2300, BufferedImage.TYPE_INT_ARGB);

                g = img.createGraphics();

                g.setPaint(new Color(0,0,0));
                g.fillRect(0,0, img.getWidth(), img.getHeight());

                g.setPaint(new Color(255,255,255));
                g.setFont(new Font("Arial", Font.PLAIN, 96));
                g.drawString(String.format("Künstler-Charts von %s (%s - %s)", message.getAuthor().getDisplayName(message.getGuild()), fromDate.format(dateFormat), toDate.format(dateFormat)), 100, 200);

                i = 0;

                g.setFont(new Font("Arial", Font.PLAIN, 48));

                for (Artist artist : apiResponse) {
                    if (i == 9)
                        break;

                    java.awt.Image albumImg;

                    try {
                        albumImg = ImageIO.read(new URL(artist.getImageURL(ImageSize.LARGE)));
                    } catch (Exception ex) {
                        LOG.info("Bad url while fetching artist image for collage generation - putting in error image instead", ex);
                        albumImg = errorImg;
                    }

                    g.drawImage(albumImg, coords3x3.get(i)[0], coords3x3.get(i)[1], 517, 517, null);
                    g.drawString(String.format("%s (%s mal gespielt)", artist.getName(), artist.getPlaycount()), coords3x3.get(i)[2], coords3x3.get(i)[3]);

                    i++;
                }

                try {
                    ImageIO.write(img, "png", imgFile);
                } catch (IOException ex) {
                    LOG.error("ERROR while trying to write finished collage.", ex);
                    message.getChannel().setTypingStatus(false);
                    break;
                }

                DiscordIO.sendFile(message.getChannel(), message.getAuthor().mention(), TEMP_IMG_PATH.toFile());
                break;
            case "4x4":
                if (imgFile.exists())
                    imgFile.delete();

                img = new BufferedImage(3200, 2300, BufferedImage.TYPE_INT_ARGB);

                g = img.createGraphics();

                g.setPaint(new Color(0,0,0));
                g.fillRect(0,0, img.getWidth(), img.getHeight());

                g.setPaint(new Color(255,255,255));
                g.setFont(new Font("Arial", Font.PLAIN, 96));
                g.drawString(String.format("Künstler-Charts von %s (%s - %s)", message.getAuthor().getDisplayName(message.getGuild()), fromDate.format(dateFormat), toDate.format(dateFormat)), 100, 200);

                i = 0;

                g.setFont(new Font("Arial", Font.PLAIN, 48));

                for (Artist artist : apiResponse) {
                    if (i == 16)
                        break;

                    java.awt.Image albumImg;

                    try {
                        albumImg = ImageIO.read(new URL(artist.getImageURL(ImageSize.LARGE)));
                    } catch (Exception ex) {
                        LOG.info("Bad url while fetching artist image for collage generation - putting in error image instead", ex);
                        albumImg = errorImg;
                    }

                    g.drawImage(albumImg, coords4x4.get(i)[0], coords4x4.get(i)[1], 375, 375, null);
                    g.drawString(String.format("%s (%s mal gespielt)", artist.getName(), artist.getPlaycount()), coords4x4.get(i)[2], coords4x4.get(i)[3]);

                    i++;
                }

                try {
                    ImageIO.write(img, "png", imgFile);
                } catch (IOException ex) {
                    LOG.error("ERROR while trying to write finished collage.", ex);
                    message.getChannel().setTypingStatus(false);
                    break;
                }

                DiscordIO.sendFile(message.getChannel(), message.getAuthor().mention(), TEMP_IMG_PATH.toFile());
                break;
            case "5x5":
                if (imgFile.exists())
                    imgFile.delete();

                img = new BufferedImage(3200, 2300, BufferedImage.TYPE_INT_ARGB);

                g = img.createGraphics();

                g.setPaint(new Color(0,0,0));
                g.fillRect(0,0, img.getWidth(), img.getHeight());

                g.setPaint(new Color(255,255,255));
                g.setFont(new Font("Arial", Font.PLAIN, 96));
                g.drawString(String.format("Künstler-Charts von %s (%s - %s)", message.getAuthor().getDisplayName(message.getGuild()), fromDate.format(dateFormat), toDate.format(dateFormat)), 100, 200);

                i = 0;

                g.setFont(new Font("Arial", Font.PLAIN, 48));

                for (Artist artist : apiResponse) {
                    if (i == 25)
                        break;

                    java.awt.Image albumImg;

                    try {
                        albumImg = ImageIO.read(new URL(artist.getImageURL(ImageSize.LARGE)));
                    } catch (Exception ex) {
                        LOG.info("Bad url while fetching artist image for collage generation - putting in error image instead", ex);
                        albumImg = errorImg;
                    }

                    g.drawImage(albumImg, coords5x5.get(i)[0], coords5x5.get(i)[1], 290, 290, null);
                    g.drawString(String.format("%s (%s mal gespielt)", artist.getName(), artist.getPlaycount()), coords5x5.get(i)[2], coords5x5.get(i)[3]);

                    i++;
                }

                try {
                    ImageIO.write(img, "png", imgFile);
                } catch (IOException ex) {
                    LOG.error("ERROR while trying to write finished collage.", ex);
                    message.getChannel().setTypingStatus(false);
                    break;
                }

                DiscordIO.sendFile(message.getChannel(), message.getAuthor().mention(), TEMP_IMG_PATH.toFile());
                break;
            default:
                DiscordIO.sendMessage(message.getChannel(), ":x: Falsche Parameter angegeben.\n\nSyntax:\n[p]lastfm collage <albums|artists> <3x3|4x4|5x5>");
                break;
        }
    }

    private void saveJSON() {
        LOG.info("Saving Last.fm config file.");

        final String jsonOutput = lastFmJSON.toString(4);
        IOUtil.writeToFile(LASTFM_PATH, jsonOutput);
    }
}
