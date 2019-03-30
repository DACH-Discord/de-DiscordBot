package de.nikos410.discordbot.modules;

import de.nikos410.discordbot.exception.InitializationException;
import de.nikos410.discordbot.framework.CommandModule;
import de.nikos410.discordbot.framework.PermissionLevel;
import de.nikos410.discordbot.framework.annotations.CommandParameter;
import de.nikos410.discordbot.framework.annotations.CommandSubscriber;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Iterator;

public class LastFm extends CommandModule {
    private static final Logger LOG = LoggerFactory.getLogger(LastFm.class);

    private static final String ERROR_IMG_RESOURCE = "modules/lastfm/error-icon.png";
    private static final URL ERROR_IMG_URL;
    static {
        ERROR_IMG_URL = LastFm.class.getClassLoader().getResource(ERROR_IMG_RESOURCE);
    }

    private static final Path LASTFM_PATH = Paths.get("data/lastFm/lastFm.json");
    private static final Path TEMP_IMG_PATH = Paths.get("data/lastFm/chart.png");

    private String botPrefix;

    private JSONObject lastFmJSON;

    private int[] offsets3x3;
    private int[] offsets4x4;
    private int[] offsets5x5;

    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private String apiKey;

    @Override
    public String getDisplayName() {
        return "Last.fm";
    }

    @Override
    public String getDescription() {
        return "Erzeugt collagen aus verschiedenen Last.fm Informationen.";
    }

    @Override
    public void init() {
        this.botPrefix = bot.configJSON.getString("prefix");

        final String jsonContent = IOUtil.readFile(LASTFM_PATH);
        if (jsonContent == null) {
            throw new InitializationException("Could not read module data.", LastFm.class);
        }
        this.lastFmJSON = new JSONObject(jsonContent);
        LOG.info("Loaded Last.fm config file.");

        if (!lastFmJSON.has("apiKey")) {
            throw new InitializationException("No Last.fm Api-Key configured.", LastFm.class);
        }

        this.apiKey = lastFmJSON.getString("apiKey");

        Caller.getInstance().setUserAgent("de-DiscordBot/1.0");
        Caller.getInstance().setCache(null); // disable caching to always get recent charts

        offsets3x3 = new int[]{3, 567, 108, 243};
        offsets4x4 = new int[]{4, 425, 78, 113};
        offsets5x5 = new int[]{5, 340, 58, 49};
    }

    @CommandSubscriber(command = "lastFmSetApiKey", help = "Last.fm API-Key setzen.", pmAllowed = false, permissionLevel = PermissionLevel.ADMIN)
    public void command_lastFmSetApiKey(final IMessage message,
                                        @CommandParameter(name = "Key", help = "Der Last.fm API-Key der benutzt werden soll.")
                                        final String key) {
        lastFmJSON.put("apiKey", key);
        saveJSON();

        this.apiKey = key;

        messageService.sendMessage(message.getChannel(), ":white_check_mark: Last.fm API-Key gesetzt.");
    }

