#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
javac -d "$out" \
  app/src/main/java/com/artcraftmobile/bridge/FileExportPolicy.java \
  tests/java/com/artcraftmobile/bridge/FileExportPolicyTest.java
java -cp "$out" com.artcraftmobile.bridge.FileExportPolicyTest
