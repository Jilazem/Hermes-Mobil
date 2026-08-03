# Tested server version

The app is developed and tested against this Hermes server build:

```text
version         0.19.1
release_date    2026.7.30
config_version  33
commit          844411da864d799bbed4f98423f821435975fad0
```

The upstream agent does not guarantee API stability yet. A newer or older
server can break individual panels while the rest of the app keeps working.
**Include your server version when reporting a bug** — `/api/status` returns
it, and the app shows it under Dashboard → Status.

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