    @CommandSubscriber(command = "lastfm", help = "Last.fm Modul - Parameter 'help' für Hilfe", pmAllowed = false, ignoreParameterCount = true)
    public void command_lastfm(final IMessage message,
                               @CommandParameter(name = "Funktion", help = "Die Funktion, die benutzt werden soll. 'help' um alle anzuzeigen.")
                               final String function,
                               @CommandParameter(name = "Parameter", help = "Die Parameter für die Funktion, die benutzt wird.")
                               final String arguments) {
        if (!apiKey.equals("")) {
            final String[] args = arguments.trim().split(" ");

            switch (function) {
                case "set":
                    try {
                        setUsername(message, args[0]);
                    } catch (ArrayIndexOutOfBoundsException ex) {
                        messageService.sendMessage(message.getChannel(), String.format(":x: Keinen Last.fm-Usernamen angegeben. '%slastfm help' für Hilfe.", botPrefix));
                    }
                    break;
                case "now":
                    getChart(message, Target.NOWPLAYING, Type.NONE);
                    break;
                case "recenttracks":
                    getChart(message, Target.RECENT, Type.NONE);
                    break;
                case "topartists":
                    getChart(message, Target.ARTISTS, Type.OVERALL);
                    break;
                case "topalbums":
                    getChart(message, Target.ALBUMS, Type.OVERALL);
                    break;
                case "toptracks":
                    getChart(message, Target.TRACKS, Type.OVERALL);
                    break;
                case "weeklyartists":
                    getChart(message, Target.ARTISTS, Type.WEEKLY);
                    break;
                case "weeklyalbums":
                    getChart(message, Target.ALBUMS, Type.WEEKLY);
                    break;
                case "weeklytracks":
                    getChart(message, Target.TRACKS, Type.WEEKLY);
                    break;
                case "collage":
                    try {
                        sendCollage(message, args[0], args[1]);
                    } catch (ArrayIndexOutOfBoundsException ex) {
                        messageService.sendMessage(message.getChannel(), String.format(":x: Falsche Parameter angegeben. '%slastfm help' für Hilfe.", botPrefix));
                    }
                    break;
                case "help":
                    String msg = "```Last.fm Modul - Hilfe\n\n"
                            + String.format("'%slastfm set <last.fm username>' um deinen Last.fm-Nutzernamen zu setzen.%n%n", botPrefix)
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

                    messageService.sendMessage(message.getChannel(), msg);
                    break;
                default:
                    messageService.sendMessage(message.getChannel(), String.format(":x: Keine gültigen Parameter angegeben. '%slastfm help' für Hilfe.", botPrefix));
            }
        } else {
            messageService.sendMessage(message.getChannel(), ":x: Kein Last.fm API-Key vorhanden.");
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

            messageService.sendMessage(message.getChannel(), ":white_check_mark: Last.fm-Username gesetzt.");
        } else {
            messageService.sendMessage(message.getChannel(), ":x: Ungültigen Last.fm-Usernamen angegeben oder fehlerhafter API-Key.");
        }
    }

    private void getChart(final IMessage message, final Target target, final Type type) {
        getChart(message, target, type, 10);
    }

