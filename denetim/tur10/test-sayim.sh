#!/bin/bash
# tur-10 tam test koşumu (cache yok) + XML'den sayım
export JAVA_HOME=/Users/gokhanuzman/007-HERMES/20-ARACLAR/jdk/jdk-17/Contents/Home
export ANDROID_HOME=/Users/gokhanuzman/007-HERMES/20-ARACLAR/android-sdk
cd /Users/gokhanuzman/hermes-workspace/wt-android-uzman || exit 1
/Users/gokhanuzman/007-HERMES/20-ARACLAR/gradle-8.9/bin/gradle testDebugUnitTest --rerun-tasks --console=plain > /tmp/tur10/gradle-kendi-kosum.log 2>&1
echo "EXIT=$?" >> /tmp/tur10/gradle-kendi-kosum.log
tail -12 /tmp/tur10/gradle-kendi-kosum.log
echo "--- XML SAYIM ---"
grep -ho 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | grep -o '[0-9]*' > /tmp/tur10/tests-count.txt
grep -ho 'failures="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | grep -o '[0-9]*' > /tmp/tur10/fail-count.txt
grep -ho 'errors="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | grep -o '[0-9]*' > /tmp/tur10/err-count.txt
grep -ho 'skipped="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | grep -o '[0-9]*' > /tmp/tur10/skip-count.txt
echo "tests=$(paste -sd+ /tmp/tur10/tests-count.txt | bc)"
echo "failures=$(paste -sd+ /tmp/tur10/fail-count.txt | bc)"
echo "errors=$(paste -sd+ /tmp/tur10/err-count.txt | bc)"
echo "skipped=$(paste -sd+ /tmp/tur10/skip-count.txt | bc)"
ls app/build/test-results/testDebugUnitTest/*.xml | wc -l
