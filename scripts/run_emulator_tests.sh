#!/usr/bin/env bash
# Runs on the booted emulator (called by reactivecircus/android-emulator-runner).
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
# Android 11+ blocks adb from reading Android/data; google_apis images allow root.
adb root || true
sleep 3
adb wait-for-device
adb pull "/sdcard/Android/data/$PKG/files/report/." ci-report/ \
  || adb pull "/data/media/0/Android/data/$PKG/files/report/." ci-report/ \
  || echo "no report files to pull"
ls -la ci-report || true
adb exec-out screencap -p > ci-report/final_screen.png || true
adb logcat -d > ci-report/logcat.txt 2>&1 || true
grep -q "^OK (" ci-report/instrument.txt
