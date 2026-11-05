# Source publication checks

This update publishes source and documentation only. The Mola SDK AAR, APKs, signing keys, private exports, raw phone screenshots and audit logs are excluded. An authorized local SDK is a prerequisite for reproducing the full preview build.

Before publication, the changed text payload was checked for private-key headers, OpenAI/GitHub/Google/Telegram key shapes, JWTs, credential-like literals, deployment identifiers and capability-style webhook URLs.

Four embedded webhook capability URLs were found in three imported macro templates: Parental_Control (2), Simple_Telegram_Bot (1) and Telegram_Secretary (1). They were replaced with inert `https://example.invalid/REDACTED-WEBHOOK` placeholders. The macro JSON parses after replacement. Upstream authors, pinned revision and MIT license are retained, and the adaptation record documents the sanitization. Configure your own endpoints when reviewing a template; imports remain paused.

The remaining credential-like matches were classified: macro apiKey values are variable placeholders, not credential values; backend authentication/secret-filter tests use fabricated local test data. One Google-key-shaped string belongs to an unchanged redaction-test file already present in the public base and is not a new publication change.

Kotlin, XML, JavaScript and backend implementation sources were compared with the delivered 7.6.1 source snapshot after newline normalization. Only the six explicitly sanitized macro assets differ among the checked implementation inputs. New documentation, retained notices and removal of a machine-specific Gradle JDK path are publication changes, not new device-feature claims.

These checks reduce accidental disclosure risk; they are not a claim that a pattern scanner can prove the absence of every kind of sensitive data. Binary licensing remains separate from source attribution and no SDK binary is published by this update.

Publication adaptation: nineteen private IPv4 address occurrences in IntruderCam, Remote_Filemanager and Wake_PC were replaced with the documentation address `192.0.2.1`. Configure your own network targets before use. Remote file-management templates are source examples, are imported paused, and are not executed by publication.
