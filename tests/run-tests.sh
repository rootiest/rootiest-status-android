#!/usr/bin/env bash
# The test suite: JVM unit tests of the app (no device needed). CI runs it.
# Needs the Android SDK: ANDROID_HOME, or sdk.dir in local.properties.
set -eu
cd "$(dirname "$0")/.."
./gradlew --no-daemon testDebugUnitTest
