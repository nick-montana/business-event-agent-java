package io.github.nick_montana.bizevents.autoconfigure;

import com.dynatrace.openkit.core.configuration.ConfigurationDefaults;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the user-facing beacon cache properties into the values OpenKit's builder expects.
 *
 * <ul>
 *   <li>{@code openkit.bizevents.beaconCacheMaxRecordAge}: a duration such as {@code 10m}, {@code 60s},
 *       {@code 500ms}, or a plain number of milliseconds. Converted to milliseconds.</li>
 *   <li>{@code openkit.bizevents.beaconCacheLowerMemoryBoundary} and
 *       {@code openkit.bizevents.beaconCacheUpperMemoryBoundary}: a whole number of megabytes (MiB), with no unit.
 *       Converted to bytes the same way OpenKit defines its defaults ({@code mb * 1024 * 1024}).</li>
 * </ul>
 *
 * A property that is not set falls back to the OpenKit default. A property that is set to an invalid value
 * is logged as bad configuration and also falls back to the OpenKit default, so a typo never stops start-up.
 */
final class BeaconCacheConfig {

    static final String MAX_RECORD_AGE_PROPERTY = "openkit.bizevents.beaconCacheMaxRecordAge";
    static final String LOWER_MEMORY_BOUNDARY_PROPERTY = "openkit.bizevents.beaconCacheLowerMemoryBoundary";
    static final String UPPER_MEMORY_BOUNDARY_PROPERTY = "openkit.bizevents.beaconCacheUpperMemoryBoundary";

    static final long DEFAULT_MAX_RECORD_AGE_MILLIS = ConfigurationDefaults.DEFAULT_MAX_RECORD_AGE_IN_MILLIS;
    static final long DEFAULT_LOWER_MEMORY_BOUNDARY_BYTES = ConfigurationDefaults.DEFAULT_LOWER_MEMORY_BOUNDARY_IN_BYTES;
    static final long DEFAULT_UPPER_MEMORY_BOUNDARY_BYTES = ConfigurationDefaults.DEFAULT_UPPER_MEMORY_BOUNDARY_IN_BYTES;

