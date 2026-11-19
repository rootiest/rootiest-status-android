# rootiest-status-android

Native Android app for the Rootiest services status page
([status.rootiest.dev](https://status.rootiest.dev)). It shows the overall level, each
service with its uptime strip and recent changes, a home-screen widget, and a notification
when a service goes down or comes back. It reads the page's JSON API (`GET /api/status`),
served by rootiest-hub, the status page's server.

Anyone can use the public view. Signing in with a Rootiest account shows what that account
may see, such as the Servers section for staff.

## Install

Android 10 or later, or Wear OS 3 or later for the watch app. Get it from Google Play's
closed test, or download the APK yourself. Both are signed with the same key, so either one
updates the other.

### Google Play (closed testing)

Rootiest Status is in closed testing on Google Play. Anyone can join. Do all three steps
with **the same Google account your phone's Play Store uses**:

1. **Join** the [Rootiest Status Google Group](https://groups.google.com/g/rootiest-status).
2. **Opt in** to testing at
   [play.google.com/apps/testing/dev.rootiest.status](https://play.google.com/apps/testing/dev.rootiest.status).
   It works from any browser.
3. **Install** it from
   [Google Play](https://play.google.com/store/apps/details?id=dev.rootiest.status). Updates
   then arrive like any other app's.

**Wear OS:** the watch app is part of the same Play entry, so joining once covers both.
After opting in, install Rootiest Status from the Play Store on the watch, or from the app's
Play Store page on the phone (choose the watch under *Install on more devices*).

Please keep the app installed for at least **14 days**: Google needs that before the app
can be published to everyone.

- **Already got a "welcome to the group" email?** You're a member: go straight to step 2.
- **"Item not found" in the Play Store:** the Play Store is signed in to a different Google
  account than the one in the group. Switch accounts in the Play Store (tap your profile
  picture), then open the install link again.
- **The opt-in page says you can't join:** you're not in the group yet, or you just joined.
  Wait a few minutes and reload. Google can take up to an hour to pass new testers through.

The group is for announcements only. To report a problem, open **Settings → About →
Report an issue** in the app, or go to [support.rootiest.dev](https://support.rootiest.dev).

### APK and Obtainium

Install the APK from the
[releases page](https://git.rootiest.dev/rootiest/rootiest-status-android/releases)
(`rootiest-status-*.apk` for the phone, `wear-rootiest-status-*.apk` for the watch), or let
[Obtainium](https://github.com/ImranR98/Obtainium) install it and keep it updated: add the
app with the URL `https://git.rootiest.dev/rootiest/rootiest-status-android` and the
Forgejo source (Gitea serves the same releases API). On the phone, set **Filter APKs by
regular expression** to `^rootiest-status-[0-9]`, or Obtainium asks which APK to install.

Releases are signed with a certificate whose SHA-256 fingerprint is
`36:15:BB:5F:65:09:81:1F:A5:BB:82:83:03:B5:72:64:2C:36:3E:40:5A:F9:3D:47:75:23:27:9A:D0:EC:93:A4`.

### Faster alerts

For alerts within seconds rather than at the next background check (every 15 minutes by default), install the
[ntfy](https://ntfy.sh) app with `https://ntfy.rootiest.dev` as its default server; the app
then registers with it on its own. See [docs/README.md](docs/README.md#notifications-and-the-background-job).

## Setup

Clone the repository and enter it:

```sh
git clone https://git.rootiest.dev/rootiest/rootiest-status-android.git
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
- Install on a connected phone or emulator: `./gradlew installRelease` to try the app
  (debug builds scroll with visible jank), `./gradlew installDebug` while debugging.
  On the maintainer's machine, which holds the release key, the two are signed with
  different keys, so switching between them means uninstalling first.
- Release: `scripts/release.sh` (see [docs/README.md](docs/README.md#releases)).
- Test: `./tests/run-tests.sh` (JVM unit, screen and screenshot tests, no device). CI runs the same command on
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
