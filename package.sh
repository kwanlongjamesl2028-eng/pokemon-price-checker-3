#!/bin/bash
# Creates a zip file ready to send to someone else.
# Includes all source, libraries, VS Code config, and pre-built classes.
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
NAME="pokemon-price-checker"
ZIP="../${NAME}.zip"

echo "Compiling..."
mkdir -p out
javac -cp lib/sqlite-jdbc-3.51.2.0.jar -d out src/*.java 2>/dev/null || {
    echo "Warning: compile failed (Java may not be on PATH). Zip will still include source."
}

echo "Creating ${ZIP}..."
rm -f "$ZIP"
zip -r "$ZIP" . \
    -x "*.DS_Store" \
    -x ".git/*" \
    -x ".gitignore" \
    -x "package.sh" \
    -x "create_zip.sh"

echo "Done! Send this file to your friend:"
echo "  $(cd .. && pwd)/${NAME}.zip"
echo ""
echo "Your friend should unzip it, open the folder in VS Code, and press F5."
echo "See HOW_TO_RUN.txt inside the zip for step-by-step instructions."
