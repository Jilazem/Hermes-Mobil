# Third-party inventory

Updated for the 7.6.1 source publication. See [CREDITS.md](CREDITS.md) for authors, pinned revisions and the distinction between source reuse and inspiration.

| Retained location | Scope |
|---|---|
| `third_party/mika/` and APK `assets/licenses/mika-*` | Apache-2.0 LICENSE/NOTICE and adaptation record |
| `third_party/phongent/` and APK `assets/licenses/phongent-*` | Apache-2.0 LICENSE/NOTICE; Mika origin retained |
| `third_party/opentasker/` and APK `assets/licenses/OpenTasker-MIT.txt` | MIT source reuse of CooldownReservations |
| `third_party/macrodroid/` and APK `assets/licenses/MacroDroid-MIT.txt` | MIT macro templates, distinct from the MacroDroid application |
| `third_party/mola/` and APK `assets/licenses/mola-reference-GPL-3.0.txt` | GPL-3.0 reference-repository text and separately described AAR provenance |
| `third_party/sherpa-onnx/LICENSE` | Upstream Apache-2.0 license, retrieved from tag v1.13.8 |
| `third_party/espeak-ng/COPYING` | Upstream GPL-3.0 text for the eSpeak NG component bundled by the speech AAR |
| `app/libs/EMA-ORT-NOTICE.md` and bridge AAR metadata | ONNX Runtime/EMA provenance; runtime/model terms remain distinct |
| `app/src/main/assets/arena/LICENSE-three.txt` | MIT notice for three.js r147 |
| `app/src/main/assets/THIRD-PARTY-NOTICES.txt` | Historical resolved-artifact inventory, retained with its original inspection limits |

The first-party MIT license does not relicense third-party code, model weights, macro templates or SDK binaries. In particular, a reference repository's GPL license does not establish an independent vendor grant for every binary it contains. Review the Mola AAR provenance before redistributing a combined binary.

Shizuku API/provider dependencies do not embed its manager/server. The separate ShizuCallRecorder application is not bundled. Mika/Phongent sandboxes, root filesystems and upstream mascot artwork are not bundled. Nefes weights are not bundled. Downloaded voices and wake-word models need their own applicable model notices.

This index and the added upstream texts improve attribution; they do not claim that every transitive artifact, SDK binary or model has received a new redistribution audit. The historical inventory's unresolved entries remain unresolved unless backed by a newer source record.

The Mola SDK AAR is deliberately excluded from this public update and ignored by Git. Its hash/provenance and the reference repository license remain available for an operator evaluating an authorized local build input. No new APK, native SDK or private phone screenshot is distributed by this update.
