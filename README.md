# Business Event Agent - Spring Boot Starter for Dynatrace Business Events

[![standard-readme compliant](https://img.shields.io/badge/standard--readme-OK-green.svg?style=flat-square)](https://github.com/RichardLitt/standard-readme)

An opinionated configuration of [Dynatrace OpenKit for Java](https://github.com/Dynatrace/openkit-java), packaged as a Spring Boot Starter, that lets any Java service send business events (bizevents) with a single method call.

Add the dependency, set a few properties, inject `BizEventAgent`, and call `sendBizEvent(...)`. OpenKit initialization, session management, beacon delivery and shutdown all happen automatically in the background.

## Table of Contents

- [Background](#background)
  - [Why this library exists](#why-this-library-exists)
  - [What the starter does for you](#what-the-starter-does-for-you)
  - [Why OpenKit instead of calling the Dynatrace API directly](#why-openkit-instead-of-calling-the-dynatrace-api-directly)
- [Install](#install)
- [Usage](#usage)
  - [Configuration](#configuration)
  - [Sending business events](#sending-business-events)
- [Security](#security)
- [Maintainers](#maintainers)
- [Contributing](#contributing)
- [License](#license)

## Background

### Why this library exists

This library was built to send business events from Java services the same way New Relic's Java agent sends [custom events](https://docs.newrelic.com/docs/data-apis/custom-data/custom-events/apm-report-custom-events-attributes/). With New Relic, recording an event is one line anywhere in your code:

```java
NewRelic.getAgent().getInsights().recordCustomEvent("MyCustomEvent", eventAttributes);
```

![New Relic custom event API](./assets/images/newrelic_report_custom_event.png)

Dynatrace OpenKit can send the same kind of data, but its `sendBizEvent()` method lives on the `Session` class. `Session` is built for Real User Monitoring (RUM) and also carries user actions, user tagging, crash reporting and web request tracing. Using it directly means you have to:

1. Configure and build an `OpenKit` instance with a `DynatraceOpenKitBuilder`.
2. Wait for it to initialize.
3. Create a `Session` and keep it alive for as long as the service runs.
4. Remember to shut OpenKit down cleanly so cached events are not lost.

![Dynatrace OpenKit sendBizEvent on Session](./assets/images/dynatrace_send_bizevent.png)

That is a lot of RUM-oriented lifecycle code for a backend service that just wants to emit a business event. This starter hides all of it behind a single `BizEventAgent` bean, so sending a bizevent works like New Relic's `recordCustomEvent`:

```java
bizEventAgent.sendBizEvent("com.companyname.backend.service", attributes);
```

### What the starter does for you

The whole OpenKit lifecycle is tied to the Spring application context:

| Phase | What happens | Where |
|---|---|---|
| **Start-up** | Spring Boot auto-configuration reads the `openkit.bizevents.*` properties, builds a `DynatraceOpenKitBuilder` and initializes OpenKit. You don't write any configuration code. | `OpenKitAutoConfiguration`, `AutoConfigUtils` |
| **Session creation** | Once OpenKit is initialized, a single long-lived `Session` is created and wrapped in a `BizEventAgent` singleton bean. | `OpenKitAutoConfiguration#injectBizEventAgent` |
| **Runtime** | Your code calls `bizEventAgent.sendBizEvent(...)`. The call returns right away; OpenKit caches the event and its background threads send it. If the Dynatrace backend's configuration calls for a session split, OpenKit does it transparently, so you never deal with sessions. | `BizEventAgent` |
| **Shutdown** | When the application context closes, Spring calls `BizEventAgent#close()`, which calls `OpenKit#shutdown()`. OpenKit flushes all unsent data and then stops its background threads. | `@Bean(destroyMethod = "close")` |

![Sessions and event data](./assets/images/sessions_event_data.svg)

The configuration is opinionated. Its only purpose is exporting bizevents:

- **RUM data collection and crash reporting are turned off** (`DataCollectionLevel.OFF`, `CrashReportingLevel.OFF`), so the session doesn't produce user actions, user tags or crash reports.
- **Device metadata is filled in for you**: the operating system is detected from the JVM, `applicationName` is sent as the OpenKit model ID, and `manufacturer` as the device manufacturer.
- **A custom `User-Agent` header** (`CUSTOM_APP_ID<applicationId>`) is added to every beacon request by `BeaconRequestInterceptor`, so this traffic is easy to spot in Dynatrace.
- **The `BizEventExporter` interface** (`sendBizEvent(String, Map<String, JSONValue>)`) is the only API you code against.

### Why OpenKit instead of calling the Dynatrace API directly

You could POST bizevents straight to the Dynatrace Business Events ingest API. But then your service owns delivery: blocking HTTP calls on request threads, retries, buffering when Dynatrace is unreachable, memory limits, and flushing on shutdown. OpenKit already solves these problems with its [asynchronous beacon forwarding](https://github.com/Dynatrace/openkit-java/blob/main/docs/internals.md):

- **Dedicated beacon-sending thread.** All communication with Dynatrace runs on a background thread that starts when OpenKit initializes. `sendBizEvent()` never blocks your business logic on network I/O.
- **Server-driven state machine.** The sender goes through `Init → CaptureOn / CaptureOff → FlushSessions → Terminal`. The backend's status responses control send intervals and session limits, and can turn capturing off and back on. OpenKit follows the server's routing and configuration instead of hard-coded client values.
- **Automatic retries with backoff.** The initial status request is retried up to 5 times (6 attempts in total) with increasing delays. Data sends in the CaptureOn state are retried three times with increasing delays to avoid data loss.
- **Beacon cache with eviction strategies.** Events are cached per session (even before initialization finishes) in a thread-safe cache. A separate background eviction thread enforces two limits:
  - *Time-based eviction*: records older than 45 minutes (the default) are dropped.
  - *Space-based eviction*: when the cache goes over 100 MB, the oldest data is evicted until it is back under 80 MB.

  If Dynatrace is unreachable for a while, your service's memory stays bounded.
- **Graceful flush on shutdown.** A shutdown during CaptureOn or CaptureOff goes through `FlushSessions`, which sends all remaining data before the sending thread stops. Because this starter ties `shutdown()` to the Spring context, events recorded right before a deployment or restart still get delivered.

In short, the whole delivery pipeline runs in background threads that start and stop with your Spring application, and you never write any of it.

## Install

Requirements: Java 17+ and Spring Boot 3.x (built against Spring Boot 3.5 and `openkit-java` 3.3.0).

**Option 1: publish to your local Maven repository (recommended)**

```sh
git clone https://github.com/nick-montana/business-event-agent.git
cd business-event-agent
./gradlew publishToMavenLocal
```

Then add it to your service:

```groovy
// build.gradle
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'io.github.nick-montana:bizevent-agent:0.0.4-SNAPSHOT'
}
```

With this option, `openkit-java` comes in as a transitive `api` dependency, so the OpenKit JSON types (`JSONValue`, `JSONStringValue`, …) are available on your compile classpath.

**Option 2: reference the built JAR directly**

```sh
./gradlew build   # produces build/libs/bizevent-agent-0.0.4-SNAPSHOT-plain.jar
```

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation files('{PATH_TO_JAR}/bizevent-agent-0.0.4-SNAPSHOT-plain.jar')
    // a file dependency carries no transitive dependencies, so add OpenKit yourself
    implementation 'com.dynatrace.openkit:openkit-java:3.3.0'
}
```

The auto-configuration is registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, so you don't need `@Import` or `@ComponentScan`.

## Usage

### Configuration

Set these properties in your service's `application.properties` (or the YAML equivalent):

```properties
# Turn the starter on or off (default: true)
openkit.bizevents.enabled=true

# Required: OpenKit / Dynatrace connection
openkit.bizevents.endpointUrl=https://{ENVIRONMENT_ACTIVE_GATE}:9999/mbeacon/{TENANT_ID}
openkit.bizevents.applicationId={CUSTOM_APPLICATION_ID}
openkit.bizevents.deviceId={NUMERIC_DEVICE_ID}

# Optional
openkit.bizevents.applicationName={APPLICATION_NAME}
openkit.bizevents.manufacturer={TEAM_NAME}
openkit.bizevents.waitForInitCompletion=true
openkit.bizevents.disableSslVerification=false
openkit.bizevents.logLevel=INFO
```

| Property | Required | Default | Description |
|---|---|---|---|
| `enabled` | no | `true` | If `false`, no OpenKit instance or `BizEventAgent` bean is created. |
| `endpointUrl` | **yes** | — | Beacon endpoint of your Dynatrace environment or ActiveGate. |
| `applicationId` | **yes** | — | ID of the custom application in Dynatrace that receives the beacons. Also used in the `User-Agent` header. |
| `deviceId` | **yes** | — | Numeric (`long`) device identifier passed to OpenKit. |
| `applicationName` | no | — | Sent to OpenKit as the model ID. |
| `manufacturer` | no | — | Sent to OpenKit as the device manufacturer (for example, your team name). |
| `waitForInitCompletion` | no | `false` | If `true`, start-up blocks for up to 60 seconds until OpenKit finishes initializing. If `false`, start-up continues right away and events are cached until initialization completes. |
| `disableSslVerification` | no | `false` | Trusts any certificate (OpenKit's `SSLBlindTrustManager`). Use only for non-production environments with self-signed certificates. |
| `logLevel` | no | OpenKit default | OpenKit log level: `DEBUG`, `INFO`, `WARN` or `ERROR`. |

### Sending business events

Inject `BizEventAgent` (or the `BizEventExporter` interface) wherever you need it:

```java
import com.dynatrace.openkit.util.json.objects.JSONNumberValue;
import com.dynatrace.openkit.util.json.objects.JSONStringValue;
import com.dynatrace.openkit.util.json.objects.JSONValue;
import io.github.nick_montana.bizevents.autoconfigure.BizEventAgent;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class BookingService {

    private final BizEventAgent bizEventAgent;

    public BookingService(BizEventAgent bizEventAgent) {
        this.bizEventAgent = bizEventAgent;
    }

    public void confirmBooking() {
        // OpenKit requires attribute values to be JSONValue instances
        Map<String, JSONValue> attributes = new HashMap<>();
        attributes.put("event.name", JSONStringValue.fromString("Confirmed Booking"));
        attributes.put("product", JSONStringValue.fromString("Danube Anna Hotel"));
        attributes.put("amount", JSONNumberValue.fromDouble(358.35));
        attributes.put("currency", JSONStringValue.fromString("USD"));
        attributes.put("reviewScore", JSONNumberValue.fromDouble(4.8));

        // returns right away: the event is cached and sent by OpenKit's background thread
        bizEventAgent.sendBizEvent("com.companyname.backend.booking", attributes);
    }
}
```

The first argument is the event type (`event.type` in Dynatrace). Like New Relic event type names, keep the set of event types small and stable, and don't build them dynamically.

You never create, end or split a session yourself, and you never call `OpenKit#shutdown()`. The starter does all of that.

## Security

Beacons are sent to the Dynatrace endpoint (directly or through an ActiveGate) over HTTPS with TLS. Certificate verification is on by default. Only set `openkit.bizevents.disableSslVerification=true` in environments where you accept the risk of trusting any certificate.

## Maintainers

[@nick-montana](https://github.com/nick-montana)

## Contributing

See [the contributing file](contributing.md)!

Small note: If editing the README, please conform to the
[standard-readme](https://github.com/RichardLitt/standard-readme) specification.

## License

MIT © 2026 nick-montana
