# rootiest-status-android documentation

Native Android app for the Rootiest services status page.

## Policy: docs are the single source of truth

Everything about this project is documented here. A change is not done until the docs
under this directory describe it. Generated documents are never edited by hand: edit
the source and regenerate.

## What it does

The app shows the same snapshot as [status.rootiest.dev](https://status.rootiest.dev):

- a headline card in the overall level's colour, centred and kept at the top while
  the list scrolls (green ok, yellow warn, red bad), with
  "Failover active" while traffic is served by the standby edge;
- the Servers section (state, vitals, note per host), only when the API includes it:
  after signing in, for staff and the groups the catalog lets see servers;
- every group of services: state dot, uptime text and the per-day uptime strip.

Tap a service to open its detail screen:

- a summary card: state (and "planned" when a planned window expects it down), uptime
  text, the service's detail line, its hostname (a link to the site, except for services
  probed by TCP connect, whose hostname is only a name) and the uptime strip;
- **Recent changes**: the snapshot's events for that service (`Kind` `service`, `Subject`
  equal to its name), newest first, in local time;
- **Daily uptime (UTC days)**: one row per day, newest first, from the strip's cells. Days are
  UTC days, as rootiest-hub counts them, so tomorrow's date can appear in the evening. Days before
  the history began (`Class` `none`) are left out.

Pull down to refresh on either screen. After a failed refresh the last good snapshot
stays on screen with an error banner above it. Back (button or gesture) returns to the
list.

## Signing in

**Sign in** (top right) signs in with RootiestID through rootiest-hub, which then shows
what that account may see, as on the web page (staff see everything, including the
Servers section). The flow is rootiest-hub's app sign-in (its `docs/README.md`, "The
Android app"):

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

The top bar shows the RootiestID display name and **Sign out**, which deletes the token.
The app reads the name and expiry from the token (signed by the hub, only read here). A
sign-in lasts 14 days, the hub's `STATUS_SESSION_TTL`; once it expires, or after the
hub's `signout-all`, the app shows the public view again and **Sign in** reappears
(after `signout-all` the name stays until the token's own expiry, but the view is
public). Notifications then include servers too; a server first seen after signing in
is not reported as a change.

## Home-screen widget

The **Rootiest Status** widget shows the overall level's colour and the headline. At
two cells tall or more it also lists what is wrong (up to four rows, then "and N more")
and when the snapshot was fetched. Tapping it opens the app.

- What is wrong (`data/Problems.kt`): servers whose state is not `healthy`, then
  services whose state is not `up` or `standby` (a standby copy is down on purpose).
  "(planned)" marks one a planned window expects.
- Each widget update fetches `/api/status` and saves the answer in the app's files
  (`last-status.json`, `data/StatusCache.kt`). When the fetch fails the widget shows
  the saved snapshot, with "Offline · as of <time>" in yellow.
- The background job (below) redraws every widget every 15 minutes. The widget's own
  `updatePeriodMillis` is 0, since the system's minimum is 30 minutes.
- The app's own refresh does not redraw the widget yet.

## Notifications and the background job

`RefreshWorker` (WorkManager) runs every 15 minutes, the shortest period WorkManager
allows, and only with a network connection. Each run fetches the status, posts a
notification for each change, saves the states and the snapshot, and redraws the
widgets. It is scheduled on every app start (and when a widget is added) and keeps
running without a widget, since it also sends the notifications. Android may run it
later than every 15 minutes when the phone is idle (Doze), so a change can take longer
to arrive; ntfy remains the fast path.

- A change is a service or server whose state differs from the previous run
  (`data/Changes.kt`; states saved in `last-states.json`). Nothing is posted on the
  first run, for a name not seen before, or for one a planned window expects (as
  rootiest-hub holds its own alerts for those).
- Notifications go to the "Service changes" channel: "Gitea is down / was up",
  "Gitea is back up / was down". Each name has one notification id, so a recovery
  replaces the down notification. Tapping one opens the app.
- On Android 13 and later the app asks for notification permission on first launch.
  Refusing only turns notifications off.

Testing on a device: a periodic job cannot be forced early (WorkManager reschedules a
run that comes "before schedule"). With a debug build, write a state into
`files/last-states.json` (`adb shell run-as dev.rootiest.status ...`) and wait for the
next run.

## Data source

