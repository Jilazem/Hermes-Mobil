# Credits and acknowledgements

Maintained by [Jilazem](https://github.com/Jilazem) and the Hermes Mobile contributors. Thank you to the upstream authors, library maintainers and testers.

This page distinguishes adapted source, bundled templates, dependencies, external software and design inspiration. Earlier credits described independent design work; **that blanket “no copied code” statement does not cover the v7 adaptations below**.

## Hermes ecosystem

[Nous Research / Hermes Agent](https://github.com/NousResearch/hermes-agent) provides the MIT-licensed self-hosted agent ecosystem this client connects to. Hermes Mobile is an independent companion, not an official Nous Research mobile distribution.

## Adapted code and bundled templates

| Project / authors | Pinned revision | Contribution and retained terms |
|---|---|---|
| [Mika](https://github.com/wangzan101/Mika), wangzan101 and contributors | `ebb0e44750d80aabb6b642c69908825d6b1a8107` | Endpoint address validation and task-state vocabulary; Apache-2.0. [LICENSE, NOTICE and adaptation record](third_party/mika/UPSTREAM.md). |
| [Phongent](https://github.com/mylea1/phongent), mylea1; based on Mika by wangzan101 | `0b57e35d4e4c8460fdd87fcd33f751f764e7423d` | ControllerQueue adapted as DurableControllerQueue, plus outbound LAN protocol compatibility. Apache-2.0; original Mika copyright/NOTICE retained. [Source record](third_party/phongent/UPSTREAM.md). |
| [OpenTasker](https://github.com/SysAdminDoc/OpenTasker), SysAdminDoc | `a3b4fda4942eb61e9c81290d0ff390ad1f2160b8` | CooldownReservations reused for atomic reservation and persisted cooldown restoration. MIT, Copyright 2026 SysAdminDoc. [Reuse record](third_party/opentasker/UPSTREAM.md). |
| [MacroDroid collection](https://github.com/BespredeL/MacroDroid), Aleksandr Kireev / BespredeL | `46bd349236f20879d2a68fa9cd57ad0c8a98de7c` | Eleven `.macro` templates for paused import/review; four webhook capability URLs in three templates are replaced with inert placeholders for publication. MIT, Copyright 2009 Aleksandr Kireev. [Source record](third_party/macrodroid/UPSTREAM.md). |

Mika/Phongent's full agents, sandbox/root filesystems and upstream avatar/animation assets are not bundled. OpenTasker's complete automation engine is not bundled. The MacroDroid collection is not the proprietary MacroDroid app. Companion faces and local team presentation are original Hermes UI work; no Grok/GPT character artwork is included.

## Mola / Android Auto compatibility

Reference: [moris545454/araba-](https://github.com/moris545454/araba-), pinned to `3f45ad3557a25822c2a3a9f152f32ef82f57e005`. Its repository GPL-3.0 text is retained in [third_party/mola/LICENSE](third_party/mola/LICENSE).

The delivered local preview used the unmodified `fermata/lib/auto/aauto.aar` as `app/libs/mola-aauto.aar`, SHA-256 `99337c3b591ac9670c12b508da38886aedba61dd494f39f5f166f02580ec584b`. That local build input is deliberately not published with this source update. It retains its upstream Google SDK package names. This records origin: a repository-wide GPL text must not be mistaken for a separate Google redistribution grant for a separately authored SDK binary. Consult the [provenance record](third_party/mola/UPSTREAM.md) when evaluating distribution.

Hermes's parked view, host caller gate and consent handling are original implementations. Fermata player/UI source and Xposed binaries were not imported. Reference compatibility is not Google endorsement or real-car acceptance.

## Runtime and voice components

| Component | Role / attribution |
|---|---|
| Android, AndroidX, Jetpack Compose, Material, CameraX, Car App Library | Android UI/lifecycle, camera and car APIs; each resolved artifact retains its own notices. |
| [Kotlin](https://github.com/JetBrains/kotlin), kotlinx libraries | Runtime, coroutines and JSON serialization; Apache-2.0 projects. |
| [OkHttp](https://github.com/square/okhttp), Okio | HTTP/WebSocket transport; Apache-2.0 projects. |
| [Shizuku](https://github.com/RikkaApps/Shizuku), RikkaApps | API/provider 13.1.5, Apache-2.0. The manager/server is not embedded. |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) contributors | Speech runtime AAR 1.13.8. [Upstream Apache-2.0 license](third_party/sherpa-onnx/LICENSE); separately bundled components keep their own terms. |
| [eSpeak NG](https://github.com/espeak-ng/espeak-ng) contributors | Present in the speech AAR. [GPL-3.0 text](third_party/espeak-ng/COPYING); not relicensed by sherpa-onnx's Apache license or this project's MIT license. |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime), Microsoft contributors | Native runtime and Java bridge, MIT. [EMA/ORT record](app/libs/EMA-ORT-NOTICE.md); bridge license retained in its AAR. |
| [EMA Lightning](https://github.com/canberk7/ema-lightning) | Optional speech pipeline integration; source/model attribution recorded in [EMA/ORT notes](app/libs/EMA-ORT-NOTICE.md) and model downloads. |
| [openWakeWord](https://github.com/dscripka/openWakeWord), David Scripka and contributors | Optional wake-word pipeline. Code and pretrained weights can have different licenses; inspect the selected model's terms. |
| [three.js](https://github.com/mrdoob/three.js) authors | r147 in the existing arena assets; [MIT notice](app/src/main/assets/arena/LICENSE-three.txt). |
| [websocket-client](https://github.com/websocket-client/websocket-client) contributors | Optional Dashboard adapter transport, pinned to 1.9.0; Apache-2.0 project. |
| JUnit and Robolectric | Test infrastructure; upstream terms apply to the test dependencies. |

Downloaded Piper voices, EMA resources and other weights are not relicensed by an app or inference-engine license. Nefes weights are not included. Separately installed ShizuCallRecorder is an optional interoperability target, not embedded source.

## Independent design inspiration

The following acknowledgements are retained from the earlier implementation records; they describe inspiration rather than copied source:

- [ShawnPana/phone-harness](https://github.com/ShawnPana/phone-harness): bounded screen waits/scrolling, permission diagnostics, explicit handoff and visible failures.
- [milind-soni/OpenMausBot](https://github.com/milind-soni/OpenMausBot): centralized approval inbox and configurable bot UX.
- [Chevey339/kelivo](https://github.com/Chevey339/kelivo): per-tool approval presentation.
- [cedar-panda/Holt](https://github.com/cedar-panda/Holt): character-oriented bot configuration.
- [Weltenwanderer1/ai-buddy](https://github.com/Weltenwanderer1/ai-buddy): user-configured persona/self-image concepts.

## License scope

[LICENSE](LICENSE) covers first-party Hermes Mobile code. Upstream copyright, LICENSE and NOTICE texts remain under `third_party/` and `app/src/main/assets/licenses/`. The larger [historical dependency inventory](app/src/main/assets/THIRD-PARTY-NOTICES.txt) is retained; it is not a new attestation for every current transitive artifact. [THIRD_PARTY.md](THIRD_PARTY.md) indexes the additions.

Credits do not replace source-distribution, model or SDK obligations. Do not describe a combined binary containing these components as exclusively MIT-licensed. All trademarks belong to their respective owners; no affiliation or endorsement is implied.

Publication adaptation: nineteen private IPv4 address occurrences in IntruderCam, Remote_Filemanager and Wake_PC were replaced with the documentation address `192.0.2.1`. Configure your own network targets before use. Remote file-management templates are source examples, are imported paused, and are not executed by publication.
