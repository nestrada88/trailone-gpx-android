#!/usr/bin/env bash
set -euo pipefail
command -v adb >/dev/null || { echo 'Install Android platform-tools (adb) first.' >&2; exit 127; }
adb devices -l
printf '\nModel: '; adb shell getprop ro.product.model
printf 'SDK level: '; adb shell getprop ro.build.version.sdk
printf 'CPU ABIs: '; adb shell getprop ro.product.cpu.abilist
printf 'OS build: '; adb shell getprop ro.build.fingerprint
