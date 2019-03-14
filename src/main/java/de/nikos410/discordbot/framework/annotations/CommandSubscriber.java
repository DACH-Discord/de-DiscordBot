package de.nikos410.discordbot.framework.annotations;

import de.nikos410.discordbot.framework.PermissionLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandSubscriber {
    String command();
    String help();
    boolean pmAllowed() default true;
    PermissionLevel permissionLevel() default PermissionLevel.EVERYONE;
    boolean passContext() default true;
    boolean ignoreParameterCount() default false;
}
