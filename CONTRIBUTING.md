# Contributing

Issues and pull requests are welcome. A few rules that come from bugs this
project already shipped:

1. **Never invent an endpoint or a field shape.** Read it from a running
   server or from the gateway source. A plausible-looking API contract that
   does not exist fails silently in a list that just comes back empty.
2. **Decode tolerantly.** Unknown keys are ignored, optional fields are
   nullable with defaults, and anything whose shape has already changed once
   is a `JsonElement` flattened at the display layer.
3. **A panel that fails must not take the app down.** Catch per section and
   log the exception through `DiagLog` — "request failed" alone never
   identifies the field.
4. **User-facing strings are bilingual.** Wrap them in `S.t2(tr, en)`, or
   `tr(tr, en)` outside Compose. Don't audit this by grepping the source:
   there are three bilingual idioms and a source scan produces mostly false
   positives. Run the app and read the screens.
5. **Log messages stay English** and carry codes — a WebSocket close code
   identifies a problem that prose does not.
6. **No new dependencies without a reason that names the alternative.** The
   local session cache is a JSON file rather than Room because it stores one
   list with no queries, relations, or migrations.
7. **Phone actions resolve on-device.** The agent runs on another machine and
   cannot reach the phone. New actions belong in `PhoneIntent`/`PhoneTools`,
   with a test, and must also be declared in the relay — the model can only
   call tools it has been told about.
8. **Before publishing screenshots**, turn on Settings → Developer → Demo
   mode. Measured with it off, 9 of 14 screens show real server data.
