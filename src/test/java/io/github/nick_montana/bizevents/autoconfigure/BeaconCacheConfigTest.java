package io.github.nick_montana.bizevents.autoconfigure;

import com.dynatrace.openkit.DynatraceOpenKitBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.github.nick_montana.bizevents.autoconfigure.BeaconCacheConfig.*;
import static org.assertj.core.api.Assertions.assertThat;

class BeaconCacheConfigTest {

    private static final long MB = 1024L * 1024L;

    private final Logger logger = Logger.getLogger(BeaconCacheConfig.class.getName());
    private final List<LogRecord> logRecords = new ArrayList<>();
    private final Handler captureHandler = new Handler() {
        @Override public void publish(LogRecord record) { logRecords.add(record); }
        @Override public void flush() { }
        @Override public void close() { }
    };

    @BeforeEach
    void captureLogs() {
        logger.addHandler(captureHandler);
    }

    @AfterEach
    void releaseLogs() {
        logger.removeHandler(captureHandler);
    }

    private boolean logged(Level level, String... fragments) {
        return logRecords.stream().anyMatch(r -> r.getLevel().equals(level)
                && List.of(fragments).stream().allMatch(f -> r.getMessage().contains(f)));
    }

    private boolean anyWarnings() {
        return logRecords.stream().anyMatch(r -> r.getLevel().equals(Level.WARNING));
    }

    @Test
    void openKitDefaultsMatchDocumentedValues() {
        assertThat(DEFAULT_MAX_RECORD_AGE_MILLIS).isEqualTo(45L * 60L * 1000L);
        assertThat(DEFAULT_LOWER_MEMORY_BOUNDARY_BYTES).isEqualTo(80L * MB);
        assertThat(DEFAULT_UPPER_MEMORY_BOUNDARY_BYTES).isEqualTo(100L * MB);
    }

    @Nested
    class MaxRecordAge {

        @Test
        void unsetUsesDefaultAndLogsIt() {
            assertThat(resolveMaxRecordAge(null)).isEqualTo(DEFAULT_MAX_RECORD_AGE_MILLIS);
            assertThat(logged(Level.INFO, MAX_RECORD_AGE_PROPERTY, "is not set", "OpenKit default")).isTrue();
            assertThat(anyWarnings()).isFalse();
        }

        @Test
        void minutesAreConvertedToMilliseconds() {
            assertThat(resolveMaxRecordAge("10m")).isEqualTo(600_000L);
            assertThat(logged(Level.INFO, "user-defined", MAX_RECORD_AGE_PROPERTY, "600000 ms")).isTrue();
            assertThat(anyWarnings()).isFalse();
        }

        @Test
        void secondsAreConvertedToMilliseconds() {
            assertThat(resolveMaxRecordAge("60s")).isEqualTo(60_000L);
        }

        @Test
        void millisecondsAreKept() {
            assertThat(resolveMaxRecordAge("500ms")).isEqualTo(500L);
            assertThat(resolveMaxRecordAge("2700000")).isEqualTo(2_700_000L);
        }

