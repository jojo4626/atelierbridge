#!/usr/bin/env bash
set -euo pipefail
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
if [[ ! -d "$SDK_ROOT" ]]; then
  echo 'SDK Android introuvable' >&2
  exit 1
fi
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
  SDKMANAGER="$(find "$SDK_ROOT/cmdline-tools" -maxdepth 4 -type f -name sdkmanager | sort -V | tail -n 1)"
fi
if [[ ! -x "${SDKMANAGER:-}" ]]; then
  echo 'sdkmanager introuvable' >&2
  exit 1
fi
echo "ANDROID_HOME=$SDK_ROOT" >> "$GITHUB_ENV"
echo "ANDROID_SDK_ROOT=$SDK_ROOT" >> "$GITHUB_ENV"
yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null || true
"$SDKMANAGER" --sdk_root="$SDK_ROOT" --install 'platforms;android-35' 'build-tools;35.0.0'
test -f "$SDK_ROOT/platforms/android-35/android.jar"
