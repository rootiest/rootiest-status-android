#!/usr/bin/env bash
# release.sh [--dry-run]: build the signed release APK and publish it as the Gitea release v<appVersion>.
# Run on the machine that holds the release key (docs/README.md, "Releases"), from a clean main that matches origin/main,
# after bumping appVersion in app/build.gradle.kts through a PR. --dry-run builds and checks everything but publishes nothing.
set -euo pipefail
cd "$(dirname "$0")/.."
REPO=rootiest/rootiest-status-android
# SHA-256 of the release certificate: an APK signed with anything else (the debug key) is never published.
CERT=3615bb5f6509811fa5bb828303b572642c363e405af93d477523279ad0ec93a4
die() { echo "release: $*" >&2; exit 1; }

dry=0
[ "${1:-}" = --dry-run ] && dry=1

ver=$(sed -n 's/^val appVersion = "\(.*\)"$/\1/p' app/build.gradle.kts)
[[ $ver =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "no appVersion in app/build.gradle.kts"
tag=v$ver

[ "$(git branch --show-current)" = main ] || die "not on main"
git fetch -q origin
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || die "main does not match origin/main"
[ -z "$(git status --porcelain)" ] || die "the working tree is not clean"
[ -z "$(git ls-remote --tags origin "refs/tags/$tag")" ] || die "$tag exists already: bump appVersion"

./tests/run-tests.sh
./gradlew --no-daemon -q assembleRelease
apk=app/build/outputs/apk/release/app-release.apk

# the SDK Gradle built with: local.properties' sdk.dir first, as Gradle reads it, then ANDROID_HOME
sdk=$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)
sdk=${sdk:-${ANDROID_HOME:-}}
# no match is not an error here: the check below says what is missing (under pipefail, ls failing would exit silently)
apksigner=$(ls -d "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)
[ -x "$apksigner" ] || die "apksigner not found under $sdk/build-tools"
got=$("$apksigner" verify --print-certs "$apk" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
[ "$got" = "$CERT" ] || die "the APK is not signed with the release key (got ${got:-nothing}); is rootiest.signing set in ~/.gradle/gradle.properties?"

out=$(mktemp -d)
cp "$apk" "$out/rootiest-status-$ver.apk"
# notes: the pull requests merged since the last release (all of them for the first)
last=$(git describe --tags --abbrev=0 2>/dev/null || true)
git log --merges --format=%s ${last:+"$last..HEAD"} |
  sed -n "s/^Merge pull request '\(.*\)' (#\([0-9]*\)).*/- \1 (#\2)/p" > "$out/notes.md"

echo "release: $tag, $(wc -l < "$out/notes.md") pull requests, $(du -h "$out/rootiest-status-$ver.apk" | cut -f1) APK signed with the release key"
if [ $dry = 1 ]; then
  cat "$out/notes.md"
  echo "release: dry run, nothing published ($out)"
  exit 0
fi
tea releases create --repo "$REPO" --tag "$tag" --target "$(git rev-parse HEAD)" --title "$tag" \
  --note-file "$out/notes.md" --asset "$out/rootiest-status-$ver.apk"
echo "release: published https://git.rootiest.dev/$REPO/releases/tag/$tag"
