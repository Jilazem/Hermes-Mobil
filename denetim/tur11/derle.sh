#!/bin/bash
# Tur-11 derleme/kosum sarmalayici — JAVA_HOME + ANDROID_HOME sabit (mac-android-build-emulator skill).
set -e
export JAVA_HOME=/Users/gokhanuzman/007-HERMES/20-ARACLAR/jdk/jdk-17/Contents/Home
export ANDROID_HOME=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk
cd /Users/gokhanuzman/hermes-workspace/wt-android-uzman
exec /Users/gokhanuzman/007-HERMES/20-ARACLAR/gradle-8.9/bin/gradle "$@" --console=plain
