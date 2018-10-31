package de.nikos410.discordBot.util.discord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.api.ClientBuilder;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.util.DiscordException;

/**
 * Authorizes the Bot and returns Instance
 */
public class Authorization {
    private final static Logger log = LoggerFactory.getLogger(Authorization.class);

    public static IDiscordClient createClient(final String token, final boolean login) {
        final ClientBuilder clientBuilder = new ClientBuilder();
        clientBuilder.withToken(token);
        try {
            if (login) {
                return clientBuilder.login();
            } else {
                return clientBuilder.build();
            }
        } catch (DiscordException e) {
            log.error("Could not authorize the bot. Please make sure your token is correct.", e);
            throw e;
        }
    }
}