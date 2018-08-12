package de.nikos410.discordBot.framework.annotations;

import de.nikos410.discordBot.framework.CommandPermissions;

import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandSubscriber {
    String command();
    String help();
    boolean pmAllowed() default true;
    int permissionLevel() default CommandPermissions.EVERYONE;
    boolean passContext() default true;
}
