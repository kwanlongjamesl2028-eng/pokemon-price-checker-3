@echo off
REM Run without VS Code (Windows). Requires Java 11+ on PATH.
cd /d "%~dp0"

if not exist out\Main.class (
    javac -cp lib\sqlite-jdbc-3.51.2.0.jar -d out src\*.java
)

java --enable-native-access=ALL-UNNAMED -cp "out;lib\sqlite-jdbc-3.51.2.0.jar" Main
pause
