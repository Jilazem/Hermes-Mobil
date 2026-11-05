# Mika adaptation record

- Project: https://github.com/wangzan101/Mika
- Pinned revision: `ebb0e44750d80aabb6b642c69908825d6b1a8107` (version 0.1.33).
- Original copyright: 2026 wangzan101.
- License: Apache License 2.0; unmodified `LICENSE` and `NOTICE` are retained here and included under APK assets `licenses/`.

Adapted and actively used source:

1. `core/src/main/kotlin/com/wangzan101/mika/core/remote/RemoteAccess.kt`, `privateAddress`: adapted in Hermes `data/remote/RemoteEndpointPolicy.kt` for outbound cleartext endpoint validation. The private IPv4, carrier-grade VPN and IPv6 scope checks remain; public HTTPS relay endpoints use normal TLS verification.
2. `core/src/main/kotlin/com/wangzan101/mika/core/remote/RemoteTasks.kt` and `RemoteTaskService.kt`: task-state vocabulary and terminal/interaction distinctions adapted into `MikaTaskStatus` and the strict outbound client DTO mapping.

Hermes-specific implementation: typed outbound HTTP client, encrypted connection registry, bounded response handling, no write replay, reverse-proxy prefix handling, Turkish errors and contract tests. These changes do not import Mika's complete on-device agent, phone-control runtime, sandbox or artwork. A separate Mika installation retains responsibility for its own permissions and configured model.
