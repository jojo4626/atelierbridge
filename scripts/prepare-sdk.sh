#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
PLATFORM_JAR="$SDK_ROOT/platforms/android-35/android.jar"
BUILD_TOOLS="$SDK_ROOT/build-tools/35.0.0"

if [[ ! -d "$SDK_ROOT" ]]; then
  echo "SDK Android introuvable : $SDK_ROOT" >&2
  exit 1
fi

if [[ -n "${GITHUB_ENV:-}" ]]; then
  printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$SDK_ROOT" "$SDK_ROOT" >> "$GITHUB_ENV"
fi

sdk_ready() {
  [[ -f "$PLATFORM_JAR" && -x "$BUILD_TOOLS/apksigner" && -x "$BUILD_TOOLS/aapt2" ]]
}

if sdk_ready; then
  echo 'Android SDK 35 et Build Tools 35.0.0 déjà installés : aucune installation nécessaire.'
  exit 0
fi

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMANAGER" ]]; then
  SDKMANAGER="$(find "$SDK_ROOT/cmdline-tools" -maxdepth 4 -type f -name sdkmanager 2>/dev/null | sort -V | tail -n 1 || true)"
fi
if [[ ! -x "${SDKMANAGER:-}" ]]; then
  echo 'sdkmanager introuvable. Impossible de compléter le SDK Android.' >&2
  exit 1
fi

# Le processus yes peut se terminer avec SIGPIPE quand sdkmanager a lu sa dernière réponse.
( set +o pipefail; yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null 2>&1 ) || true

for attempt in 1 2 3; do
  echo "Installation Android SDK 35 (tentative $attempt/3)..."
  if "$SDKMANAGER" --sdk_root="$SDK_ROOT" --install 'platforms;android-35' 'build-tools;35.0.0'; then
    if sdk_ready; then
      echo 'Android SDK prêt.'
      exit 0
    fi
    echo 'La commande a réussi, mais des fichiers Android requis sont absents.' >&2
  else
    echo "sdkmanager a échoué à la tentative $attempt." >&2
  fi
  if (( attempt < 3 )); then sleep 6; fi
done

for file in "$PLATFORM_JAR" "$BUILD_TOOLS/apksigner" "$BUILD_TOOLS/aapt2"; do
  if [[ ! -e "$file" ]]; then echo "Fichier manquant : $file" >&2; fi
done
exit 1
