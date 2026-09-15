#!/bin/bash
# Tur-12: emulator açılışı (M4). Skill: mac-android-build-emulator
export JAVA_HOME=/Users/gokhanuzman/007-HERMES/20-ARACLAR/jdk/jdk-17/Contents/Home
export ANDROID_HOME=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk
echo "AVD listesi:"
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" list avd
