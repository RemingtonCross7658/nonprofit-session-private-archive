#!/usr/bin/env sh
set -eu

classes="${TMPDIR:-/tmp}/nonprofit-session-archive-classes"
mkdir -p "$classes"
javac -d "$classes" $(find src/main/java -name '*.java')
java -cp "$classes" org.nonprofit.archive.NonprofitSessionRunner "$@"