        @Test
        void surroundingWhitespaceAndUppercaseUnitsAreAccepted() {
            assertThat(resolveMaxRecordAge(" 10M ")).isEqualTo(600_000L);
            assertThat(resolveMaxRecordAge("30S")).isEqualTo(30_000L);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "abc", "10h", "10 m", "1.5m", "-5m", "0", "0s", "m", "10mm", "999999999999999999m"})
        void invalidValueLogsBadConfigurationAndUsesDefault(String rawValue) {
            assertThat(resolveMaxRecordAge(rawValue)).isEqualTo(DEFAULT_MAX_RECORD_AGE_MILLIS);
            assertThat(logged(Level.WARNING, "Bad configuration detected", MAX_RECORD_AGE_PROPERTY, "OpenKit default"))
                    .isTrue();
        }
    }

    @Nested
    class MemoryBoundaryResolution {

        @Test
        void unsetUsesDefaultsAndLogsThem() {
            BeaconCacheConfig.MemoryBoundaries result = resolveMemoryBoundaries(null, null);

            assertThat(result.lowerBytes()).isEqualTo(DEFAULT_LOWER_MEMORY_BOUNDARY_BYTES);
            assertThat(result.upperBytes()).isEqualTo(DEFAULT_UPPER_MEMORY_BOUNDARY_BYTES);
            assertThat(logged(Level.INFO, LOWER_MEMORY_BOUNDARY_PROPERTY, "is not set", "80 MB")).isTrue();
            assertThat(logged(Level.INFO, UPPER_MEMORY_BOUNDARY_PROPERTY, "is not set", "100 MB")).isTrue();
            assertThat(anyWarnings()).isFalse();
        }

        @Test
        void megabytesAreConvertedToBytes() {
            BeaconCacheConfig.MemoryBoundaries result = resolveMemoryBoundaries("20", "80");

            assertThat(result.lowerBytes()).isEqualTo(20L * MB);
            assertThat(result.upperBytes()).isEqualTo(80L * MB);
            assertThat(logged(Level.INFO, "user-defined", LOWER_MEMORY_BOUNDARY_PROPERTY, "20971520 bytes")).isTrue();
            assertThat(logged(Level.INFO, "user-defined", UPPER_MEMORY_BOUNDARY_PROPERTY, "83886080 bytes")).isTrue();
            assertThat(anyWarnings()).isFalse();
        }

        @Test
        void onlyOneBoundarySetKeepsTheOtherDefault() {
            BeaconCacheConfig.MemoryBoundaries result = resolveMemoryBoundaries("50", null);

            assertThat(result.lowerBytes()).isEqualTo(50L * MB);
            assertThat(result.upperBytes()).isEqualTo(DEFAULT_UPPER_MEMORY_BOUNDARY_BYTES);
            assertThat(logged(Level.INFO, UPPER_MEMORY_BOUNDARY_PROPERTY, "is not set")).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "abc", "80MB", "80m", "-1", "0", "1.5", "8 0", "99999999999999999"})
        void invalidLowerBoundaryLogsBadConfigurationAndUsesDefault(String rawValue) {
            BeaconCacheConfig.MemoryBoundaries result = resolveMemoryBoundaries(rawValue, "90");

            assertThat(result.lowerBytes()).isEqualTo(DEFAULT_LOWER_MEMORY_BOUNDARY_BYTES);
            assertThat(result.upperBytes()).isEqualTo(90L * MB);
            assertThat(logged(Level.WARNING, "Bad configuration detected", LOWER_MEMORY_BOUNDARY_PROPERTY)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "abc", "100MB", "-1", "0", "2.5"})
        void invalidUpperBoundaryLogsBadConfigurationAndUsesDefault(String rawValue) {
            BeaconCacheConfig.MemoryBoundaries result = resolveMemoryBoundaries("20", rawValue);

            assertThat(result.lowerBytes()).isEqualTo(20L * MB);
            assertThat(result.upperBytes()).isEqualTo(DEFAULT_UPPER_MEMORY_BOUNDARY_BYTES);
            assertThat(logged(Level.WARNING, "Bad configuration detected", UPPER_MEMORY_BOUNDARY_PROPERTY)).isTrue();
        }

        @Test
        void lowerEqualToUpperLogsBadConfigurationAndUsesBothDefaults() {
            BeaconCacheConfig.MemoryBoundaries result = resolveMemoryBoundaries("80", "80");

            assertThat(result.lowerBytes()).isEqualTo(DEFAULT_LOWER_MEMORY_BOUNDARY_BYTES);
            assertThat(result.upperBytes()).isEqualTo(DEFAULT_UPPER_MEMORY_BOUNDARY_BYTES);
            assertThat(logged(Level.WARNING, "Bad configuration detected", "must be less than")).isTrue();
        }

        @Test
        void lowerAboveDefaultUpperLogsBadConfigurationAndUsesBothDefaults() {
            BeaconCacheConfig.MemoryBoundaries result = resolveMemoryBoundaries("120", null);

            assertThat(result.lowerBytes()).isEqualTo(DEFAULT_LOWER_MEMORY_BOUNDARY_BYTES);
            assertThat(result.upperBytes()).isEqualTo(DEFAULT_UPPER_MEMORY_BOUNDARY_BYTES);
            assertThat(logged(Level.WARNING, "Bad configuration detected", "must be less than")).isTrue();
        }
    }

    @Nested
    class BuilderConfiguration {

        private DynatraceOpenKitBuilder newBuilder() {
            return new DynatraceOpenKitBuilder("https://tenantid.beaconurl.com/mbeacon", "test", 123456L);
        }

        @Test
        void userDefinedValuesAreAppliedToTheBuilder() {
            OpenKitProperties properties = new OpenKitProperties();
            properties.setBeaconCacheMaxRecordAge("10m");
            properties.setBeaconCacheLowerMemoryBoundary("20");
            properties.setBeaconCacheUpperMemoryBoundary("80");
            DynatraceOpenKitBuilder builder = newBuilder();

            AutoConfigUtils.configureBeaconCache(builder, properties);

            assertThat(builder.getBeaconCacheMaxRecordAge()).isEqualTo(600_000L);
            assertThat(builder.getBeaconCacheLowerMemoryBoundary()).isEqualTo(20L * MB);
            assertThat(builder.getBeaconCacheUpperMemoryBoundary()).isEqualTo(80L * MB);
        }

        @Test
        void unsetValuesApplyTheOpenKitDefaultsToTheBuilder() {
            DynatraceOpenKitBuilder builder = newBuilder();

            AutoConfigUtils.configureBeaconCache(builder, new OpenKitProperties());

            assertThat(builder.getBeaconCacheMaxRecordAge()).isEqualTo(DEFAULT_MAX_RECORD_AGE_MILLIS);
            assertThat(builder.getBeaconCacheLowerMemoryBoundary()).isEqualTo(DEFAULT_LOWER_MEMORY_BOUNDARY_BYTES);
            assertThat(builder.getBeaconCacheUpperMemoryBoundary()).isEqualTo(DEFAULT_UPPER_MEMORY_BOUNDARY_BYTES);
        }
    }
}