`GET https://status.rootiest.dev/api/status`, served by rootiest-hub
(`internal/status/web.go`). The body is rootiest-hub's `View` struct
(`internal/status/view.go`) encoded as JSON, so field names are Go's exported names
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
| `StatusViewModel.kt` | holds `StatusUiState` (last view, loading, error) as a `StateFlow`; fetches on start and on `refresh()` |
| `Routes.kt` | Navigation 3 keys: `Home`, `ServiceDetail(group, name)` |
| `ui/App.kt` | the app root: one `StatusViewModel` above the navigation, the back stack and `NavDisplay` |
| `ui/StatusScreen.kt` | the list: top bar, pull-to-refresh, headline, servers, groups, tappable services |
| `ui/ServiceScreen.kt` | a service's detail: summary, recent changes, daily uptime |
| `ui/Components.kt` | pieces both screens use: error banner, section title, dot, uptime strip |
| `data/Problems.kt` | what the widget lists: unhealthy servers and down services |
| `data/StatusCache.kt` | the last good snapshot, saved for the widget |
| `data/Auth.kt` | proof and challenge, reading a token, `AuthStore` (the saved sign-in) |
| `widget/StatusWidget.kt` | the Glance widget: fetch or cached snapshot, two layouts by size |
| `widget/StatusWidgetReceiver.kt` | the widget's receiver; schedules the background job |
| `widget/RefreshWorker.kt` | the background job: fetch, notify changes, redraw widgets, every 15 minutes |
| `data/Changes.kt` | what changed between two fetches |
| `Notifier.kt` | the notification channel, permission check and posting |
| `ui/theme/Theme.kt` | Catppuccin Mocha palette, Material colour scheme, state colours |
| `MainActivity.kt` | edge-to-edge activity that hosts the screen |

Navigation uses Navigation 3: the back stack is a list of `@Serializable` keys that
survives process death. Screens slide horizontally on open, back and the back gesture
(Navigation 3 would fade, and shrink the screen during the gesture). Both screens read the one `StatusViewModel`, so a detail screen
shows the same snapshot as the list, and refreshing either refreshes both. A service is
identified by group and name; if it leaves the catalog while its screen is open, the
screen says so instead of crashing.

The launcher icon is a placeholder (green dot on Mocha base) until rootiest-icons has a
status app icon.

## Build configuration

- Versions live in `gradle/libs.versions.toml`. AGP 9 compiles Kotlin itself, so the
  build applies only the Compose and serialization Kotlin plugins.
- `compileSdk`/`targetSdk` 37, `minSdk` 29 (Android 10).
- Application id `dev.rootiest.status`. The version is `appVersion` in
  `app/build.gradle.kts`; `versionCode` follows from it (`1.2.3` is 10203).
- Release builds are minified with R8 and shrink unused resources, and are signed with
  the release key when the machine has it (see Releases), otherwise with the debug key.
- Use release builds to judge how the app feels. On a Pixel 9 Pro the list scrolled with
  13% janky frames in a debug build and 1% in a release build.

## Tests

`./tests/run-tests.sh` runs `testDebugUnitTest`: JVM tests, no device needed.
`StatusApiTest` parses `app/src/test/resources/status-sample.json`, a saved signed-out
answer from the live API, and checks that unknown fields are ignored. `ProblemsTest` covers
what the widget lists, `ChangesTest` what counts as a change, `AuthTest` the challenge (the same test vector as rootiest-hub's) and reading a token. `LookupsTest` runs
the `data/Lookups.kt` functions against the same fixture (it relies on the fixture's
Notes outage on 2026-10-27, so keep an answer with a service event when refreshing it). Refresh the
fixture when the API changes:

```sh
curl -sSf https://status.rootiest.dev/api/status > app/src/test/resources/status-sample.json
```

## Releases

Releases are built and signed on the workstation that holds the release key, and
published as Gitea releases with the APK attached. The key never leaves that machine
and CI holds no secrets.

- **The key**: `~/.local/share/android/rootiest-status-release.jks` (PKCS12, alias
  `rootiest-status`, RSA 4096, valid until 2126), its passwords in
  `rootiest-status-release.properties` next to it (both mode 600). Certificate SHA-256
  `36:15:BB:5F:65:09:81:1F:A5:BB:82:83:03:B5:72:64:2C:36:3E:40:5A:F9:3D:47:75:23:27:9A:D0:EC:93:A4`.
  Keep a copy of both files somewhere safe (a password manager): an update must be
  signed with the same key, so losing it means every phone has to uninstall and
  reinstall.
- **Gradle** finds the key through `rootiest.signing=<path to the properties file>` in
  `~/.gradle/gradle.properties`. Without it, release builds get the debug key.
- **To release**: bump `appVersion` in `app/build.gradle.kts` through a PR, merge it,
  then on an up-to-date `main` run `scripts/release.sh`. It refuses a dirty tree, a
  `main` that differs from `origin/main`, and a tag that exists; runs the tests; builds
  the release APK; refuses one not signed with the release certificate; and creates the
  Gitea release `v<appVersion>` (the tag on the current commit) with
  `rootiest-status-<version>.apk` attached and the pull requests merged since the last
  release as notes. `scripts/release.sh --dry-run` does all of it but publishing.
- **Installing and updates**: Obtainium, with its Forgejo source (Gitea serves the same
  releases API): the repository URL `https://git.rootiest.dev/rootiest/rootiest-status-android`
  and a Gitea access token with read access to repositories, since the repository is
  private. Obtainium then offers each new release.
- A build signed with another key (a debug build, or a release build from a machine
  without the key) cannot update an installed release, and the reverse: Android
  refuses the signature. Switching between them means uninstalling first, which loses
  the sign-in and removes the widget.

## CI

`.github/workflows/ci.yml` is a GitHub-only workflow: the Android build needs the SDK
and about 2 GB of Gradle heap, and no Gitea runner is sized for that. It runs on every
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
