package nikos.discordBot;

import nikos.discordBot.modules.*;
import nikos.discordBot.util.Authorization;
import nikos.discordBot.util.Util;
import org.json.JSONObject;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventDispatcher;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.impl.events.ReadyEvent;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * First try at a Discord Bot
 * <strong>Main class</strong>
 */
public class DiscordBot {
    private final static Path CONFIG_FILE = Paths.get("config/config.json");

    private final IDiscordClient client;
    private final String prefix;

    private DiscordBot() {
        // Token aus Config-Datei auslesen
        final String configFileContent = Util.readFile(CONFIG_FILE);
        final JSONObject json = new JSONObject(configFileContent);
        final String token = json.getString("token");
        this.prefix = json.getString("prefix");

        this.client = Authorization.createClient(token, true);

        try {
            EventDispatcher dispatcher = client.getDispatcher();

            dispatcher.registerListener(this);
            dispatcher.registerListener(new StandardCommands(client));
            dispatcher.registerListener(new Games(client));
            dispatcher.registerListener(new UserLog(client));
            dispatcher.registerListener(new Rules(client));
            dispatcher.registerListener(new RedditLinker(client));
            dispatcher.registerListener(new Poll(client));
            dispatcher.registerListener(new WapBapRemover(client));
            dispatcher.registerListener(new Roll(client));
        }
        catch (NullPointerException e) {
            System.err.println("[Error] Could not get EventDispatcher: " + '\n' + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventSubscriber
    public void onStartup(ReadyEvent event) {
        System.out.println("[INFO] Bot ready. Prefix: " + this.prefix);
        client.changePlayingText(this.prefix + "help | WIP");
    }

    public static void main(String[] args) {
        new DiscordBot();
    }
}
