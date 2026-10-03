# Android Background Service

Apache-2.0 framework for Android background execution. It contains no networking,
Git, authentication or message model. Android 8 / API 26+, compile SDK 36, JDK 21
for the build; published JVM bytecode targets Java 11.

## Modules

- `core`: execution state and host effects (`ACTIVE`, `FGS`, `WORKER`, `DISABLED`),
  process generations, execution leases and configurable polling cadence.
- `android-runtime`: `ManagedForegroundService`, foreground-to-worker handoff,
  debounced activity lifecycle, WorkManager scheduling, deadline alarms,
  capability checks and bounded persistent diagnostic history.
- `diagnostics-ui`: optional non-exported Activity showing capabilities and history;
  report can be copied to the clipboard.
- `sample`: executable JVM host-contract example (`./gradlew :sample:run`).

## Local development

Set `ANDROID_HOME`, or create an ignored `local.properties` containing `sdk.dir`.

```sh
./gradlew verify :android-runtime:assembleRelease :diagnostics-ui:assembleRelease
./gradlew :sample:run
```

Consumers add to `settings.gradle.kts`:

```kotlin
includeBuild(providers.gradleProperty("backgroundServiceDir")
    .orElse("../android-background-service").get())
```

And dependencies:

```kotlin
implementation("io.github.backgroundservice:android-runtime:0.1.0")
implementation("io.github.backgroundservice:diagnostics-ui:0.1.0") // optional
```

The application and library share one WorkManager instance. The library does not
replace its initializer, factory, Application class or network configuration.
`publishToMavenLocal` is available for evaluating artifacts. Configure an artifact
repository and pin a released version before using binary dependencies in CI.
The initial integrations use a source checkout in CI instead.

## Host responsibilities

`ExecutionCoordinator` accepts lifecycle/service facts, persists each state through
`StateStore`, and invokes `ExecutionHost`. Durable storage is supplied by the host.
`processStarted` invalidates previous process generations and restores worker
fallback. Pass the captured generation with asynchronous service callbacks.
`ExecutionLease` is process-local; use the same instance for all executors. Release
it only once the old operation has actually stopped. Cross-process coordination,
if used, requires an application-provided durable lock.

Subclass `ManagedForegroundService` and supply the notification, lawful service
type, enabled flag, workload and cleanup. Report observed service status to your
coordinator. `BackgroundHandoff` supports migrating an existing scheduler while
preserving its work names, preferences and alarm identities. Call `serviceRunning`
after `startForeground` succeeds; call `serviceStopped` on failure/teardown.
Existing watchdog/health-check workers remain application adapters: service health
must not be inferred from a worker intentionally stopped while FGS runs.

`BackgroundWork.periodic` uses stable unique work with UPDATE. `once` appends a
local reconciliation request without cancelling an in-flight reconciliation.
Work implementations and cancellation belong to the host. KotoChat retains its
socket cancellation registry and work identities. Orgi retains repository locking.

Use `BackgroundCapabilities.read` after returning from system settings; do not
infer a grant from an Activity result. Notification visibility, channel state,
battery exemption and exact alarm access are independent. Request runtime
permissions from the hosting Activity in response to user intent.

Open `BackgroundDiagnosticsActivity` with the `namespace` Intent extra. Use the
same namespace for `BackgroundDiagnostics.record`. History retains at most 200
entries and is stored locally. Do not record task contents, credentials or URLs.

## Android limits

FGS support is optional. Applications declare their own service type and
permissions; this library declares no messaging or battery-exemption permission.
KotoChat provides remote-messaging work. Local calendar reminders do not qualify
as remote messaging. Orgi uses WorkManager for Git and AlarmManager for deadlines.

WorkManager delays and inexact alarms are best effort. Exact alarms require access
on recent Android versions and are still subject to idle quotas. A foreground
service does not itself hold a CPU wake lock. The idle lease uses uptime; deep
sleep can postpone expiry. Force-stop prevents normal recovery until the user
opens the app. `onDestroy` is not guaranteed on process death: hosts need durable
work and boot reconciliation in addition to service callbacks.

## Migration / validation

KotoChat preserves its worker class names, notification channel/ID, settings and
transport; service lifecycle, handoff, cadence, capability reads and persisted
history use this library. App-scoped chat visibility and transport source selection
remain in its domain. Orgi's sync keeps its periodic work identity and connectivity
constraint; reminders are a separate offline workload.

Automated coverage includes handoff ordering, stale process callbacks, execution
leases, service cleanup, permission-independent delivery and bounded history.
Device checks still needed for OEM behavior: Doze, force-stop, reboot, permission
revocation and audible sound quality. No Android device was attached during the
initial implementation.
