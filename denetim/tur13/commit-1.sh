#!/bin/bash
# Tur-13 commit'leri (ASCII mesajlar; ayristirma icin ayri dosyalar).
set -e
cd /Users/gokhanuzman/hermes-workspace/wt-android-uzman

git add app/src/main/AndroidManifest.xml \
        app/src/main/res/values/strings.xml \
        app/src/main/java/com/hermes/mobile/data/AssistantMode.kt \
        app/src/main/java/com/hermes/mobile/data/AssistantRole.kt \
        app/src/main/java/com/hermes/mobile/data/AppSettings.kt \
        app/src/main/java/com/hermes/mobile/ChatViewModel.kt \
        app/src/main/java/com/hermes/mobile/MainActivity.kt \
        app/src/main/java/com/hermes/mobile/ui/ChatScreen.kt \
        app/src/main/java/com/hermes/mobile/ui/LiveVoiceSheet.kt \
        app/src/main/java/com/hermes/mobile/ui/SettingsScreen.kt \
        app/src/test/java/com/hermes/mobile/AssistantModeTest.kt \
        app/src/debug/AndroidManifest.xml \
        app/src/debug/java/com/hermes/mobile/ui/AssistantSelfTestActivity.kt

git commit -q -F - << 'MSG'
feat(tur13): phone assistant role - local voice path by default

Assistant gesture no longer opens Gemini Live: ACTION_ASSIST/VOICE_COMMAND
now enter assistant mode (chat + prominent push-to-talk + silent mic
permission request; recording starts only on user press).

- manifest: activity-alias .AssistantAlias labelled "Hermes Asistan" holds
  ASSIST + VOICE_COMMAND filters (moved off MainActivity so the assistant
  list does not show the app twice); exported + targetActivity.
- AssistantModeLogic (pure): role status text, flow phase machine
  (Off/Ready/Recording/Transcribing/AwaitingReply/Speaking), auto-read rule,
  auto-send rule, mic-ask rule. Tested in AssistantModeTest (24 tests).
- AssistantRole (Android): role status from public APIs only. RoleManager
  getRoleHolders is NOT in the compile SDK stub; uses isRoleHeld +
  Settings.Secure "assistant" + PackageManager.resolveActivity. Request
  intent; Android 12+ closes RequestRoleActivity instantly
  ("Role is not requestable"), so a missing grant falls back to
  Settings -> Default apps with short steps.
- settings: new "Telefon assistant" section (role line + make-default button
  + "auto-read the reply" switch + which voice path the assistant uses).
- voice: live sheet now separates "Yerel (Kahya)" (default assistant path)
  from "Gemini Live" (optional); choosing local stops the live session and
  opens assistant mode. Assistant replies are read out through the local
  voice_api path (message.complete -> voiceMsg.speak) only in assistant
  mode; plain chat defaults (voiceAutoSend=false, tap-to-speak) unchanged.
- debug: AssistantSelfTestActivity runs the real client code end to end
  (decisions + record state machine + /health + /transcribe + auto-read +
  /synthesize + playback) against mock or real endpoint.
MSG

git log --oneline -1
