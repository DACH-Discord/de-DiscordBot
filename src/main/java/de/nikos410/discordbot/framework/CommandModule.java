package de.nikos410.discordbot.framework;

import de.nikos410.discordbot.DiscordBot;

public abstract class CommandModule {
    protected DiscordBot bot = null;

    /**
     * @return Specify the name of this module that is seen by the public. (e.g. when listing commands)
     */
    public abstract String getDisplayName();

    /**
     * @return Provide a brief description of what the module does.
     */
    public abstract String getDescription();

    /**
     * @return Specify whether to register the module with the bot's {@link sx.blah.discord.api.events.EventDispatcher}.
     * Set this to true if this module is using {@link sx.blah.discord.api.events.EventSubscriber}s.
     */
    public boolean hasEvents() {
        return false;
    }

    /**
     * Set the bot field to the current bot instance.
     *
     * @param bot The bot instance.
     */
    public void setBot(final DiscordBot bot) {
        this.bot = bot;
    }

    /**
     * Gets called when the module is initialized.
     */
    public void init() {}

    /**
     * Gets called when the module is initialized and the bot is ready.
     */
    public void initWhenReady() {}

    /**
     * Gets called when the module is disabled or the bot is shut down
     */
    public void shutdown() {}
}
