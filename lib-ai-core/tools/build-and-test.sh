#!/usr/bin/env bash
# Compiles and tests lib-ai-core with plain javac: no Gradle, no Android SDK, no network.
#
# Dependencies (gson, junit, hamcrest) are expected in $AI_LIBS or ~/libs-ai:
#   curl -sSfLO https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.1/gson-2.8.1.jar
#   curl -sSfLO https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar
#   curl -sSfLO https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBS="${AI_LIBS:-$HOME/libs-ai}"
GSON="$LIBS/gson-2.8.1.jar"
JUNIT="$LIBS/junit-4.12.jar"
HAMCREST="$LIBS/hamcrest-core-1.3.jar"

for jar in "$GSON" "$JUNIT" "$HAMCREST"; do
    if [ ! -f "$jar" ]; then
        echo "missing dependency: $jar" >&2
        exit 2
    fi
done

cd "$MODULE_DIR"
rm -rf build
mkdir -p build/classes build/test-classes

find src/main/java -name '*.java' > build/main-sources.txt
find src/test/java -name '*.java' > build/test-sources.txt

echo "compiling main sources"
javac --release 8 -Xlint:-options -cp "$GSON" -d build/classes @build/main-sources.txt

echo "compiling tests"
javac --release 8 -Xlint:-options \
    -cp "$GSON:build/classes:$JUNIT:$HAMCREST" \
    -d build/test-classes @build/test-sources.txt

echo "running tests"
java -cp "$GSON:build/classes:$JUNIT:$HAMCREST:build/test-classes" org.junit.runner.JUnitCore \
    com.duy.ide.ai.sse.SseReaderTest \
    com.duy.ide.ai.diff.LineDiffTest \
    com.duy.ide.ai.diff.PatchApplierTest \
    com.duy.ide.ai.context.TokenBudgetTest \
    com.duy.ide.ai.context.ContextBuilderTest \
    com.duy.ide.ai.provider.OpenAiCompatibleProviderTest
