package io.github.nick_montana.bizevents.autoconfigure;

import com.dynatrace.openkit.api.OpenKitConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * OpenKitAutoConfiguration properties
 *
 * These properties are adapted to match the primary focus which is sending bizevents.
 * To-Do: elaborate more before showing the following:
 *
 * ApplicationName = ModelId
 * Namespace = Manufacturer
 *
 * The following two OpenKit Configurations are intentionally left out since they are unrelated to bizevent funcitonality
 *  1. DataCollectionLevel
 *  2. CrashReportingLevel
 *
 *
 * <a href="http://json.org/">OpenKit GitHub Documentation</a>.
 */
@ConfigurationProperties(prefix = "openkit.bizevents")
public class OpenKitProperties {


    private String applicationId;

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(long deviceId) {
        this.deviceId = deviceId;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationVersion() {
        return applicationVersion;
    }

    public void setApplicationVersion(String applicationVersion) {
        this.applicationVersion = applicationVersion;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public void setOperatingSystem(String operatingSystem) {
        this.operatingSystem = operatingSystem;
    }

    public boolean isWaitForInitCompletion() {
        return waitForInitCompletion;
    }

    public void setWaitForInitCompletion(boolean waitForInitCompletion) {
        this.waitForInitCompletion = waitForInitCompletion;
    }

    private String endpointUrl;

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    private String logLevel;

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    private String manufacturer;
    private String operatingSystem;
    private boolean waitForInitCompletion;


    public boolean isDisableSSLVerification() {
        return disableSSLVerification;
    }

    public void setDisableSSLVerification(boolean disableSSLVerification) {
        this.disableSSLVerification = disableSSLVerification;
    }

    private boolean disableSSLVerification = false;

    private String applicationName;
    private String applicationVersion;

    /*
     * Beacon cache settings are bound as raw strings so that BeaconCacheConfig can validate them,
     * log bad configuration and fall back to the OpenKit defaults instead of failing start-up.
     * null means the property is not set.
     *
     * beaconCacheMaxRecordAge: duration such as 10m, 60s, 500ms or a plain number of milliseconds
     * beaconCacheLowerMemoryBoundary / beaconCacheUpperMemoryBoundary: whole number of megabytes, e.g. 80
     */
    private String beaconCacheMaxRecordAge;
    private String beaconCacheLowerMemoryBoundary;
    private String beaconCacheUpperMemoryBoundary;

    public String getBeaconCacheMaxRecordAge() {
        return beaconCacheMaxRecordAge;
    }

    public void setBeaconCacheMaxRecordAge(String beaconCacheMaxRecordAge) {
        this.beaconCacheMaxRecordAge = beaconCacheMaxRecordAge;
    }

    public String getBeaconCacheLowerMemoryBoundary() {
        return beaconCacheLowerMemoryBoundary;
    }

    public void setBeaconCacheLowerMemoryBoundary(String beaconCacheLowerMemoryBoundary) {
        this.beaconCacheLowerMemoryBoundary = beaconCacheLowerMemoryBoundary;
    }

    public String getBeaconCacheUpperMemoryBoundary() {
        return beaconCacheUpperMemoryBoundary;
    }

    public void setBeaconCacheUpperMemoryBoundary(String beaconCacheUpperMemoryBoundary) {
        this.beaconCacheUpperMemoryBoundary = beaconCacheUpperMemoryBoundary;
    }

    private long deviceId;

}
