# Security

## Reporting

Open a [private security advisory](https://github.com/Jilazem/Hermes-Mobil/security/advisories/new)
rather than a public issue. Include the app build, the server version from
Dashboard → Status, and what you observed.

## What this app holds

Server profiles, session tokens, and any optional model key live in
`EncryptedSharedPreferences` backed by the Android Keystore. The app is designed to keep them out of plain exports and redact them from diagnostics. Review any exported logs before sharing. Credentials are used with the configured server/provider paths; this is not a guarantee about the logging policy of an external service.

The diagnostics log redacts secrets **as entries are written**, not when they
are displayed — filtering at display time means one missed path leaks. If you
add a new log call that can carry a token, add a case to
`DiagLog.redact` and a test alongside it.

## Deliberate limits

These are choices, not omissions:

- **The dialer opens; the call is not placed.** Same for SMS — a draft is
  prepared and the user sends it.
- **Optional accessibility service.** HermesAccessibilityService can read screen text, perform gestures, type and capture screenshots after the user explicitly enables it in Android Accessibility settings. Disable it there to revoke access. Shizuku is a separate optional privilege boundary, off by default and requiring an explicit ADB grant.
- **`phone_shell` has no command allowlist.** A partial filter creates false
  confidence; enabling Shizuku is itself the consent boundary.
- **sparkDash has no authentication of its own**, so its port is never exposed
  directly. Remote access goes through the agent's reverse proxy, which
  requires the session token and permits only GET.

## v7 phone and media boundaries

Authorized phone automation can perform real writes, including supported messaging actions. Do not assume every write receives a new confirmation: review the enabled bridge permissions, read-only/write settings and approval coverage. Revoke the bridge and Android accessibility/notification access when no longer needed.

The HTML document viewer disables JavaScript, network loads and local file access. Authenticated media downloads do not follow redirects and use bounded temporary storage. Shizuku remains a separate authorization boundary; its manager/server is not embedded. Driving restrictions are not bypassed.
