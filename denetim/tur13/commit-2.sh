#!/bin/bash
# Tur-13 kanit commit'i (docs). ASCII mesaj.
set -e
cd /Users/gokhanuzman/hermes-workspace/wt-android-uzman
git add denetim/tur13
git commit -q -F - << 'MSG'
docs(tur13): report + emulator evidence (574 -> 598 tests, APK sha256)

denetim/tur13/RAPOR.md: what changed (F1/F2/F3), architecture decisions,
evidence table, measured platform facts, honest remaining list.

Evidence kept in the same folder:
- unit tests: 46 XML files, tests=598 failures=0 errors=0 (574 before)
- APK sha256/md5 + install Success on the emulator
- query-activities dumps: com.hermes.mobile.AssistantAlias resolves for both
  ACTION_ASSIST and ACTION_VOICE_COMMAND; aapt2 shows string/assistant_label
  = "Hermes Asistan" while app_name stays "Hermes V2"
- screenshots + uiautomator dumps: chooser listing "Hermes Asistan",
  RECORD_AUDIO permission dialog, assistant banner (ready / auto-read on /
  local path, no recording), cold + warm + VOICE_COMMAND starts
- settings: "Su an: Google" then "Hermes: varsayilan asistan" after the role
  is held; role button now falls back to Settings -> Default apps because
  Android 12+ refuses the in-app role request ("Role is not requestable")
- assistant self-test (mock endpoint): BASARILI, mock log shows
  GET /health 200, POST /transcribe 200 (multipart, ogg), POST /synthesize 200
- assistant self-test (real endpoint): /health and /transcribe OK with a real
  whisper transcript, /synthesize did not answer within ~300 s
  (live_synth_probe.py from the Mac confirms the same server-side slowness)
- run scripts kept reproducible (01..09 + selftest_kos.py + live_synth_probe.py)
MSG

git log --oneline -3
git status --short | head -5
