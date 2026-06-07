#!/bin/bash
# Run without VS Code (Mac/Linux). Requires Java 11+ on PATH.
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

mkdir -p out
if [ ! -f out/Main.class ]; then
    javac -cp lib/sqlite-jdbc-3.51.2.0.jar -d out src/*.java
fi

java --enable-native-access=ALL-UNNAMED \
    -cp "out:lib/sqlite-jdbc-3.51.2.0.jar" Main
