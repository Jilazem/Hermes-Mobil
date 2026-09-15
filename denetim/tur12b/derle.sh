#!/bin/bash
# Tur-12b derleme + test (M4). Skill: mac-android-build-emulator
export JAVA_HOME=/Users/gokhanuzman/007-HERMES/20-ARACLAR/jdk/jdk-17/Contents/Home
GRADLE=/Users/gokhanuzman/007-HERMES/20-ARACLAR/gradle-8.9/bin/gradle
cd /Users/gokhanuzman/hermes-workspace/wt-android-uzman
"$GRADLE" testDebugUnitTest assembleDebug --rerun-tasks --console=plain
echo "GRADLE-EXIT=$?"
