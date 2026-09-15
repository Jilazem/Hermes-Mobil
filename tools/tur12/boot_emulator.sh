#!/bin/bash
# Tur-12: emulator hermes-v2 açılışı (arka plan; boot ~2-4 dk)
export ANDROID_HOME=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk
exec "$ANDROID_HOME/emulator/emulator" -avd hermes-v2 -no-audio -gpu auto -no-boot-anim
