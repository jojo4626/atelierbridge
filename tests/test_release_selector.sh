#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
javac -encoding UTF-8 -d "$TMP" \
  app/src/main/java/com/artcraftmobile/bridge/ReleaseAssetSelector.java \
  tests/java/com/artcraftmobile/bridge/ReleaseAssetSelectorTest.java
java -cp "$TMP" com.artcraftmobile.bridge.ReleaseAssetSelectorTest
