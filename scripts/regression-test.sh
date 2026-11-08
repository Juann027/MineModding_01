#!/usr/bin/env bash
# Testes das correções sem Gradle, Minecraft ou dependências externas. Requer Java 21 com jdk.compiler.
set -euo pipefail
cd "$(dirname "$0")/.."
classes="$(mktemp -d)"
trap 'rm -rf "$classes"' EXIT
base=src/main/java/com/tupinicraft
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$classes" \
  "$base/election/Ballot.java" "$base/election/Candidate.java" "$base/election/ElectionTally.java" \
  "$base/territory/TerritoryType.java" "$base/politics/PoliticalOffice.java" \
  "$base/mandate/OccupantType.java" "$base/mandate/Occupant.java" "$base/mandate/Mandate.java" \
  "$base/mandate/SeatKey.java" "$base/mandate/MandateRegistry.java" \
  "$base/skilltree/EffectReconciler.java" src/test/java/com/tupinicraft/RegressionSelfTest.java
java -cp "$classes" com.tupinicraft.RegressionSelfTest
