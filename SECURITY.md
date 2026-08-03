# Security

## Reporting

Open a [private security advisory](https://github.com/Jilazem/Hermes-Mobil/security/advisories/new)
rather than a public issue. Include the app build, the server version from
Dashboard → Status, and what you observed.

## What this app holds

Server profiles, session tokens, and any optional model key live in
`EncryptedSharedPreferences` backed by the Android Keystore. They are never
written to plain files, never logged, and never sent anywhere except the
server you configured.

The diagnostics log redacts secrets **as entries are written**, not when they
are displayed — filtering at display time means one missed path leaks. If you
add a new log call that can carry a token, add a case to
`DiagLog.redact` and a test alongside it.

## Deliberate limits

These are choices, not omissions:

- **The dialer opens; the call is not placed.** Same for SMS — a draft is
  prepared and the user sends it.
- **No accessibility service.** Android's Advanced Protection closes that API
  to automation apps. Deep control goes through Shizuku, which is off by
  default and requires an explicit ADB grant.
- **`phone_shell` has no command allowlist.** A partial filter creates false
  confidence; enabling Shizuku is itself the consent boundary.
- **sparkDash has no authentication of its own**, so its port is never exposed
  directly. Remote access goes through the agent's reverse proxy, which
  requires the session token and permits only GET.
