#!/bin/bash
# Test XML sayımı (tur-12b) — tests/failures/errors/skipped
cd /Users/gokhanuzman/hermes-workspace/wt-android-uzman/app/build/test-results/testDebugUnitTest || exit 1
files=$(ls *.xml 2>/dev/null | wc -l | tr -d ' ')
tests=$(grep -ho 'tests="[0-9]*"' *.xml | sed 's/[^0-9]//g' | paste -sd+ - | bc)
fail=$(grep -ho 'failures="[0-9]*"' *.xml | sed 's/[^0-9]//g' | paste -sd+ - | bc)
errs=$(grep -ho 'errors="[0-9]*"' *.xml | sed 's/[^0-9]//g' | paste -sd+ - | bc)
skip=$(grep -ho 'skipped="[0-9]*"' *.xml | sed 's/[^0-9]//g' | paste -sd+ - | bc)
echo "XML-DOSYA=$files TESTS=$tests FAILURES=$fail ERRORS=$errs SKIPPED=$skip"
ls -l /Users/gokhanuzman/hermes-workspace/wt-android-uzman/app/build/outputs/apk/debug/app-debug.apk
