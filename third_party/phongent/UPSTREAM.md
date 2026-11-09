# Phongent source record

- Repository: https://github.com/mylea1/phongent
- Branch: `main`
- Reviewed and pinned commit: `0b57e35d4e4c8460fdd87fcd33f751f764e7423d`
- Commit date: 2026-11-01 21:00:15 +0800.
- Remote HEAD and local shallow checkout initially matched on 2 November 2026.
- A later read on the same date observed main HEAD
  `cb58c10425a870d189ef39b8cc3dd1c8e8155d3e`. That newer commit was not checked
  out or reviewed; this integration record remains pinned to `0b57e35`.
- Upstream is based on https://github.com/wangzan101/Mika.
- Upstream `LICENSE`: Apache License, Version 2.0.
- Upstream `NOTICE`: `Mika / Copyright 2026 wangzan101`.

## Hermes adaptations

`core/src/main/kotlin/com/wangzan101/mika/core/run/ControllerQueue.kt` is the
source for the renamed `DurableControllerQueue`/`ControllerTask` component.
The Hermes adaptation retains save-before-dispatch, idempotent command IDs,
bounded history and uncertain-on-restart behavior. Localized copy and
additional validation are Hermes changes. The source header records these changes.

The outbound `PhongentRemoteClient` implements the upstream LAN wire contract
from `app/src/main/java/com/wangzan101/mika/remote/{LanAccess,RemoteBridge,
RemoteTaskQueue,FleetController}.kt`; it does not embed Phongent's phone runtime.

The original LICENSE and NOTICE are preserved next to this record. They must
also accompany redistributed binaries containing adapted code; the app's
license assets or a companion distribution should include these notices.

## Deliberately excluded upstream resources

No Aora Bot / Mood Mates avatar files, animation engine, sandbox binary,
root filesystem, PRoot or bundled runtime were copied. Upstream third-party
resources have their own licenses, including GPL/LGPL and community/commercial
terms, and are not relicensed by Phongent's Apache-2.0 source license.

Upstream source was read but not built or run in this task. A successful Hermes
contract test would verify the adapter against a controlled HTTP fixture; it
would not prove physical-device permissions, pairing approval or phone execution.
