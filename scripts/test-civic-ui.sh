#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes="$(mktemp -d)"
trap 'rm -rf "$classes"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$classes" src/main/java/com/tupinicraft/client/{BallotLayout,AnnouncementLayout}.java src/test/java/com/tupinicraft/CivicUiSelfTest.java
java -cp "$classes" com.tupinicraft.CivicUiSelfTest
