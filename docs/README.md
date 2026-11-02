# rootiest-status-android documentation

Native Android app for the Rootiest services status page.

## Policy: docs are the single source of truth

Everything about this project is documented here. A change is not done until the docs
under this directory describe it. Generated documents are never edited by hand: edit
the source and regenerate.

## What it does

The app shows the same snapshot as [status.rootiest.dev](https://status.rootiest.dev):

- a headline card in the overall level's colour (green ok, yellow warn, red bad), with
  "Failover active" while traffic is served by the standby edge;
- the Servers section (state, vitals, note per host), only when the API includes it.
  The app does not sign in yet, so the API leaves it out (`"Hosts": null`);
- every group of services: state dot, uptime text and the per-day uptime strip.

Pull down to refresh. After a failed refresh the last good snapshot stays on screen
with an error banner above it.

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
| `StatusViewModel.kt` | holds `StatusUiState` (last view, loading, error) as a `StateFlow`; fetches on start and on `refresh()` |
| `ui/StatusScreen.kt` | the screen: top bar, pull-to-refresh, headline, servers, groups, uptime strips |
| `ui/theme/Theme.kt` | Catppuccin Mocha palette, Material colour scheme, state colours |
| `MainActivity.kt` | edge-to-edge activity that hosts the screen |

The launcher icon is a placeholder (green dot on Mocha base) until rootiest-icons has a
status app icon.

## Build configuration

- Versions live in `gradle/libs.versions.toml`. AGP 9 compiles Kotlin itself, so the
  build applies only the Compose and serialization Kotlin plugins.
- `compileSdk`/`targetSdk` 37, `minSdk` 29 (Android 10).
- Application id `dev.rootiest.status`, version `0.1.0` (code 1).
- Release builds are minified with R8 and shrink unused resources; there is no release
  signing set up yet.

## Tests

`./tests/run-tests.sh` runs `testDebugUnitTest`: JVM tests, no device needed.
`StatusApiTest` parses `app/src/test/resources/status-sample.json`, a saved signed-out
answer from the live API, and checks that unknown fields are ignored. Refresh the
fixture when the API changes:

```sh
curl -sSf https://status.rootiest.dev/api/status > app/src/test/resources/status-sample.json
```

## CI

`.github/workflows/ci.yml` is a GitHub-only workflow: the Android build needs the SDK
and about 2 GB of Gradle heap, and no Gitea runner is sized for that. It runs on every
push the Gitea push mirror copies to GitHub, and on demand. It runs the tests, builds
the debug APK and uploads it as the `app-debug` artifact. Results appear on GitHub,
not on the Gitea pull request.

## Roadmap

1. Fetch and render the live status (this version).
2. Service detail screens and navigation.
3. Home-screen widget (Glance) refreshed by WorkManager.
4. Notifications on state changes.
5. Sign in with RootiestID (AppAuth, PKCE) to see the Servers section; needs a public
   PocketID client and bearer-token support in rootiest-hub.
6. Signed release APKs on Gitea releases, installed and updated with Obtainium.
