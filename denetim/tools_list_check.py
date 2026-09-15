# Tur-6 denetimi: MCP stdio tools/list (initialize + initialized + tools/list)
import json, subprocess, sys

inp = "\n".join([
    '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"audit","version":"1"}}}',
    '{"jsonrpc":"2.0","method":"notifications/initialized"}',
    '{"jsonrpc":"2.0","id":2,"method":"tools/list"}',
]) + "\n"

proc = subprocess.run(
    [sys.executable, "/Users/gokhanuzman/007-HERMES/10-MCP-SERVERS/phone-bridge/phone_mcp_server.py"],
    input=inp, capture_output=True, text=True, timeout=60,
)
tools = []
for line in proc.stdout.splitlines():
    line = line.strip()
    if not line:
        continue
    d = json.loads(line)
    if d.get("id") == 2:
        tools = [t["name"] for t in d.get("result", {}).get("tools", [])]

old17 = ["phone_status","phone_battery","phone_notifications","phone_calendar","phone_location",
         "phone_contacts","phone_clipboard_read","phone_notify","phone_speak","phone_open_app",
         "phone_navigate","phone_clipboard_write","phone_dial","phone_sms_draft","phone_timer",
         "phone_flashlight","phone_media"]
print("TOOL_COUNT=", len(tools))
print("ESKI17_EKSIK=", [t for t in old17 if t not in tools])
print("YENILER=", [t for t in tools if t not in old17])
if proc.stderr.strip():
    print("STDERR=", proc.stderr.strip()[:400])
