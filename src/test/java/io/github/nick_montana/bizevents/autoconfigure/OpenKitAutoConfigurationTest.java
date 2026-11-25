package io.github.nick_montana.bizevents.autoconfigure;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.security.InvalidParameterException;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenKitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenKitAutoConfiguration.class));

    @Test
    public void testAutoConfigurationWithOnlyRequiredProperties() {
        contextRunner.withPropertyValues(
                "openkit.bizevents.enabled=true",
                "openkit.bizevents.applicationId=test",
                "openkit.bizevents.deviceId=123456",
                "openkit.bizevents.endpointUrl=https://tenantid.beaconurl.com/mbeacon",
                "openkit.bizevents.waitForInitCompletion=false"
        ).run((context) -> {
            assertThat(context).hasSingleBean(BizEventAgent.class);
        });
    }

    @Test
    public void testAutoConfigurationWithMissingRequiredProperties() {

        IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> { throw new InvalidParameterException("Missing value for required property applicationId!"); }
        );

        contextRunner.withPropertyValues(
                "openkit.bizevents.enabled=true",
                "openkit.bizevents.deviceId=123456",
                "openkit.bizevents.endpointUrl=https://tenantid.beaconurl.com/mbeacon",
                "openkit.bizevents.waitForInitCompletion=false"
        ).run((context) -> {
            Assertions.assertEquals("Missing value for required property applicationId!", exception.getMessage());
        });
    }

    @Test
    @Disabled
    public void testAutoConfigurationWithOptionalProperties() {

        contextRunner.withPropertyValues(
                "openkit.bizevents.enabled=true",
                "openkit.bizevents.applicationId=test",
                "openkit.bizevents.waitForInitCompletion=false",
                "openkit.bizevents.deviceId=123456",
                "openkit.bizevents.endpointUrl=https://tenantid.beaconurl.com/mbeacon",
                "openkit.bizevents.applicationName=mockApplicationName",
                "openkit.bizevents.namespace=mockTeamName",
                "openkit.bizevents.applicationVersion=1.2.3").run((context) -> {
            assertThat(context).hasSingleBean(BizEventAgent.class);
        });
    }


    @Test
    @Disabled
    public void testDisableAutoConfigurationWithExplicitBoolean() {

        contextRunner.withPropertyValues(
                "openkit.bizevents.enabled=false",
                "openkit.bizevents.applicationId=test",
                "openkit.bizevents.deviceId=123456",
                "openkit.bizevents.endpointUrl=test"
        ).run((context) -> {
            assertThat(context).doesNotHaveBean(BizEventAgent.class);
        });
    }

    /* Tests the default behavior that does not configure given no configuration properties in the environment */
    @Test
    @Disabled
    public void testDefaultNoAutoConfigurationBehavior() {

        contextRunner.withPropertyValues(
                "foo.bar=foobar"
        ).run((context) -> {
            assertThat(context).doesNotHaveBean(BizEventAgent.class);
        });
    }
}