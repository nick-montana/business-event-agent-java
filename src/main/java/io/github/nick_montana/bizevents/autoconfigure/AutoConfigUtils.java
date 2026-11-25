package io.github.nick_montana.bizevents.autoconfigure;

import com.dynatrace.openkit.CrashReportingLevel;
import com.dynatrace.openkit.DataCollectionLevel;
import com.dynatrace.openkit.DynatraceOpenKitBuilder;
import com.dynatrace.openkit.api.LogLevel;
import com.dynatrace.openkit.api.OpenKitConstants;

import java.security.InvalidParameterException;
import java.text.MessageFormat;

/* Contains utility methods for @OpenKitAutConfiguration
*/
public interface AutoConfigUtils {

    DataCollectionLevel bizeventAgentCollectionLevel = DataCollectionLevel.OFF;

    enum OS {
        WINDOWS, LINUX, MAC, SOLARIS, UNKOWN
    };// Operating systems.


    static DynatraceOpenKitBuilder openKitBuilder(OpenKitProperties properties) {

        // check that the three required configurations are present
        // throws exception if any are missing
        validateRequiredProperty("applicationId",  properties.getApplicationId());
        validateRequiredProperty("deviceId", properties.getDeviceId());
        validateRequiredProperty("endpointUrl", properties.getEndpointUrl());

        DynatraceOpenKitBuilder openKitBuilder = new DynatraceOpenKitBuilder(
                properties.getEndpointUrl(),
                properties.getApplicationId(),
                properties.getDeviceId()
        );

        // only configure with a logger if its level is explicitly set
        if(properties.getLogLevel() != null) {
            openKitBuilder.withLogLevel(setOpenKitLogger(properties.getLogLevel()));
        }
        // only set the manufacturer if it is explicitly configured
        if (properties.getManufacturer() != null) {
            openKitBuilder.withManufacturer(properties.getManufacturer());
        }
        openKitBuilder.withModelID(properties.getApplicationName());
        openKitBuilder.withOperatingSystem(getOS().toString());
        openKitBuilder.withApplicationVersion(openKitBuilder.getApplicationVersion());
        openKitBuilder.withDataCollectionLevel(bizeventAgentCollectionLevel);
        openKitBuilder.withCrashReportingLevel(CrashReportingLevel.OFF);
        configureBeaconCache(openKitBuilder, properties);
        return openKitBuilder;
    };

    /* Applies the beacon cache eviction settings
     *  - user-defined values are validated and converted (duration -> milliseconds, megabytes -> bytes)
     *  - unset or invalid values fall back to the OpenKit defaults, and BeaconCacheConfig logs which one was used
     * */
    static void configureBeaconCache(DynatraceOpenKitBuilder openKitBuilder, OpenKitProperties properties) {
        long maxRecordAgeMillis = BeaconCacheConfig.resolveMaxRecordAge(properties.getBeaconCacheMaxRecordAge());
        BeaconCacheConfig.MemoryBoundaries boundaries = BeaconCacheConfig.resolveMemoryBoundaries(
                properties.getBeaconCacheLowerMemoryBoundary(),
                properties.getBeaconCacheUpperMemoryBoundary()
        );

        openKitBuilder.withBeaconCacheMaxRecordAge(maxRecordAgeMillis);
        openKitBuilder.withBeaconCacheLowerMemoryBoundary(boundaries.lowerBytes());
        openKitBuilder.withBeaconCacheUpperMemoryBoundary(boundaries.upperBytes());
    }

    /* Throws Exception if implementation is missing required properties
     *  - this prevents failure during OpenKit initialization
     * */
    private static void validateRequiredProperty(String propertyKey, Object propertyValue) {
        if(propertyValue == null) {
            throw new InvalidParameterException(MessageFormat.format("Missing value for required property {0}!", propertyKey));
        }
    }


    private static OS getOS() {
        String operSys = System.getProperty("os.name").toLowerCase();

        if (operSys.contains("win")) {
            return OS.WINDOWS;
        } else if (operSys.contains("nix") || operSys.contains("nux")
                || operSys.contains("aix")) {
            return OS.LINUX;
        } else if (operSys.contains("mac")) {
            return OS.MAC;
        } else if (operSys.contains("sunos")) {
            return OS.SOLARIS;
        }
        return OS.UNKOWN;
    }


    /* Properly sets OpenKit compatible log-level from a string representation
    * */
    private static LogLevel setOpenKitLogger(String logLevelString) {
        return LogLevel.valueOf(logLevelString);
    };
}
