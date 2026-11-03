# rootiest-status-android documentation

Native Android app for the Rootiest services status page.

## Policy: docs are the single source of truth

Everything about this project is documented here. A change is not done until the docs
under this directory describe it. Generated documents are never edited by hand: edit
the source and regenerate.

## What it does

The app shows the same snapshot as [status.rootiest.dev](https://status.rootiest.dev):

- a headline card in the overall level's colour, centred and fixed above the list (green ok, yellow warn, red bad), with
  "Failover active" while traffic is served by the standby edge;
- the Servers section (state, vitals, note per host), only when the API includes it:
  after signing in, for staff and the groups the catalog lets see servers;
- every group of services: state dot, uptime text and the per-day uptime strip, and
  for a service that publishes a backup report the hub's backup line ("Last backup 3 h
  ago (...)"), grey when unknown and yellow when the backup is older than the hub
  allows (`Backup`, rootiest-hub's `BackupView`; also on the detail screen). Its time
  follows Settings > Times, built from the hub's `At` and `Detail`; a hub that sends
  only `Text` is shown as it is.

Section titles (Servers, then each group) stick below the headline while their rows
scroll, until the next section's title pushes them off. The headline is fixed above
the list rather than a sticky header itself, because every sticky header in a Compose
list sticks to the same top edge and the next one would push it away too.

Tap a service to open its detail screen:

- a summary card, fixed above the list: state (and "planned" when a planned window expects it down), uptime
  text, the service's detail line, its hostname (a link to the site, except for services
  probed by TCP connect, whose hostname is only a name) and the uptime strip;
- **Recent changes**: the snapshot's events for that service (`Kind` `service`, `Subject`
  equal to its name), newest first, with times as the Times setting shows them;
- **Daily uptime (UTC days)**: one row per day, newest first, from the strip's cells.
  "Recent changes" sticks below the summary card until this title pushes it off. Days are
  UTC days, as rootiest-hub counts them, so tomorrow's date can appear in the evening. Days before
  the history began (`Class` `none`) are left out.

Pull down to refresh on either screen. After a failed refresh the last good snapshot
stays on screen with an error banner above it. Back (button or gesture) returns to the
list.

## Signing in

**Sign in** (in the account menu, top right) signs in with RootiestID, the Rootiest
single sign-on, through rootiest-hub, which then shows what that account may see, as on
the web page (staff see everything, including the Servers section). Without an account
the app shows the public view. The flow is rootiest-hub's app sign-in:

1. The app makes a random proof (32 bytes, base64url), saves it, and opens
   `https://status.rootiest.dev/app/login?challenge=<base64url SHA-256 of the proof>`
   in a Custom Tab.
2. After RootiestID, the hub redirects to `dev.rootiest.status:/signed-in?token=...`,
   which `MainActivity` receives (`singleTop`; the intent filter matches the scheme, and
   the activity checks the path).
3. The app keeps the token and proof in its private storage (`AuthStore`, preferences
   file `auth`) and sends `Authorization: Bearer <token>` and `X-Rootiest-Proof: <proof>`
   with every request: the app, the widget and the background job. The hub counts the
   token only with that proof, so a token caught on its way back is useless.

The top bar shows the RootiestID display name; tapping it opens the account menu, with
**Settings** and **Sign out**, which deletes the token.
The app reads the name and expiry from the token (signed by the hub, only read here). A
sign-in lasts 14 days, the hub's `STATUS_SESSION_TTL`; once it expires, or after the
hub's `signout-all`, the app shows the public view again and the menu offers **Sign in**
(after `signout-all` the name stays until the token's own expiry, but the view is
public). Notifications then include servers too; a server first seen after signing in
is not reported as a change.

## Account menu and settings

The top bar's right side opens the account menu: under the display name when signed in,
under an account icon when signed out. Either way it has **Settings**, then **Sign out**
or **Sign in**, so settings are reachable without an account.

