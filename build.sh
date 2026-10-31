#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_JAR:?Set ANDROID_JAR}"
D8="${D8:-d8}"
MIN_API="${MIN_API:-21}"
rm -rf build out && mkdir -p build/classes out
javac --release 8 -encoding UTF-8 -Xlint:-options -cp "$ANDROID_JAR" -d build/classes $(ls *.java)
( cd build/classes && jar cf ../remotead.jar . )
"$D8" --release --min-api "$MIN_API" --lib "$ANDROID_JAR" --output out build/remotead.jar
[ -f out/classes.dex ] || { echo "d8 did not produce classes.dex" >&2; exit 1; }
echo "OK: out/classes.dex"
if [ -n "${BAKSMALI_JAR:-}" ]; then rm -rf smali; java -jar "$BAKSMALI_JAR" d -o smali out/classes.dex; fi
