# Android Auto voice startup — 8 October 2026

A car voice turn previously requested audio focus before starting its microphone foreground service. For apps targeting Android 15 or later, focus requires a top activity or a running foreground service. CarVoiceSession now waits for DrivingModeService to acknowledge startForeground before requesting focus and opening CarAudioRecord. Startup failure or a five-second readiness timeout returns an error and cleans up the service instead of leaving the turn busy.

The phone APK is versionCode 5, versionName 1.3-ema-preview. Read-only debug readiness checks use the device's encrypted profile without exporting tokens or URLs. A separate debug focus check can verify service readiness and focus while the activity is behind Home. Neither diagnostic appears in the launcher.

## Validation

- 924 JVM tests passed; zero failures and errors. ARM64 and x86_64 debug APKs built.
- On an Android 16 Samsung SM-S918B, service readiness was acknowledged, focus was granted while the activity was behind Home, and the foreground service was stopped after the check.
- Baseline focus without DrivingModeService also succeeded on this configured phone. The reported car failure was not reproduced as a focus rejection; this change enforces the documented startup order, rather than establishing that rejection caused the user's failed wireless connection.
- The configured local and remote Hermes endpoints answered. STT health and transcription of synthetic diagnostic speech passed on both endpoint paths. Downloaded EMA loaded, synthesized audio, and completed production-factory playback on the phone.
- Android Auto 17.8 connected from the physical phone to DHU 2.0 via USB/ADB tunneling, completed TLS negotiation, and rendered its dashboard. This does not validate a real vehicle's wireless pairing or car microphone.
- Hermes was absent from the DHU launcher and the phone's launcher customization list, although PackageManager registered HermesCarService. The APK is installed via ADB. The current official documentation requires trusted distribution for Car App Library apps; enabling Unknown Sources alone does not cover templated apps. Trusted test distribution remains to be validated.

## Limits

The generic assistant still uses the existing navigation-category car declaration. No new Play distribution, category reclassification, installer spoofing, Android Auto data deletion, firmware change, or CSC change was performed. Physical wireless head-unit compatibility, app visibility after trusted distribution, and an actual car microphone-to-Hermes-to-EMA turn remain unverified.

Call recording is a separate app and permission path. Its Shizuku service availability and both-party audio require separate validation; this APK change does not establish reliable Bluetooth call recording.

References: [audio focus](https://developer.android.com/media/optimize/audio-focus), [car microphone](https://developer.android.com/training/cars/apps/library/car-microphone), [trusted vehicle testing](https://developer.android.com/training/cars/testing), [DHU](https://developer.android.com/training/cars/testing/dhu).
