#!/usr/bin/env bash
# release.sh [--dry-run]: build the signed release APKs and publish them as the Gitea release v<appVersion>, then release the app
# bundles on Google Play's internal testing tracks (phone and Wear OS) when this machine has the Play key; without it they wait in
# build/play/ for Play Console.
# Run on the machine that holds the release key (docs/README.md, "Releases"), from a clean main that matches origin/main,
# after bumping appVersion in build.gradle.kts through a PR. --dry-run builds and checks everything but publishes nothing.

case "${1:-}" in -h | --help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;; esac
set -euo pipefail
cd "$(dirname "$0")/.."
REPO=rootiest/rootiest-status-android
# SHA-256 of the release certificate: an APK signed with anything else (the debug key) is never published.
CERT=3615bb5f6509811fa5bb828303b572642c363e405af93d477523279ad0ec93a4
die() { echo "release: $*" >&2; exit 1; }

dry=0
case "$*" in
  --dry-run) dry=1 ;;
  "") ;;
  *) die "unknown argument '$*' (--dry-run or --help)" ;;
esac

ver=$(sed -n 's/^extra\["appVersion"\] = "\(.*\)"$/\1/p' build.gradle.kts)
[[ $ver =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "no appVersion in build.gradle.kts"
tag=v$ver

[ "$(git branch --show-current)" = main ] || die "not on main"
git fetch -q origin
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || die "main does not match origin/main"
[ -z "$(git status --porcelain)" ] || die "the working tree is not clean"
[ -z "$(git ls-remote --tags origin "refs/tags/$tag")" ] || die "$tag exists already: bump appVersion"

./tests/run-tests.sh
./gradlew --no-daemon -q assembleRelease bundleRelease
apk=app/build/outputs/apk/release/app-release.apk
wear=wear/build/outputs/apk/release/wear-release.apk
aab=app/build/outputs/bundle/release/app-release.aab
wear_aab=wear/build/outputs/bundle/release/wear-release.aab

# the SDK Gradle built with: local.properties' sdk.dir first, as Gradle reads it, then ANDROID_HOME
sdk=$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)
sdk=${sdk:-${ANDROID_HOME:-}}
# no match is not an error here: the check below says what is missing (under pipefail, ls failing would exit silently)
apksigner=$(ls -d "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)
[ -x "$apksigner" ] || die "apksigner not found under $sdk/build-tools"
for f in "$apk" "$wear"; do
  got=$("$apksigner" verify --print-certs "$f" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
  [ "$got" = "$CERT" ] || die "$f is not signed with the release key (got ${got:-nothing}); is rootiest.signing set in ~/.gradle/gradle.properties?"
done
# apksigner reads only APKs; a bundle is signed as a JAR, which keytool reads
for f in "$aab" "$wear_aab"; do
  got=$(keytool -printcert -jarfile "$f" 2>/dev/null | sed -n 's/^[[:space:]]*SHA256: //p' | head -1 | tr -d : | tr '[:upper:]' '[:lower:]')
  [ "$got" = "$CERT" ] || die "$f is not signed with the release key (got ${got:-nothing}); is rootiest.signing set in ~/.gradle/gradle.properties?"
done

out=$(mktemp -d)
cp "$apk" "$out/rootiest-status-$ver.apk"
# the watch app: its name does not start like the phone's, so an APK filter of ^rootiest-status-[0-9] keeps them apart
cp "$wear" "$out/wear-rootiest-status-$ver.apk"
# the bundles for Google Play: released on Play below, or uploaded by hand in Play Console; they stay out of the Gitea release
play=build/play
rm -rf "$play"
mkdir -p "$play"
cp "$aab" "$play/rootiest-status-$ver.aab"
cp "$wear_aab" "$play/wear-rootiest-status-$ver.aab"
# notes: the pull requests merged since the last release (all of them for the first)
last=$(git describe --tags --abbrev=0 2>/dev/null || true)
git log --merges --format=%s ${last:+"$last..HEAD"} |
  sed -n "s/^Merge pull request '\(.*\)' (#\([0-9]*\)).*/- \1 (#\2)/p" > "$out/notes.md"

# names that the lowercase pull request titles spell in lowercase, spelled properly in Play's notes (a word inside a
# hyphenated name, such as rootiest-hub, stays as it is)
play_names=('rootiest notes:Rootiest Notes' 'rootiest status:Rootiest Status' rootiestid:RootiestID rootiest:Rootiest
  'pocket id:Pocket ID' 'moe memos:Moe Memos' catppuccin:Catppuccin mocha:Mocha latte:Latte 'google play:Google Play'
  'wear os:Wear OS' android:Android gitea:Gitea obtainium:Obtainium unifiedpush:UnifiedPush readme:README)
play_names_sed=()
for name in "${play_names[@]}"; do
  play_names_sed+=(-e "s/(^|[^[:alnum:]-])${name%%:*}(\$|[^[:alnum:]-])/\\1${name#*:}\\2/g")
done
# Play's release notes: only the pull requests people notice in the app (feat, fix and perf, but not those scoped to
# build, ci, deps or release, which change the tooling), without the commit type or the PR number
grep -E '^- (feat|fix|perf)(\([^)]*\))?!?: ' "$out/notes.md" | grep -Ev '^- [a-z]+\((build|ci|deps|release)\)' |
  sed -E -e 's/^- [a-z]+(\([^)]*\))?!?: /- /' -e 's/ \(#[0-9]+\)$//' | sed -E "${play_names_sed[@]}" |
  awk '{ $2 = toupper(substr($2, 1, 1)) substr($2, 2); print }' > "$out/play-notes.txt" || true
# the service account key for the Play publishing API (docs/README.md, "Google Play")
play_key=${PLAY_KEY:-$HOME/.local/share/android/play-publisher.json}

echo "release: $tag, $(wc -l < "$out/notes.md") pull requests, $(du -h "$out/rootiest-status-$ver.apk" | cut -f1) APK and $(du -h "$out/wear-rootiest-status-$ver.apk" | cut -f1) watch APK signed with the release key"
if [ $dry = 1 ]; then
  cat "$out/notes.md"
  if [ -f "$play_key" ]; then
    echo "release: Play release notes:"
    if [ -s "$out/play-notes.txt" ]; then cat "$out/play-notes.txt"; else echo "(none: no app changes)"; fi
    scripts/play-upload.py --package dev.rootiest.status --key "$play_key" --check
  fi
  echo "release: dry run, nothing published ($out)"
  exit 0
fi
tea releases create --repo "$REPO" --tag "$tag" --target "$(git rev-parse HEAD)" --title "$tag" \
  --note-file "$out/notes.md" --asset "$out/rootiest-status-$ver.apk" --asset "$out/wear-rootiest-status-$ver.apk"
echo "release: published https://git.rootiest.dev/$REPO/releases/tag/$tag"
play_upload=(scripts/play-upload.py --package dev.rootiest.status --key "$play_key" --notes "$out/play-notes.txt"
  "$play/rootiest-status-$ver.aab" "$play/wear-rootiest-status-$ver.aab=wear:internal")
if [ -f "$play_key" ]; then
  "${play_upload[@]}" || die "the Gitea release is out, but not the Play one; retry with: ${play_upload[*]}"
else
  echo "release: no Play key at $play_key: upload the bundles in $play/ in Play Console"
fi
