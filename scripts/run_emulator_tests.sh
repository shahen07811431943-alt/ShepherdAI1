#!/usr/bin/env bash
# Runs on the booted emulator (called by reactivecircus/android-emulator-runner).
# Uses `am instrument` directly (not Gradle connectedAndroidTest) so the app is not uninstalled
# before we pull the report files.
set -uo pipefail
PKG=com.example.mycompose.shepherdai
mkdir -p ci-report
adb wait-for-device
adb install -r -t app/build/outputs/apk/debug/ShepherdAI-debug.apk || exit 1
TEST_APK=$(ls app/build/outputs/apk/androidTest/debug/*.apk | head -1)
adb install -r -t "$TEST_APK" || exit 1
adb shell pm grant "$PKG" android.permission.CAMERA || true
adb logcat -c || true
adb shell am instrument -w "$PKG.test/androidx.test.runner.AndroidJUnitRunner" 2>&1 | tee ci-report/instrument.txt
adb pull "/sdcard/Android/data/$PKG/files/report/." ci-report/ || echo "no report files to pull"
adb exec-out screencap -p > ci-report/final_screen.png || true
adb logcat -d > ci-report/logcat.txt 2>&1 || true
# `am instrument` exits 0 even on failures, so decide from its output
grep -q "^OK (" ci-report/instrument.txt
