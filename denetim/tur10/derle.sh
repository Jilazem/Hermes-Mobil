#!/bin/bash
# tur-10 derleme koşumu (kendi bağımsız loguna yazar)
export JAVA_HOME=/Users/gokhanuzman/007-HERMES/20-ARACLAR/jdk/jdk-17/Contents/Home
export ANDROID_HOME=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk
cd /Users/gokhanuzman/hermes-workspace/wt-android-uzman || exit 1
/Users/gokhanuzman/007-HERMES/20-ARACLAR/gradle-8.9/bin/gradle testDebugUnitTest assembleDebug --console=plain > /tmp/tur10/gradle-kosum.log 2>&1
echo "EXIT=$?" >> /tmp/tur10/gradle-kosum.log
tail -25 /tmp/tur10/gradle-kosum.log