**Settings** is its own screen (back returns to the list), in sections: Notifications,
Instant alerts and Background check (see [Notifications and the background job](#notifications-and-the-background-job)), then Appearance,
Service list, Times and About:

- **Appearance**: **System default** (the default) follows the phone's dark theme;
  **Light** and **Dark** always use that one. Dark is Catppuccin Mocha, light is
  Catppuccin Latte, the status page's light and dark palettes. The choice is saved in
  the preferences file `settings` (`SettingsStore`). On Android 12 and later the app
  also gives it to the system (`UiModeManager.setApplicationNightMode`), so the window
  shown before the app draws matches it too; on Android 10 and 11 that brief window
  follows the phone. The status and navigation bars' icons follow the choice. The
  home-screen widget always stays dark.
- **Service list**: **As on the status page** (the default); **Problems first**, which
  keeps every section in place and moves its down services (or unhealthy servers) to
  the top, in their order; or **Problems only**, which hides what is up or healthy and
  the sections left empty, and says "Nothing is down" when that leaves nothing. A
  standby copy is not a problem, as for the widget. Saved as `serviceList` in
  `settings`; `arranged` in `data/Problems.kt` does it, and only the list uses it (the
  widget, detail screens and notifications see every service).
- **Times**: **Relative** ("3 h ago", "2 days ago"; the clock once it is a week old) or
  **Clock time** (the default), and for the clock:
  - **Format**: **Default** (the locale's date, "Nov 2, 2026, 4:05 PM"), **ISO**
    ("2026-11-02 16:05") or **Custom**, a strftime pattern typed in Settings (default
    `%Y-%m-%d %H:%M`) with a live example. It knows `%Y %y %m %d %e %b %B %a %A %H %I %l
    %M %S %p %Z %z %%` (Settings lists what each gives); anything else, an unknown field
    too, is kept as written. A custom pattern is used as written everywhere, the widget
    included: it sets its own hours and adds no zone name unless it has `%Z`.
  - **Hours**: **Phone setting** (the default, the phone's "Use 24-hour format",
    read each time the setting is loaded), **12-hour** or **24-hour**.
  - **Time zone**: **Local time** (the default, the phone's zone) or **UTC** (with
    " UTC" after Default and ISO times).

  It covers the detail screen's recent changes and Settings' last check and last push.
  The widget's "Updated" time follows format, hours and zone but is always a clock time
  (the time of day, or the whole custom pattern), as a relative one would go stale
  between redraws; Settings says so while Relative is chosen. A change redraws the
  widgets a second after the last one (so typing a pattern does not redraw on every
  key). The examples under the choices are the current time less three hours. Daily
  uptime stays in UTC days, as the hub counts them. `TimeFormat` and `strftime` in
  `data/Times.kt` do the formatting; saved as `time-style`, `time-date`,
  `time-pattern`, `time-hours` and `time-utc` in `settings`, and provided to every
  screen as `LocalTimeFormat`, the backup line's time included.
- **About**: the version (name and code), then links that open in the browser: the
  status page, the source code and **Report a problem** (a new issue on Gitea). Last,
  **Open-source licenses** (`ui/LicensesScreen.kt`) lists the libraries the app is
  built with and their licenses, tap one for its text. The AboutLibraries Gradle plugin
  writes that list at build time from the release dependencies, as
  `res/raw/aboutlibraries.json`, so it is never out of date.

## Home-screen widget

The **Rootiest Status** widget shows the overall level's colour and the headline. At
two cells tall or more it also lists what is wrong (up to four rows, then "and N more")
and when the snapshot was fetched. Tapping it opens the app.

- What is wrong (`data/Problems.kt`): servers whose state is not `healthy`, then
  services whose state is not `up` or `standby` (a standby copy is down on purpose).
  "(planned)" marks one a planned window expects.
- Each widget update fetches `/api/status` and saves the answer in the app's files
  (`last-status.json`, `data/StatusCache.kt`). When the fetch fails the widget shows
  the saved snapshot, with "Offline · as of <time>" in yellow (the time in the zone the
  Times setting chooses).
- The background job (below) redraws every widget each time it runs (every 15 minutes by default). The widget's own
  `updatePeriodMillis` is 0, since the system's minimum is 30 minutes.
- The app's own refresh does not redraw the widget yet.

## Notifications and the background job

`RefreshWorker` (WorkManager) runs every 15 minutes by default, the shortest period
WorkManager allows, or at the interval chosen in Settings (below), and only with a network connection, and at once when a push arrives (below). Each run fetches the status, posts a
notification for each change, saves the states and the snapshot, and redraws the
widgets. It is scheduled on every app start (and when a widget is added) and keeps
running without a widget, since it also sends the notifications. Android may run it
later than its interval when the phone is idle (Doze), so a change can take longer
to arrive; ntfy remains the fast path.

- A change is a service or server whose state differs from the previous run
  (`data/Changes.kt`; states saved in `last-states.json`). Nothing is posted on the
  first run, for a name not seen before, or for one a planned window expects (as
  rootiest-hub holds its own alerts for those). Nothing is posted for a muted name
  either (below).
- Notifications go to one of two channels (`Notifier.kt`), so the phone's own settings
  can treat them apart: "Outages" (high importance: sound and a heads-up banner), for
  "Gitea is down / was up" and any other move away from green, and "Recoveries" (low:
  silent, in the shade only), for "Gitea is back up / was down". Each name has one
  notification id across both, so a recovery replaces the down notification. Tapping
  one opens the app. The channels are created on every app start, so they show in the
  phone's settings before the first change; the single "Service changes" channel of
  1.3.0 and earlier is deleted then.
- On Android 13 and later the app asks for notification permission on first launch.
  Refusing only turns notifications off.

**Settings.** The settings screen (`ui/SettingsScreen.kt`) reads the phone's state each
time it shows, so it is current after a trip to the phone's settings:

- Notifications: when they are off (permission refused, or the app switched off), one
  row opens the app's notification page in the phone's settings, where Android 13+ also
  grants the permission. Otherwise a row per channel, saying "Off" when that channel is
  off, opens that channel's page; "Services to notify about" (below); and "Send a test
  notification" posts a sample outage on the Outages channel.
- **Services to notify about** (`ui/WatchListScreen.kt`) lists every server (when the
  viewer may see them) and every service by group, each with a switch. Turning one off
  mutes it: the background job drops its changes (`unmuted` in `data/Changes.kt`). The
  app saves the muted names, not the watched ones, in the preferences file `settings`
  (`SettingsStore.muted`), so a service added later is notified about until muted. The
  list comes from the snapshot on screen; the Settings row says "All" or "All but N
  muted".
- Instant alerts: whether push is on (and through which distributor app, with when the
  last push arrived), installed but without an address, or off with no distributor.
- Background check: how often the job runs on its own, every 15 minutes (the default),
  30 minutes or hour, or **Only when a push arrives**, which cancels the periodic job
  (`SettingsStore.refreshInterval`, preferences file `settings`). `RefreshWorker.schedule`
  applies it on every app start and on each change, with WorkManager's `UPDATE` policy,
  which changes a scheduled job's period without restarting its clock. Push only is
  marked in yellow while push is not on, since nothing would then run the job: no
  notifications and a stale widget. With push on, the widget then updates only after an
  alert. Below the choice, when the job last ran (the time `last-states.json` was
  written, which only the job does), and the phone's battery setting for the app
  (`Battery.kt`), the only part of "App battery usage" the system tells apps:
  **Restricted** (`isBackgroundRestricted`; in yellow, as no check or alert runs then)
  and **Unrestricted** (`isIgnoringBatteryOptimizations`) open the app's info page, where
  App battery usage changes it; **Optimized**, the default, opens the system's dialog to
  let the app run unrestricted (permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; lint's
  `BatteryLife` warning, a Play Store policy check, is suppressed, as the app is not on
  Play).

**Push.** The app uses UnifiedPush with the phone's distributor, the ntfy app. On start
it asks for the current or default distributor and registers; the distributor hands it a
push address, an `up...` topic on the ntfy app's default server, which must be
`https://ntfy.rootiest.dev` (rootiest-hub refuses addresses anywhere else). The app saves
it and sends it to the hub (`POST /app/push`) on every run of the job, so the host
serving the page learns it soon after a failover. After each alert the hub posts
`refresh` to every address; the ntfy app hands it to `StatusPushService`, which queues
the job at once (expedited on Android 12+). The message carries no data and is not
encrypted (the connector logs "Could not decrypt message, trying with plain text"), so
each run fetches what its viewer may see. Runs take turns (a lock), so a push arriving
during the periodic run cannot notify a change twice. Without a distributor, or with the
ntfy app on another server, there is no push and the periodic job carries on alone.

To test the phone's side without an alert, post to the address (logged as
`StatusPush: ... for <address>` when the hub refuses it, or found in the ntfy app's
subscriptions): `curl -d refresh '<address>'`, then watch
`adb logcat | grep -E 'StatusPush|RefreshWorker'`.

Testing on a device: a periodic job cannot be forced early (WorkManager reschedules a
run that comes "before schedule"). With a debug build, write a state into
`files/last-states.json` (`adb shell run-as dev.rootiest.status ...`) and wait for the
next run.

## Data source

`GET https://status.rootiest.dev/api/status`, served by rootiest-hub (the status page's
server, whose repository is not public). The body is rootiest-hub's `View` struct
encoded as JSON, so field names are Go's exported names
(`Level`, `Groups`, `Services`, `Bar`...). It depends on the viewer: a signed-out
request gets only what is public.

`app/.../data/StatusModels.kt` declares only the fields the app shows and the parser
ignores the rest, so new server fields never break the app. A renamed or removed field
does: when rootiest-hub changes `View`, update the models and refresh the test fixture.

States and their colours (`ui/theme/Theme.kt`, `toneOf`):

| Colour | Levels and states |
|---|---|
| Green | `ok`, `up`, `healthy` |
| Yellow | `warn`, `standby`, `degraded` |
| Red | `bad`, `down`, `unhealthy` |
| Grey | anything else; `none` (no data) in the uptime strip |

## Architecture

Single module `:app`, package `dev.rootiest.status`, Kotlin with Jetpack Compose and
Material 3.

| File | Role |
|---|---|
| `data/StatusModels.kt` | `@Serializable` data classes for the API's JSON |
| `data/StatusApi.kt` | OkHttp GET plus kotlinx.serialization parse, on `Dispatchers.IO` |
| `data/Lookups.kt` | plain functions over a snapshot: find a service, its events, a day's summary text |
| `StatusViewModel.kt` | holds `StatusUiState` (last view, loading, error), the appearance, service list, times and interval settings and the muted names as `StateFlow`s; fetches on start and on `refresh()` |
| `Routes.kt` | Navigation 3 keys: `Home`, `ServiceDetail(group, name)`, `Settings`, `WatchList`, `Licenses` |
| `ui/App.kt` | the app root: one `StatusViewModel` above the navigation, the back stack and `NavDisplay` |
| `ui/StatusScreen.kt` | the list: top bar, pull-to-refresh, headline, servers, groups, tappable services |
| `ui/ServiceScreen.kt` | a service's detail: summary, recent changes, daily uptime |
| `ui/SettingsScreen.kt` | settings: notifications, push status, the background check, appearance, service list, times, about |
| `ui/WatchListScreen.kt` | the services and servers to notify about, one switch each |
| `ui/LicensesScreen.kt` | the open-source licenses, from the list the AboutLibraries plugin generates |
| `ui/Components.kt` | pieces both screens use: error banner, section title and sticky section header, dot, uptime strip |
| `data/Problems.kt` | what counts as a problem: what the widget lists, and the list arranged by the Service list setting |
| `data/StatusCache.kt` | the last good snapshot, saved for the widget |
| `data/Settings.kt` | `Appearance`, `ServiceListMode`, `RefreshInterval` and `SettingsStore` (the saved settings: appearance, service list, interval, muted names) |
| `data/Auth.kt` | proof and challenge, reading a token, `AuthStore` (the saved sign-in) |
| `widget/StatusWidget.kt` | the Glance widget: fetch or cached snapshot, two layouts by size |
| `widget/StatusWidgetReceiver.kt` | the widget's receiver; schedules the background job |
| `widget/RefreshWorker.kt` | the background job: fetch, notify changes, redraw widgets, at the chosen interval and on push |
| `data/Times.kt` | `TimeFormat` (relative or clock; default, ISO or custom format; 12 or 24 hours; local or UTC) and `strftime` |
| `data/Changes.kt` | what changed between two fetches, less the muted names |
| `Notifier.kt` | the two notification channels, permission and channel checks, the phone's settings pages, posting |
| `Battery.kt` | the app's battery setting (restricted, optimized, unrestricted) and the page that changes it |
| `Push.kt` | `StatusPushService` (UnifiedPush events), the saved push address and last push time, `pushStatus` |
| `ui/theme/Theme.kt` | Catppuccin Mocha and Latte palettes, the current one (`palette`), Material colour schemes, state colours |
| `MainActivity.kt` | edge-to-edge activity that hosts the screen; applies the appearance setting |

Navigation uses Navigation 3: the back stack is a list of `@Serializable` keys that
survives process death. Screens slide horizontally on open, back and the back gesture
(Navigation 3 would fade, and shrink the screen during the gesture). Both screens read the one `StatusViewModel`, so a detail screen
shows the same snapshot as the list, and refreshing either refreshes both. A service is
identified by group and name; if it leaves the catalog while its screen is open, the
screen says so instead of crashing.

The launcher icon is a placeholder (green dot on Mocha base) until the app has its own
icon.

## Build configuration

- Versions live in `gradle/libs.versions.toml`. AGP 9 compiles Kotlin itself, so the
  build applies only the Compose and serialization Kotlin plugins, plus AboutLibraries
  (`com.mikepenz.aboutlibraries.plugin.android`), which collects the dependencies'
  licenses for Settings > About.
- `compileSdk`/`targetSdk` 37, `minSdk` 29 (Android 10).
- Application id `dev.rootiest.status`. The version is `appVersion` in
  `app/build.gradle.kts`; `versionCode` follows from it (`1.2.3` is 10203).
- `./gradlew install*` installs for the phone's main user only (`--user 0`), not into a
  work profile or Private space; `adb install` without it installs into every profile.
- Release builds are minified with R8 and shrink unused resources, and are signed with
  the release key when the machine has it (see Releases), otherwise with the debug key.
- Use release builds to judge how the app feels. On a Pixel 9 Pro the list scrolled with
  13% janky frames in a debug build and 1% in a release build.

## Tests

`./tests/run-tests.sh` runs `testDebugUnitTest`: JVM tests, no device needed.
`StatusApiTest` parses `app/src/test/resources/status-sample.json`, a saved signed-out
answer from the live API, and checks that unknown fields are ignored and the backup line parses and follows the time setting. `ProblemsTest`
covers what the widget lists and the Service list arrangements, `ChangesTest` what counts as a change and muting, `TimesTest` the time formats and strftime fields, `AuthTest` the challenge (the same test vector as rootiest-hub's) and reading a token. `LookupsTest` runs
the `data/Lookups.kt` functions against the same fixture (it relies on the fixture's
Notes outage on 2026-10-27, so keep an answer with a service event when refreshing it). Refresh the
fixture when the API changes:

```sh
curl -sSf https://status.rootiest.dev/api/status > app/src/test/resources/status-sample.json
```

## Releases

Releases are built and signed on the maintainer's machine, which holds the release key,
and published as Gitea releases with the APK attached. The key never leaves that machine
and CI holds no secrets.

- **The key**: a PKCS12 keystore (alias `rootiest-status`, RSA 4096, valid until 2126)
  with its passwords in a properties file beside it. Certificate SHA-256
  `36:15:BB:5F:65:09:81:1F:A5:BB:82:83:03:B5:72:64:2C:36:3E:40:5A:F9:3D:47:75:23:27:9A:D0:EC:93:A4`.
  Both files are backed up off the machine: an update must be signed with the same key,
  so losing it would mean every phone has to uninstall and reinstall.
- **Gradle** finds the key through `rootiest.signing=<path to the properties file>`
  (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) in `~/.gradle/gradle.properties`.
  Without it, as in CI or on a contributor's machine, release builds get the debug key.
- **To release**: bump `appVersion` in `app/build.gradle.kts` through a PR, merge it,
  then on an up-to-date `main` run `scripts/release.sh`. It refuses a dirty tree, a
  `main` that differs from `origin/main`, and a tag that exists; runs the tests; builds
  the release APK; refuses one not signed with the release certificate; and creates the
  Gitea release `v<appVersion>` (the tag on the current commit) with
  `rootiest-status-<version>.apk` attached and the pull requests merged since the last
  release as notes. `scripts/release.sh --dry-run` does all of it but publishing.
- **Installing and updates**: Obtainium, with its Forgejo source (Gitea serves the same
  releases API) and the repository URL `https://git.rootiest.dev/rootiest/rootiest-status-android`.
  No token is needed: the repository is public. Obtainium then offers each new release.
- A build signed with another key (a debug build, or a release build from a machine
  without the key) cannot update an installed release, and the reverse: Android
  refuses the signature. Switching between them means uninstalling first, which loses
  the sign-in and removes the widget.

## CI

`.github/workflows/ci.yml` is a GitHub-only workflow: the Android build needs the SDK
and about 2 GB of Gradle heap, more than the Gitea runners are sized for. It runs on every
push the Gitea push mirror copies to GitHub, and on demand. It runs the tests, builds
the debug APK and uploads it as the `app-debug` artifact. Results appear on GitHub,
not on the Gitea pull request.

## Roadmap

1. Fetch and render the live status. Done.
2. Service detail screens and navigation. Done.
3. Home-screen widget (Glance) refreshed by WorkManager. Done.
4. Notifications on state changes. Done.
5. Sign in with RootiestID to see the Servers section. Done, through rootiest-hub's
   app sign-in rather than a separate RootiestID client.
6. Signed release APKs on Gitea releases, installed and updated with Obtainium. Done.
