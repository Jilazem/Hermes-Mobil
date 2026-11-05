# Mika connection in Hermes Mobile v7

This is a real outbound adapter for Mika's task API v1, pinned against [Mika 0.1.33, ebb0e447](https://github.com/wangzan101/Mika/tree/ebb0e44750d80aabb6b642c69908825d6b1a8107). It connects to a separately installed Mika; it does not pretend to embed Mika's entire on-device runtime.

## Setup

1. Install and configure Mika on the target Android phone. Mika currently requires Android 11 for chat/web/files, and its phone-control setup requires Android 12 or newer. Hermes itself can retain its own Android 8+ minimum because it is an HTTP client.
2. On Mika, open Settings → Connect a computer and explicitly enable network access. Copy that connection's access token. Its default network port is 8765.
3. In Hermes' device registry, select Mika, provide a label, endpoint and that token. Example endpoint: `http://192.168.1.23:8765`. This is Mika's API, not Hermes' `/api/bots` custom bot service.
4. Connect/discover capabilities before sending a task. The server must advertise `api_version=1` and `tasks`.

HTTP accepts only literal private/loopback/VPN IPs or `localhost`. DNS hostnames require HTTPS. A trusted HTTPS Mika relay is supported, including an endpoint path prefix such as `https://relay.example/phone-a`. No redirects are followed. Tokens are sent only in the Authorization header and are saved through Android encrypted preferences. They are not put in a URL, diagnostic string, export, Markdown transcript or log.

For a computer connected by USB, Mika's own CLI can bootstrap its loopback access and `adb forward` its port. Hermes does not silently turn on that access or read another app's private token. On a phone with no network access, the user must explicitly enable a supported connection.

## Exact HTTP contract

Each authenticated request uses `Authorization: Bearer <phone-token>` and `Accept: application/json`; JSON writes additionally use `Content-Type: application/json`.

| Method and suffix | Request | Result |
|---|---|---|
| GET `/api/v1` | none | API version, capabilities, server version, limits |
| GET `/api/v1/tasks?limit=50` | none | `tasks` array |
| POST `/api/v1/tasks` | `{"goal":"...","conversation":"mcp-uuid (optional)"}` | HTTP 201 task snapshot |
| GET `/api/v1/tasks/{id}` | none | authoritative snapshot |
| POST `/api/v1/tasks/{id}/wait` | `{"timeout_s":30}` | snapshot and changed flag; read-only bounded wait |
| POST `/api/v1/tasks/{id}/answer` | `{"text":"..."}` | task_id + accepted |
| POST `/api/v1/tasks/{id}/steer` | `{"text":"..."}` | HTTP 202 task_id + message_id + accepted |
| POST `/api/v1/tasks/{id}/cancel` | `{}` | HTTP 202 request acknowledgement, or already-terminal status |

Goals/steers are limited to 4000 characters, answers to 2000, waits to 1–50 seconds. Advertised server limits are read as well. A continued conversation must use the exact `mcp-<uuid>` identifier previously returned by Mika.

States: `starting`, `queued`, `running`, `paused`, `waiting_for_answer`, `waiting_on_phone`, `done`, `failed`, `cancelled`. Only the final three are terminal. `waiting_for_answer` can accept a remote answer; a payment, consent, login or takeover classified as `waiting_on_phone` must be handled on the target phone. Steering never starts or resumes a task. The REST API does not expose a general-purpose pause/resume endpoint; Hermes must not fabricate one.

Cancellation's `accepted=true` is an acknowledgement, not proof of terminal cancellation. Query the same task until the authoritative status is `cancelled` or another terminal outcome. A request timeout, connection loss, malformed write acknowledgement or HTTP 5xx/redirect is treated as outcome unknown. Mutating requests have automatic retries disabled; do not resend an uncertain task automatically.

## Evidence and limits

`MikaRemoteClientContractTest` exercises real loopback HTTP against the actual Kotlin client: bearer header, prefix retention, UTF-8 goal/conversation, compatibility checks, steering/answer distinction, bounded waiting, uncertain writes and cancellation acknowledgement. `RemoteAgentConnectionStoreTest` checks registry transaction/isolation semantics with injected preferences; it is not an encryption/device acceptance claim. The production constructor uses Android EncryptedSharedPreferences.

A successful local contract test does not prove that a user's real phone has Mika installed, network access enabled, permissions granted or a configured model. Those require device acceptance. The adapter contains no provider/model API key and changes no global model configuration.

## Provenance

See `third_party/mika/UPSTREAM.md`, LICENSE and NOTICE. The private address guard and task-state/interaction semantics are source adaptations that are actively used. No upstream artwork or bundled third-party assets were copied.
