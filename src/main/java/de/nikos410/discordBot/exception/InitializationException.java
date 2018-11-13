package de.nikos410.discordBot.exception;

public class InitializationException extends RuntimeException {
    private final Class<?> module;

    public InitializationException(String message, Class<?> module) {
        super(message);
        this.module = module;
    }

    public InitializationException(String message, Throwable cause, Class<?> module) {
        super(message, cause);
        this.module = module;
    }

    public Class<?> getModule() {
        return this.module;
    }
}