    private void getChart(final IMessage message, final Target target, final Type type, final int limit) {
        try {
            message.getChannel().setTypingStatus(true);

            String username = lastFmJSON.getJSONObject("users").getString(message.getAuthor().getStringID());

            String title = "";
            String titleAppendix = "";

            Period period = Period.OVERALL;

            LocalDate toDate = LocalDate.now();
            LocalDate fromDate = toDate.minusDays(7);

            EmbedBuilder embedBuilder = new EmbedBuilder();

            embedBuilder.withColor(185, 0, 0);
            embedBuilder.withAuthorIcon(message.getAuthor().getAvatarURL());

            Collection<?> response = null;

            Track track;
            Artist artist;
            Album album;

            switch (type) {
                case OVERALL:
                    // use default value
                    break;
                case WEEKLY:
                    period = Period.WEEK;
                    titleAppendix = String.format("%n(Woche vom %s zum %s)", fromDate.format(dateFormat), toDate.format(dateFormat));
                    break;
                default:
                    // use default value; NONE defaults here
                    break;
            }

            switch (target) {
                case ALBUMS:
                    title = String.format("Top-Alben von %s", message.getAuthor().getDisplayName(message.getGuild()));
                    response = User.getTopAlbums(username, period, apiKey);
                    break;
                case ARTISTS:
                    title = String.format("Top-Künstler von %s", message.getAuthor().getDisplayName(message.getGuild()));
                    response = User.getTopArtists(username, period, apiKey);
                    break;
                case TRACKS:
                    title = String.format("Top-Titel von %s", message.getAuthor().getDisplayName(message.getGuild()));
                    response = User.getTopTracks(username, period, apiKey);
                    break;
                case RECENT:
                    title = String.format("Kürzlich gespielte Tracks von %s", message.getAuthor().getDisplayName(message.getGuild()));
                    response = User.getRecentTracks(username, apiKey).getPageResults();
                    break;
                case NOWPLAYING:
                    title = String.format("Aktuell gespielter Track von %s", message.getAuthor().getDisplayName(message.getGuild()));
                    response = User.getRecentTracks(username, apiKey).getPageResults();
                    break;
            }

            embedBuilder.withAuthorName(title + titleAppendix);

            StringBuilder chart = new StringBuilder();

            int i = 0;

            if (response != null) {
                Iterator itor = response.iterator();

                while (itor.hasNext() && i < limit) {
                    Object responseObj = itor.next();

                    if (responseObj instanceof Track) {
                        track = (Track)responseObj;
                        if (target == Target.NOWPLAYING) {
                            if (track.isNowPlaying()) {
                                embedBuilder.appendField("Künstler", track.getArtist(), true);
                                embedBuilder.appendField("Titel", track.getName(), true);
                                embedBuilder.appendField("Album", track.getAlbum(), false);
                                embedBuilder.withThumbnail(track.getImageURL(ImageSize.LARGE));
                                break; // only process first track, if target is NOWPLAYING.
                            } else {
                                messageService.sendMessage(message.getChannel(), ":x: Du hörst gerade nichts.");
                                return; // leave method if no track is playing
                            }
                        } else if (target == Target.RECENT) {
                            if (track.isNowPlaying())
                                continue; // skip currently playing track, only do scrobbled tracks
                            chart.append(String.format("`%s` **%s** - *%s*%n", i + 1, track.getArtist(), track.getName()));
                        } else {
                            chart.append(String.format("`%s` **%s** - *%s* (%s mal gespielt)%n", i + 1, track.getArtist(), track.getName(), track.getPlaycount()));
                        }
                    } else if (responseObj instanceof Artist) {
                        artist = (Artist)responseObj;
                        chart.append(String.format("`%s` **%s** (%s mal gespielt)%n", i + 1, artist.getName(), artist.getPlaycount()));
                    } else if (responseObj instanceof Album) {
                        album = (Album)responseObj;
                        chart.append(String.format("`%s` **%s** - *%s* (%s mal gespielt)%n", i + 1, album.getArtist(), album.getName(), album.getPlaycount()));
                    }

                    i++;
                }
            }

            embedBuilder.withDesc(chart.toString());

            messageService.sendEmbed(message.getChannel(), embedBuilder.build());
        } catch (JSONException ex) {
            messageService.sendMessage(message.getChannel(), String.format(":x: Du hast noch keinen Last.fm-Usernamen gesetzt. '%slastfm help' für Hilfe.", botPrefix));
        }
    }

