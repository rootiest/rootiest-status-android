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
[Obtainium](https://github.com/ImranR98/Obtainium) install it and keep it updated. Add it to
Obtainium in one of three ways:

- **One tap:** on the phone, open [Add Rootiest Status to Obtainium][obtainium-phone]. A
  watch that runs Obtainium can use [Add the Wear OS app to Obtainium][obtainium-wear].
- **From a file:** download
  [`rootiest-status.json`](https://git.rootiest.dev/rootiest/rootiest-status-android/raw/branch/main/obtainium/rootiest-status.json)
  (or [`rootiest-status-wear.json`](https://git.rootiest.dev/rootiest/rootiest-status-android/raw/branch/main/obtainium/rootiest-status-wear.json)
  for the watch) and import it in Obtainium under **Settings → Import/export → Obtainium
  import**.
- **By hand:** add the app with the URL
  `https://git.rootiest.dev/rootiest/rootiest-status-android` and the Forgejo source (Gitea
  serves the same releases API). Set **Filter APKs by regular expression** to
  `^rootiest-status-[0-9]` on the phone (`^wear-rootiest-status-[0-9]` on a watch), or
  Obtainium asks which APK to install.

[obtainium-phone]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22dev.rootiest.status%22%2C%22url%22%3A%22https%3A%2F%2Fgit.rootiest.dev%2Frootiest%2Frootiest-status-android%22%2C%22author%22%3A%22rootiest%22%2C%22name%22%3A%22Rootiest%20Status%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22includeDrafts%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertReleaseTitlesFilter%5C%22%3Afalse%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertReleaseNotesFilter%5C%22%3Afalse%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Erootiest-status-%5B0-9%5D%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22minimumUpdateAgeDays%5C%22%3A%5C%22%5C%22%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%2C%5C%22detectionDateAsReleaseDate%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22%5C%22%2C%5C%22installerMode%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22allowedSigningCertHashes%5C%22%3A%5C%22%5C%22%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthStaleRelease%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthStaleVersion%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthFailedChecks%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthArchived%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthFilteredReleases%5C%22%3A%5C%22%5C%22%7D%22%2C%22overrideSource%22%3A%22Codeberg%22%7D
[obtainium-wear]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22dev.rootiest.status%22%2C%22url%22%3A%22https%3A%2F%2Fgit.rootiest.dev%2Frootiest%2Frootiest-status-android%22%2C%22author%22%3A%22rootiest%22%2C%22name%22%3A%22Rootiest%20Status%20%28Wear%20OS%29%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22includeDrafts%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertReleaseTitlesFilter%5C%22%3Afalse%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertReleaseNotesFilter%5C%22%3Afalse%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Afalse%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Ewear-rootiest-status-%5B0-9%5D%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22minimumUpdateAgeDays%5C%22%3A%5C%22%5C%22%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%2C%5C%22detectionDateAsReleaseDate%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22%5C%22%2C%5C%22installerMode%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22allowedSigningCertHashes%5C%22%3A%5C%22%5C%22%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthStaleRelease%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthStaleVersion%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthFailedChecks%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthArchived%5C%22%3A%5C%22%5C%22%2C%5C%22updateHealthFilteredReleases%5C%22%3A%5C%22%5C%22%7D%22%2C%22overrideSource%22%3A%22Codeberg%22%7D

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
