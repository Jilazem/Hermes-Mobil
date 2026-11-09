# Tested server version

The app is developed and tested against this Hermes server build (Hermes Mobil v7.8.1 / versionCode 36):

```text
version         0.23.0
release_date    2026.9.24
config_version  35
commit          a08f2418cd82760986c43c8db97c85afd85bd18b
ports           9120 (Dashboard / Caddy reverse proxy), 9150 (Webapp & Gateway API/WS)
```

The upstream agent does not guarantee API stability yet. A newer or older
server can break individual panels while the rest of the app keeps working.
**Include your server version when reporting a bug** — `/api/status` returns
it, and the app shows it under Dashboard → Status.

## Port Architecture (v7.8.0 / v7.8.1)

- **Port 9120:** Hermes Dashboard UI (Caddy reverse proxy; WebSocket upgrade via Caddy).
- **Port 9150:** Hermes Webapp & Gateway (`/api/status`, `/api/auth/ws-ticket`, `/api/ws`, `/api/sessions`).
- Dynamic scheme preservation: HTTPS/WSS are never downgraded to HTTP/WS; custom dashboard and webapp URLs are supported in `ServerProfile`.

## WebSocket & Event Deduplication (v7.8.1 P0)

Gateway events are bound to authoritative server IDs:
- `turn_id`: Prompt turn streams (`message.start`, `message.delta`, `message.complete`) prevent turn re-creation across reconnects.
- `offset`: Dropped re-sent chunks already applied in streamed segments.
- `tool_id`: Tool call and tool result events bind to the exact same card (`tool_${sessionId}_${toolId}`), eliminating duplicate working/completed cards.
- `run_id` / `subagent_id`: Stable tracking for task starts, finishes, and subagent progress.

## Why this file exists

This client decodes JSON from a server that is still changing shape. Twice a
field that used to be a string started arriving as an object, and both times
the panel crashed instead of degrading:

- `skills[].category` and `skills[].description` — string → object
- `mcp[].tools` — array → `{"include": [...]}`

Neither failure was visible in a stack trace the user could see; the list
simply came back empty. The rules below exist because of those two bugs.

## Decoding rules

1. **Never invent an endpoint or a field shape.** Read it from the running
   server or from the gateway source
   (`hermes_cli/web_routers/`, `web_models.py`), not from a guess about what
   a sensible API would look like.
2. **Decode tolerantly.** `ignoreUnknownKeys = true`, `isLenient = true`, and
   every optional field is nullable with a default. An unexpected key must
   never fail a response.
3. **Use `JsonElement` for fields whose shape is not stable**, then flatten
   at the display layer (`flattenJson`). A field that has already changed
   shape once should be assumed to change again.
4. **Log the decode failure.** `DiagLog` records which panel failed and with
   which exception; "request failed" alone does not identify the field.
5. **A panel that cannot decode must not take the app down.** Each dashboard
   section fails on its own.

## Endpoints that do not exist on this server

Confirmed 404 — do not add UI for them without checking first:

```text
/api/plugins   /api/channels   /api/keys   /api/docs   /api/kanban
```

## Endpoint names that are easy to get wrong

```text
POST /api/cron/jobs/<id>/trigger     (not /run)
PUT  /api/cron/jobs/<id>             body: {"updates": {...}}
```

Cron schedules are plain strings (`"0 1 * * 0"`), not structured objects.
