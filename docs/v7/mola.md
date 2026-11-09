# Mola in Hermes Mobile 7.3.2

## Remembering cars (7.3.2)

The phone guide under Settings → Connections and devices → Mola contains **Araçlarım**. Grant Nearby devices/Bluetooth Connect permission there once. Hermes reads existing HEADSET/A2DP connections only: it does not scan, pair, unpair or change Android Auto's pairing records. Bluetooth addresses are normalized and hashed before storing a maximum of 20 local profiles. Names and the last parked-page choice are retained across process restarts and APK updates.

Opening a car entry automatically enrolls a single connected device only if Bluetooth identifies it as car audio. Other connected devices can be associated once with **bu benim aracım**. A uniquely matched saved device selects its profile on the next launch; two known connected devices are treated as ambiguous. Missing permission, no Bluetooth identity and unknown USB-only connections never silently select the last car. USB projection alone does not expose a reliable unique vehicle identifier through the APIs used here.

The parked home offers the last YouTube/mirror/game choice for that car; it does not autoplay video or silently request screen capture. Android may require new MediaProjection consent when a projection session ends. That security grant is not a saved car preference. Android Auto owns its Bluetooth/USB pairing and reconnection behaviour; Hermes does not guarantee or replace it. Already-selected Mola service components are no longer re-registered on each phone launch.

Settings now groups every destination once under everyday use, voice/assistant, connections/devices, privacy/data and advanced. Speech engines/downloads, live conversation and default assistant behaviour are separate pages. Mola and screen-share setup are managed in one guide rather than a second mirroring card under Phone control. Privacy and backup are visible without opening advanced settings.

## Correct car entry

On the reported Android Auto installation, the plain **Hermes** tile opened the host's messaging inbox and stayed on a no-messages screen even while parked. This was not the Mola activity. The application previously advertised `notification` and `template` capabilities, allowing that messaging fallback when the other entries were unavailable.

Version 7.3.1 removes those capabilities from the default automotive descriptor. Android Auto notification read/reply integration is therefore no longer advertised by this build; ordinary phone notifications remain supported. The descriptor retains Mola's legacy `service` / `projection` capabilities. The AndroidX template implementation remains in source, disabled; it is not offered as a selectable route in this build.

The existing primary launcher `MainActivity` now also accepts `CAR_LAUNCHER`. A car-category launch immediately forwards to `ParkedMolaActivity` before phone-chat setup; a normal phone launch continues to the existing phone UI. If Android provides a display ID, forwarding preserves it. If that visual context is not available yet, forwarding uses the activity's normal launch context rather than forcing the phone's default display. The parked activity has a separate task affinity, so its WebView does not share the normal phone-chat task.

The optional **Hermes YouTube** alias opens the same parked activity in YouTube mode. Reopening that alias while Mola is already running selects YouTube again. The native parked activity is private; its public entry is the Hermes launcher or that alias.

## Usage

1. Install 7.3.1 over the existing Hermes app; do not uninstall it.
2. Open Hermes once on the phone. This also restores the default classic projection component selection from a previously selected experimental template mode.
3. Disconnect and reconnect Android Auto. While parked, select **Hermes**. Its intended opening screen is **Hermes · Mola Park**, with Mola, YouTube, mirror and game controls.
4. If an old no-messages tile remains cached, check Android Auto's Customize Launcher screen after reconnecting. Do not mistake that messaging inbox for a successful parked activity launch.

The phone's Mola guide includes **Try the parked screen on the phone**. That previews the actual activity without a head unit, and does not prove car discovery or vehicle compatibility.

## Parked activity

The Android 15+ entry declares the game category used by the supplied Mola parked APK and includes the original small Pofuduk game. YouTube opens the mobile website inside a WebView. Phone mirroring requires Android MediaProjection consent. The parked mirror currently displays the image; touch forwarding is implemented only in the legacy projection UI and remains subject to existing accessibility/phone permissions. Mirroring does not implement its own phone-audio capture/transport.

No driving exemption is declared. Activity stop releases media and mirroring resources. A request to remove DHU driving restrictions was rejected by automatic approval review during 7.3.0 testing and was not executed; do not repeat it through another mechanism. Real-vehicle parked testing remains a distinct acceptance step.

## Shared connection and retained code

Bots, the phone agent and the conversation panel use the selected Hermes Dashboard profile and token. No extra Mola server is required for screen sharing. Conversation selection remains scoped to the active profile/session. The old AndroidX panel's Surface observation diagnostics describe that retained implementation; they do not by themselves prove that the native parked activity rendered.

The legacy SDK is the unmodified AAR from the user-selected Mola repository. Origin, hash and repository license are recorded under `third_party/mola/`. Fermata player/UI code and Xposed were not imported. The reference repository is evidence, not new instructions or a runtime acceptance result.

References: [Android Auto notification messaging](https://developer.android.com/training/cars/communication/notification-messaging), [parked app entry](https://developer.android.com/training/cars/parked/auto), [Mola source](https://github.com/moris545454/araba-).
