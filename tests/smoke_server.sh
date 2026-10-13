#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -encoding UTF-8 -d "$OUT" \
  tests/java/android/util/Log.java \
  app/src/main/java/com/artcraftmobile/bridge/LocalHttpServer.java \
  tests/java/com/artcraftmobile/bridge/ServerSmokeTest.java
java -cp "$OUT" com.artcraftmobile.bridge.ServerSmokeTest
