# Privacy — preview source distribution

Hermes Mobil connects to servers selected by the user. Messages, prompts, selected attachments and any explicitly enabled phone context are sent to those servers and their configured model providers. Their retention and processing policies also apply. There is no claim that all processing stays on the phone.

Microphone and camera are optional runtime permissions used for speech, voice sessions and selected camera content. Speech recognition may use the Android recognizer/provider; local speech and server speech modes have different processing locations. Contacts, calendar and location access are optional; refusing a permission must leave unrelated chat functions usable.

The optional Accessibility service can read on-screen text, perform gestures, type and capture screenshots. Enable only when you intend to allow this control. Revoke it in Android Settings > Accessibility. Shizuku is a separate optional shell-level control mechanism. The phone bridge enables remote agent tools only when configured; disable it and revoke associated permissions to stop remote control. Screen projection requires Android consent. Notification access, if enabled in system settings, can expose other apps' message contents to assistant tools and the selected server.

Profile credentials use Android Keystore-backed encrypted preferences. Device diagnostics attempt to redact secrets; do not publish diagnostic files without reviewing them. The app disables Android automatic backup. Explicit JSON exports are NOT encrypted and may contain personal persona text, saved prompts, connection addresses and session labels even when credential fields are excluded. Keep exports private; deleting an export requires deleting the file from its selected destination too.

Revoke permissions in Android Settings, disable remote control features and remove server credentials when no longer required. Removing app data does not delete messages, attachments, logs or model-provider copies already stored on remote servers. Ask the operator of each selected server/provider for retention, deletion and access requests.

This document describes the preview's capabilities; it is not a completed Play Store policy submission or a guarantee of public-distribution readiness. Public release requires an identified operator/contact, a reviewed in-app disclosure and a verified signed package.

HTML document previews disable scripts, network loading and local-file access. Downloaded media uses temporary app storage. External links leave the application and the destination website has its own privacy policy. The new publication screenshot contains a controlled test document; raw phone captures, private conversations, deployment configuration and signing keys are not part of this update.