    private void sendCollage(final IMessage message, final String target, final String dimensions) {
        try {
            message.getChannel().setTypingStatus(true);

            int[] coords = new int[]{100, 400, 1801, 445};

            String username = lastFmJSON.getJSONObject("users").getString(message.getAuthor().getStringID());

            int[] offsetsToUse;
            int imgSize;

            Collection<?> response;

            String title;

            LocalDate toDate = LocalDate.now();
            LocalDate fromDate = toDate.minusDays(7);

            File imgFile = TEMP_IMG_PATH.toFile();

            java.awt.Image albumImg;

            Artist artist;
            Album album;

            BufferedImage img;
            Graphics2D g;

            int i;
            int iOffset;
            int limit;

            switch (target) {
                case "albums":
                    response = User.getTopAlbums(username, Period.WEEK, apiKey);
                    title = String.format("Album-Charts von %s (%s - %s)", message.getAuthor().getDisplayName(message.getGuild()), fromDate.format(dateFormat), toDate.format(dateFormat));
                    break;
                case "artists":
                    response = User.getTopArtists(username, Period.WEEK, apiKey);
                    title = String.format("Künstler-Charts von %s (%s - %s)", message.getAuthor().getDisplayName(message.getGuild()), fromDate.format(dateFormat), toDate.format(dateFormat));
                    break;
                default:
                    messageService.sendMessage(message.getChannel(), String.format(":x: Falsche Parameter angegeben. '%slastfm help' für Hilfe.", botPrefix));
                    return;
            }

            switch (dimensions) {
                case "3x3":
                    offsetsToUse = offsets3x3;
                    limit = 9;
                    imgSize = 517;
                    break;
                case "4x4":
                    offsetsToUse = offsets4x4;
                    limit = 16;
                    imgSize = 375;
                    break;
                case "5x5":
                    offsetsToUse = offsets5x5;
                    limit = 25;
                    imgSize = 290;
                    break;
                default:
                    messageService.sendMessage(message.getChannel(), String.format(":x: Falsche Parameter angegeben. '%slastfm help' für Hilfe.", botPrefix));
                    return;
            }

            try {
                LOG.debug("Chart png file already found - deleting.");
                Files.deleteIfExists(imgFile.toPath());
            }
            catch (IOException e) {
                LOG.error("Chart png could not be deleted.", e);
            }

            img = new BufferedImage(3200, 2300, BufferedImage.TYPE_INT_ARGB);

            g = img.createGraphics();

            g.setPaint(new Color(0,0,0));
            g.fillRect(0,0, img.getWidth(), img.getHeight());

            g.setPaint(new Color(255,255,255));
            g.setFont(new Font("Arial", Font.PLAIN, 96));
            g.drawString(title, 100, 200);

            i = 0;
            iOffset = 1;

            g.setFont(new Font("Arial", Font.PLAIN, 48));

            if (response != null) {
                Iterator itor = response.iterator();

                while (itor.hasNext() && i < limit) {
                    if (iOffset > offsetsToUse[0]) { // determine if new row
                        iOffset = 1;
                        coords[0] = 100; // new row - reset coordinate
                        coords[1] += offsetsToUse[1]; // new row - add y offset for picture
                        coords[3] += offsetsToUse[3]; // new row - add y offset gap adjust for text
                    }

                    Object responseObj = itor.next();

                    if (responseObj instanceof Artist) {
                        artist = (Artist)responseObj;
                        try {
                            albumImg = ImageIO.read(new URL(artist.getImageURL(ImageSize.LARGE)));
                        } catch (Exception ex) {
                            LOG.info("Bad url while fetching artist image for collage generation - putting in error image instead");
                            albumImg = getErrorImage();
                        }
                        g.drawImage(albumImg, coords[0], coords[1], imgSize, imgSize, null);
                        g.drawString(String.format("%s (%s mal gespielt)", artist.getName(), artist.getPlaycount()), coords[2], coords[3]);
                    } else if (responseObj instanceof Album) {
                        album = (Album)responseObj;
                        try {
                            albumImg = ImageIO.read(new URL(album.getImageURL(ImageSize.LARGE)));
                        } catch (Exception ex) {
                            LOG.info("Bad url while fetching album image for collage generation - putting in error image instead");
                            albumImg = getErrorImage();
                        }
                        g.drawImage(albumImg, coords[0], coords[1], imgSize, imgSize, null);
                        g.drawString(String.format("%s - %s", album.getArtist(), album.getName()), coords[2], coords[3]);
                    }

                    coords[0] += offsetsToUse[1]; // new entry - add x offset for picture
                    coords[3] += offsetsToUse[2]; // new entry - add y offset for text
                    iOffset++;
                    i++;
                }
            }

            try {
                ImageIO.write(img, "png", imgFile);
            } catch (IOException ex) {
                LOG.error("ERROR while trying to write finished collage.", ex);
                message.getChannel().setTypingStatus(false);
                return;
            }

            messageService.sendMessage(message.getChannel(), message.getAuthor().mention(), TEMP_IMG_PATH.toFile());
        } catch (JSONException ex) {
            messageService.sendMessage(message.getChannel(), String.format(":x: Du hast noch keinen Last.fm-Usernamen gesetzt. '%slastfm help' für Hilfe.", botPrefix));
        }
    }

    private static java.awt.Image getErrorImage() {
        try {
            return ImageIO.read(ERROR_IMG_URL);
        } catch (IOException | IllegalArgumentException ex) {
            LOG.error("Couldn't find error image.", ex);
            return null;
        }
    }

    private void saveJSON() {
        LOG.info("Saving Last.fm config file.");

        final String jsonOutput = lastFmJSON.toString(4);
        IOUtil.writeToFile(LASTFM_PATH, jsonOutput);
    }

    private enum Target {
        NOWPLAYING,
        RECENT,
        TRACKS,
        ALBUMS,
        ARTISTS
    }

    private enum Type {
        OVERALL,
        WEEKLY,
        NONE
    }
}
