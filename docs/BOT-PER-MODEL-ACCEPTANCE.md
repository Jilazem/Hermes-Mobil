# Per-bot model routing (draft)

A bot now stores an optional `provider` alongside its existing `model`, `profile` and `think_mode`. The mobile editor loads the server's read-only `/mobile-api/models` catalog and lets the user select provider/model pairs. A manual fallback remains for providers the catalog does not enumerate.

The loopback Dashboard bridge sends `model` and `provider` only with `session.create`. It does **not** write the global configuration. Resumed sessions preserve their original model. Editing a bot's model takes effect in a **new chat**, not an old one.

## Acceptance required before merge

1. Run `cd backend && python3 -m unittest discover -s bot_framework/tests -t .`.
2. Run Android `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` with documented local AAR dependencies.
3. On a live Hermes server supporting per-session `session.create` model/provider overrides, create two different bots using different providers, capture `session.status` or a reliable runtime model badge and verify each effective model. **Do not treat a selected UI value as proof.**
4. Confirm an older server either rejects an unsupported override or is explicitly marked incompatible. A server that silently ignores model/provider is **not acceptable**.
5. Verify session resume does not change the model, bot export/import roundtrips provider, and malformed providers return a clear error.
6. Verify reconnect, offline model-catalog failure and outbox/unknown-result safeguards remain operational.

## Not addressed in this draft

- Hermes-native per-bot `think_mode` semantics: backend stores this preference but Dashboard forwarding is NOT implemented; do not claim reasoning effort changed.
- Batch importing Grok contracts, mascot visuals or new phone permissions. Keep these in separate PRs.
