#!/usr/bin/env bash
set -euo pipefail

old=previous-release/app-release.apk
new=signed-release/app-release.apk
signer="$ANDROID_HOME/build-tools/35.0.0/apksigner"
old_cert=$("$signer" verify --print-certs "$old" | grep '^Signer #1 certificate SHA-256 digest:')
new_cert=$("$signer" verify --print-certs "$new" | grep '^Signer #1 certificate SHA-256 digest:')
test "$old_cert" = "$new_cert" || { echo 'Signing certificate changed: update would require uninstall'; exit 1; }

adb root
adb wait-for-device
adb install "$old"
adb shell am start -W -n com.tokopad.app/.MainActivity
adb shell am force-stop com.tokopad.app
uid_before=$(adb shell stat -c %u /data/user/0/com.tokopad.app | tr -d '\r')
adb shell "printf 'tokopad-update-check' > /data/user/0/com.tokopad.app/files/update-check"
db=/data/user/0/com.tokopad.app/databases/tokopad.db
adb shell test -s "$db"
db_before=$(adb shell sha256sum "$db" | tr -d '\r')

adb install -r "$new"
test "$(adb shell stat -c %u /data/user/0/com.tokopad.app | tr -d '\r')" = "$uid_before"
test "$(adb shell cat /data/user/0/com.tokopad.app/files/update-check | tr -d '\r')" = tokopad-update-check
test "$(adb shell sha256sum "$db" | tr -d '\r')" = "$db_before"
adb shell am start -W -n com.tokopad.app/.MainActivity
test -n "$(adb shell pidof com.tokopad.app | tr -d '\r')"
echo 'Signed APK installed over existing app; UID, private data, database preserved.' >> "$GITHUB_STEP_SUMMARY"
