# rootiest-status-android

Native Android app for the Rootiest services status page
([status.rootiest.dev](https://status.rootiest.dev)). It reads the page's JSON API
(`GET /api/status`, served by [rootiest-hub](https://git.rootiest.dev/rootiest/rootiest-hub))
and shows the overall level, each service with its uptime strip, and the servers when the
viewer may see them.

## Setup

Clone the repository and enter it:

```sh
git clone <repository-url> rootiest-status-android
cd rootiest-status-android
git config core.hooksPath .githooks
```

You need JDK 21 or newer and the Android SDK. Android Studio installs the SDK; without it,
put the command-line tools in `~/Android/Sdk/cmdline-tools/latest`, accept the licences
(`sdkmanager --licenses`) and point the build at the SDK, either with `ANDROID_HOME` or with
`sdk.dir=/home/<you>/Android/Sdk` in `local.properties` (git-ignored). The build downloads
the platform and build tools it needs.

## Build and test

- Build: `./gradlew assembleDebug` writes `app/build/outputs/apk/debug/app-debug.apk`.
- Install on a connected phone or emulator: `./gradlew installDebug`.
- Test: `./tests/run-tests.sh` (JVM unit tests, no device). CI runs the same command on
  GitHub Actions, on the branches the Gitea mirror copies there; see
  [docs/README.md](docs/README.md#ci).

## Conventions

This project follows the Rootiest project conventions: branch before editing, never
push `main`, Conventional Commits, labeled PRs, docs as the single source of truth.
See [CONTRIBUTING.md](CONTRIBUTING.md).

## Documentation

Project documentation lives in [docs/](docs/README.md).

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

## License

Released under the GPL-3.0-or-later license. See [LICENSE](LICENSE).
