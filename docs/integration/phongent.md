# Phongent LAN integration — Hermes Mobile v7

Protocol source: [mylea1/phongent at 0b57e35](https://github.com/mylea1/phongent/tree/0b57e35d4e4c8460fdd87fcd33f751f764e7423d).
The contract is pinned to that reviewed commit; upstream main advanced during
the work. See `third_party/phongent/UPSTREAM.md` for the later unreviewed HEAD.
This is a separate protocol from Hermes's gateway and custom Bot API, and from
Mika's `/api/v1` remote-task API.

## Device prerequisites and discovery

The execution device must run a compatible **Phongent build** with its LAN
foreground service enabled. The upstream README warns that an ordinary Mika
APK may not expose fleet-control endpoints. Upstream Android minSdk is 30;
Hermes's outbound HTTP adapter does not import that Android runtime.

`LanService.kt:38-48` creates `ServerSocket(0)` and advertises
`_mika-agent._tcp.` over Android NSD. TXT fields are `id`, `name` and
`transport=tcp-http-v1`. The LAN port is dynamic; do not assume port 18765.
The separate USB/local bridge binds `127.0.0.1:18765` and uses an app-private
`remote-token` file (`RemoteBridge.kt:31-35`). Do not copy that token or confuse
the computer's own listener on 18765 with an Android endpoint.

## Physical approval and credentials

LAN requests are plain HTTP inside the trusted LAN in the upstream design.
Hermes's shared remote transport accepts HTTP only for explicit private or
loopback IPs and permits HTTPS endpoints. It disables redirects and write retry.

1. `POST /pair` without Authorization:

   ```json
   {"requestId":"<two UUID strings concatenated: 72 characters>","name":"Hermes Mobile"}
   ```

   Response is normally HTTP 202, `{"status":"pending"}`.
2. The user grants or rejects the connection on the Android device itself.
   The offer expires after 90 seconds; notification permission is required.
3. `GET /pair/<requestId>` without Authorization returns `pending`, `denied`,
   `expired`, or `approved` with a `token` field. Only the approved token is
   stored through Hermes's encrypted connection registry.
4. All subsequent endpoints require `Authorization: Bearer <token>`.

`LanAccess.kt:26-84` validates the 64–80 character request ID, rate limits
offers, performs constant-time bearer comparisons and returns the token only
after approval. Never log response bodies or put the bearer in a URL/export.

## Read capabilities before writing

`GET /state` returns `phase`, `detail`, `runId`, `commandId`,
`conversationId` / `currentConversationId`, `taskQueueVersion`, `queuedTasks`
and exchanges. Phases include `idle`, `running`, `waiting`, `takeover`,
`confirmation`, `completed`, `failed`, `cancelled`.

- If `taskQueueVersion == 1`, use `/tasks` for new work.
- For an older compatible execution build without that version, use the
  `/command` task action.
- A generic `waiting` phase is not proof of a user-paused task. Resume is only
  available when the current exchange is actually `paused` and the same
  `runId` is still selected.

## Durable queue and exact conversation binding

`GET /tasks` returns an `items` array. Each row has `id`, `text`,
`conversation`, `state`, `error`, `result`.

`POST /tasks` enqueues:

```json
{"id":"<stable UUID>","action":"task","text":"<goal>","conversationId":"<expected conversation>"}
```

The receipt is `{"id":"...","status":"queued","conversationId":"..."}`.
Queued/accepted is not a completed result. The execution side checks the
expected conversation and deduplicates IDs. On a transport failure, read
`GET /tasks` for the **same ID**. Do not retry a new ID or silently fall back
to another endpoint; that can duplicate a side effect.

`ControllerQueue.kt` persists a dispatching state before invoking an executor.
On restart, dispatching/running becomes uncertain; an uncertain task blocks
new claims until its actual outcome is reconciled. Limits are 100 active
tasks, 400 terminal history rows and an 8000-character goal.

## Cancel, stop and resume are different operations

- `POST /tasks {"action":"cancel","id":"..."}` cancels only a queued task.
  A cancelled response row provides terminal confirmation.
- For currently executing work, send
  `POST /command {"id":"<new command UUID>","action":"stop","runId":"<observed run>"}`.
  The command response may say `accepted`; poll state/tasks for the terminal
  cancellation. Do not display stopped merely because the HTTP call succeeded.
- `POST /command` with `action:"resume"` requires the same observed `runId`
  and an actual held task. There is **no general pause action** in this wire API.
- `POST /tasks {"action":"stop_all"}` first prevents queued work from
  starting and asks current runs to cancel. Its `status:"accepted"` is still
  not terminal confirmation for every task.

## Other upstream capabilities

The same authenticated execution service exposes `/chats`, `/chat/<id>`,
`/knowledge`, `/plans`, `/routes`, `/workspace`, `/models/settings` and
`/events?heartbeat=30`. They should only be shown as available when supported
by the connected build. Device/group assignment is controller-local in the
upstream UI and is not automatically synchronized to another controller.

`GET /virtual/video` is a length-prefixed H.264 byte stream, not a JPEG URL.
`/virtual/input` requires a control lease plus current run/conversation/display
fences. Do not label a mock thumbnail a live view or send coordinates without
those identity checks. These phone-operation APIs are separate from Mola's
Android Auto presentation screen.

## Verification scope

`PhongentRemoteClientContractTest` uses real loopback sockets and the production
HTTP client to test physical-approval state parsing, bearer separation,
conversation binding, queue-version routing, accepted-versus-terminal
semantics and unknown-write receipt recovery without replay. Root runs the
combined Gradle checks. Neither this document nor a passing fixture proves
physical-device setup, a running model, LAN approval or Android Auto acceptance.
