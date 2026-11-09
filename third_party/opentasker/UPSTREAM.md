# OpenTasker reuse

Source: https://github.com/SysAdminDoc/OpenTasker
Revision: a3b4fda4942eb61e9c81290d0ff390ad1f2160b8
License: MIT; LICENSE retained here and in APK assets/licenses.

`core/engine/src/main/kotlin/com/opentasker/core/engine/CooldownReservations.kt`
is copied without semantic changes to the same package in Hermes. It provides
atomic check-and-reserve and persisted cooldown restoration for native macro rules.
The complete OpenTasker application, 78 actions and its encrypted Room runtime
are not embedded. OpenTasker bundle JSON is preserved as a disabled review record.
