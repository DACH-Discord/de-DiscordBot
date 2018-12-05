package de.nikos410.discordbot.util;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandUtils {

    /**
     * Parse a time unit indicator to a {@link java.time.temporal.ChronoUnit}.
     * Supports the following indcators: s, m, h, d. Returns null if any other other inicator is given.
     *
     * @param chronoUnitString The inicator to find a ChronoUnit for.
     * @return The ChronoUnit, or null if an unsupported indicator was given.
     */
    public static ChronoUnit parseChronoUnit (final String chronoUnitString) {
        switch (chronoUnitString.toLowerCase()) {
            case "s": return ChronoUnit.SECONDS;
            case "m": return ChronoUnit.MINUTES;
            case "h": return ChronoUnit.HOURS;
            case "d": return ChronoUnit.DAYS;
            default: return null;
        }
    }

    /**
     * Return the corresponding {@link java.util.concurrent.TimeUnit} for a given {@link java.time.temporal.ChronoUnit}.
     * Works for SECONDS, MINUTES, HOURS, DAYS
     *
     * @param chronoUnit The chronounit for which to return the corresponding TimeUnit
     * @return The TimeUnit
     */
    public static TimeUnit toTimeUnit (final ChronoUnit chronoUnit) {
        switch (chronoUnit) {
            case SECONDS: return TimeUnit.SECONDS;
            case MINUTES: return TimeUnit.MINUTES;
            case HOURS: return TimeUnit.HOURS;
            case DAYS: return TimeUnit.DAYS;

            default: throw new UnsupportedOperationException("Unsupported ChronoUnit");
        }
    }

    public static DurationParameters parseDurationParameters(final String input) {
        final Pattern pattern = Pattern.compile("(\\d+)\\s*([smhd])\\s*(.*)");
        final Matcher matcher = pattern.matcher(input);

        if (!matcher.matches()) {
            // No valid duration was specified
            return null;
        }

        final int muteDuration = Integer.parseInt(matcher.group(1));
        final ChronoUnit muteDurationUnit = parseChronoUnit(matcher.group(2));
        final String message = matcher.group(3);

        return new DurationParameters(muteDuration, muteDurationUnit, message);
    }

    public static class DurationParameters {
        private int duration;
        private ChronoUnit durationUnit;
        private String message;

        private DurationParameters(final int duration, final ChronoUnit durationUnit, final String message) {
            this.duration = duration;
            this.durationUnit = durationUnit;
            this.message = message;
        }

        public int getDuration() {
            return duration;
        }

        public ChronoUnit getDurationUnit() {
            return durationUnit;
        }

        public String getMessage() {
            return message;
        }
    }
}
