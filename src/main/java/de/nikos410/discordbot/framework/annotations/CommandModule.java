package de.nikos410.discordbot.framework.annotations;

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Mark a class as a module that contains commands so it will be loaded by the bot.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandModule {
    /**
     * The module's name.
     */
    String moduleName();

    /**
     * Specify if the module only contains commands or if one ore more additional EventSubscribers are present, so that
     * the module needs to be registered in the EventDispatcher.
     */
    boolean commandOnly();
}
