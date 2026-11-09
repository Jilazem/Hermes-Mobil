# Makrolar — Hermes Mobil 7.1

Modüller > Makrolar is an embedded local engine. Four native action kinds:
local notification, short vibration, media volume percentage, flashlight on/off.
Manual execution and charging/unplugged/headset/unlocked broadcasts are supported.
Automatic rules require both their own enable switch and the master switch.
Imports and edited rules start disabled. Local macros run independently of phone
agent credentials and its legacy pause flag. Service lifetime cancellation is
checked before each step; permission checks run before the first side effect.
Sequences stop on the first failure and report partial completion; they are not
transactions and do not roll back volume or flashlight changes.

The monitor is a user-started special-use foreground service with a persistent
stop notification. There is no boot receiver, polling loop, remote shell, SMS
sender, Telegram credential ingestion or hidden background app launcher.
After reboot, open Makrolar to resume an enabled monitor. Android force-stop
also requires reopening the app. Macros do not use the bot/phone WebSocket token.

OpenTasker's original MIT CooldownReservations provides atomic admission;
deadlines persist across service recreation. Run history stores only macro ID,
completion count and exception class, never action values or imported source.
Each foreign record retains its original item and bundle metadata privately;
single-item imports retain the exact input. Deleting a sibling no longer removes
the remaining item's source. This review JSON can be exported with the disabled
record. Unsupported MacroDroid/OpenTasker graphs cannot run.
Use a separate native adaptation to select a supported trigger and actions.

JSON imports are bounded to 1 MiB, at most 100 rules and 20 actions per native
rule. The small plaintext file contains local rules and private source JSON;
avoid storing credentials in macro text. Export occurs only through explicit
Android document selection. This is not OpenTasker's encrypted Room engine.

In 7.1.1, interrupted atomic-file backups are recovered before reads or writes.
Corrupt originals/backups stop a save rather than being overwritten. A disabled
notification channel blocks the entire sequence before any side effect.
Automatic events use one worker with at most 16 waiting events; a full queue is
reported in history. Stopping/destroying the monitor drops waiting events and
signals an in-progress rule to stop before its next action. Broadcast callbacks
do not perform camera/audio work or wait for the execution lock on the UI thread.
