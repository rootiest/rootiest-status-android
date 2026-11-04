#!/usr/bin/env bash
# The test suite: JVM unit tests, Robolectric screen tests, and screenshots compared with the golden images in
# app/src/test/screenshots (no device needed). CI runs it.
# Needs the Android SDK: ANDROID_HOME, or sdk.dir in local.properties.
set -eu
cd "$(dirname "$0")/.."
./gradlew --no-daemon verifyRoborazziDebug
