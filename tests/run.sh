#!/usr/bin/env bash
set -euo pipefail
# Run from the project root. With only a JRE, set TEST_ECJ_JAR to an ECJ jar.
TEST_BUILD_DIR="$(mktemp -d)"
TEST_SOURCES=(
  tests/fakes/android/os/Build.java
  tests/fakes/android/media/audiofx/AudioEffect.java
  tests/fakes/android/media/audiofx/DynamicsProcessing.java
  tests/fakes/android/media/audiofx/Equalizer.java
  tests/fakes/android/media/audiofx/LoudnessEnhancer.java
  app/src/main/java/com/volumeboostersoundboost/app/RecoveryBudget.java
  tests/EngineTests.java
  app/src/main/java/com/volumeboostersoundboost/app/BoostConfig.java
  app/src/main/java/com/volumeboostersoundboost/app/SessionEffect.java
)
if [[ -n "${TEST_ECJ_JAR:-}" ]]; then
  java -jar "$TEST_ECJ_JAR" -source 8 -target 8 -encoding UTF-8 -d "$TEST_BUILD_DIR" "${TEST_SOURCES[@]}"
else
  javac -source 8 -target 8 -encoding UTF-8 -d "$TEST_BUILD_DIR" "${TEST_SOURCES[@]}"
fi
java -cp "$TEST_BUILD_DIR" com.volumeboostersoundboost.app.EngineTests
# Retain temporary classes for inspection; no cleanup touches the project.
