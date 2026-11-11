#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes="$(mktemp -d)"
trap 'rm -rf "$classes"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$classes" src/main/java/com/tupinicraft/tigrinho/SlotRules.java src/main/java/com/tupinicraft/tigrinho/SpinGate.java src/main/java/com/tupinicraft/client/tigrinho/ReelAnimation.java src/test/java/com/tupinicraft/TigrinhoSelfTest.java
java -cp "$classes" com.tupinicraft.TigrinhoSelfTest