    static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    // <number><optional unit>, where unit is ms (milliseconds), s (seconds) or m (minutes)
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)(ms|s|m)?$");
    // a whole number of megabytes, no unit
    private static final Pattern MEGABYTES_PATTERN = Pattern.compile("^\\d+$");

    private static final Logger log = Logger.getLogger(BeaconCacheConfig.class.getName());

    /** Lower and upper beacon cache memory boundaries, in bytes. */
    record MemoryBoundaries(long lowerBytes, long upperBytes) { }

    private BeaconCacheConfig() { }

    /**
     * @param rawValue the raw property value, or {@code null} if the property is not set
     * @return the maximum record age in milliseconds
     */
    static long resolveMaxRecordAge(String rawValue) {
        if (rawValue == null) {
            log.info(MAX_RECORD_AGE_PROPERTY + " is not set. Using the OpenKit default of "
                    + DEFAULT_MAX_RECORD_AGE_MILLIS + " ms.");
            return DEFAULT_MAX_RECORD_AGE_MILLIS;
        }

        OptionalLong millis = parseDurationToMillis(rawValue);
        if (millis.isEmpty()) {
            log.warning("Bad configuration detected: " + MAX_RECORD_AGE_PROPERTY + "='" + rawValue
                    + "' is not a positive duration such as 10m, 60s, 500ms or 2700000. Using the OpenKit default of "
                    + DEFAULT_MAX_RECORD_AGE_MILLIS + " ms.");
            return DEFAULT_MAX_RECORD_AGE_MILLIS;
        }

        log.info("Using user-defined " + MAX_RECORD_AGE_PROPERTY + "='" + rawValue + "' ("
                + millis.getAsLong() + " ms).");
        return millis.getAsLong();
    }

    /**
     * @param rawLower the raw lower boundary in megabytes, or {@code null} if not set
     * @param rawUpper the raw upper boundary in megabytes, or {@code null} if not set
     * @return the lower and upper boundaries in bytes
     */
    static MemoryBoundaries resolveMemoryBoundaries(String rawLower, String rawUpper) {
        long lower = resolveMemoryBoundary(LOWER_MEMORY_BOUNDARY_PROPERTY, rawLower, DEFAULT_LOWER_MEMORY_BOUNDARY_BYTES);
        long upper = resolveMemoryBoundary(UPPER_MEMORY_BOUNDARY_PROPERTY, rawUpper, DEFAULT_UPPER_MEMORY_BOUNDARY_BYTES);

        // OpenKit silently disables space-based eviction when upper <= lower, so treat that as bad configuration
        if (upper <= lower) {
            log.warning("Bad configuration detected: " + LOWER_MEMORY_BOUNDARY_PROPERTY + " (" + toMegabytes(lower)
                    + " MB) must be less than " + UPPER_MEMORY_BOUNDARY_PROPERTY + " (" + toMegabytes(upper)
                    + " MB). Using the OpenKit defaults of " + toMegabytes(DEFAULT_LOWER_MEMORY_BOUNDARY_BYTES)
                    + " MB and " + toMegabytes(DEFAULT_UPPER_MEMORY_BOUNDARY_BYTES) + " MB.");
            return new MemoryBoundaries(DEFAULT_LOWER_MEMORY_BOUNDARY_BYTES, DEFAULT_UPPER_MEMORY_BOUNDARY_BYTES);
        }

        return new MemoryBoundaries(lower, upper);
    }

    private static long resolveMemoryBoundary(String propertyName, String rawValue, long defaultBytes) {
        if (rawValue == null) {
            log.info(propertyName + " is not set. Using the OpenKit default of "
                    + toMegabytes(defaultBytes) + " MB (" + defaultBytes + " bytes).");
            return defaultBytes;
        }

        OptionalLong bytes = parseMegabytesToBytes(rawValue);
        if (bytes.isEmpty()) {
            log.warning("Bad configuration detected: " + propertyName + "='" + rawValue
                    + "' is not a positive whole number of megabytes (for example 80). Using the OpenKit default of "
                    + toMegabytes(defaultBytes) + " MB (" + defaultBytes + " bytes).");
            return defaultBytes;
        }

        log.info("Using user-defined " + propertyName + "='" + rawValue + "' (" + bytes.getAsLong() + " bytes).");
        return bytes.getAsLong();
    }

    /** Parses values like 10m, 60s, 500ms or 2700000 (milliseconds). Empty if invalid, zero or overflowing. */
    static OptionalLong parseDurationToMillis(String rawValue) {
        Matcher matcher = DURATION_PATTERN.matcher(rawValue.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return OptionalLong.empty();
        }
        try {
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2) == null ? "ms" : matcher.group(2);
            long millis = switch (unit) {
                case "m" -> Math.multiplyExact(amount, 60_000L);
                case "s" -> Math.multiplyExact(amount, 1_000L);
                default -> amount;
            };
            return millis > 0 ? OptionalLong.of(millis) : OptionalLong.empty();
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** Parses a whole number of megabytes into bytes. Empty if invalid, zero or overflowing. */
    static OptionalLong parseMegabytesToBytes(String rawValue) {
        String value = rawValue.trim();
        if (!MEGABYTES_PATTERN.matcher(value).matches()) {
            return OptionalLong.empty();
        }
        try {
            long bytes = Math.multiplyExact(Long.parseLong(value), BYTES_PER_MEGABYTE);
            return bytes > 0 ? OptionalLong.of(bytes) : OptionalLong.empty();
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private static String toMegabytes(long bytes) {
        return bytes % BYTES_PER_MEGABYTE == 0
                ? Long.toString(bytes / BYTES_PER_MEGABYTE)
                : String.format(Locale.ROOT, "%.2f", (double) bytes / BYTES_PER_MEGABYTE);
    }
}
