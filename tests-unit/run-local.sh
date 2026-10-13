#!/usr/bin/env bash
# Runs the JVM unit tests for the voice post-processor without Gradle or an Android SDK.
# Needs a JDK 17 and kotlinc 2.1 on PATH (or DEVTOOLS pointing at a folder holding jdk-17*/, kotlinc/, lib/*.jar).
# CI runs the same tests through Gradle: ./gradlew testUnstableDebugUnitTest
set -euo pipefail
cd "$(dirname "$0")/.."

DEVTOOLS="${DEVTOOLS:-../.devtools}"
if [ -d "$DEVTOOLS" ]; then
  JDK_DIR=$(ls -d "$DEVTOOLS"/jdk-17* 2>/dev/null | head -1 || true)
  [ -n "$JDK_DIR" ] && export JAVA_HOME="$JDK_DIR" && PATH="$JDK_DIR/bin:$PATH"
  PATH="$DEVTOOLS/kotlinc/bin:$PATH"
fi
KOTLINC=$(command -v kotlinc || command -v kotlinc.bat)
SEP=":"; native() { echo "$1"; }
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";"; native() { cygpath -m "$1"; };; esac
LIB=$(native "$DEVTOOLS/lib")
JUNIT_CP="$LIB/junit-4.13.2.jar${SEP}$LIB/hamcrest-core-1.3.jar"

OUT=$(native "${TMPDIR:-/tmp}/voicepp-tests")
rm -rf "$OUT" && mkdir -p "$OUT/classes"

"$KOTLINC" -nowarn \
  java/src/org/futo/inputmethod/latin/uix/utils/voicepp/*.kt \
  tests-unit/src/org/futo/inputmethod/latin/uix/utils/voicepp/*.kt \
  -cp "$JUNIT_CP" -d "$OUT/classes"

KOTLIN_STDLIB=$(native "$(dirname "$KOTLINC")/../lib/kotlin-stdlib.jar")
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT/classes${SEP}tests-unit/resources${SEP}$JUNIT_CP${SEP}$KOTLIN_STDLIB" \
  org.junit.runner.JUnitCore org.futo.inputmethod.latin.uix.utils.voicepp.VoicePPGoldenTest
