#!/usr/bin/env python3
# Mock gateway'e temiz WS istemcisi: handshake + session.active_list + prompt.submit
import socket, base64, os, json, time, struct

TOKEN = open('/tmp/tur19-token.txt').read().strip()
key = base64.b64encode(os.urandom(16)).decode()
s = socket.create_connection(('127.0.0.1', 8198), timeout=8)
req = ("GET /api/ws?token=" + TOKEN + " HTTP/1.1\r\n"
       "Host: 127.0.0.1:8198\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
       "Sec-WebSocket-Key: " + key + "\r\nSec-WebSocket-Version: 13\r\n\r\n")
s.sendall(req.encode())
resp = b''
while b'\r\n\r\n' not in resp:
    resp += s.recv(4096)
print('HANDSHAKE:', resp.split(b'\r\n')[0].decode())

def send(obj):
    data = json.dumps(obj).encode()
    n = len(data)
    hdr = b'\x81'
    if n < 126:
        hdr += bytes([0x80 | n])
    else:
        hdr += bytes([0x80 | 126]) + struct.pack('>H', n)
    mask = os.urandom(4)
    masked = bytes(data[i] ^ mask[i % 4] for i in range(n))
    s.sendall(hdr + mask + masked)

def recv():
    h = s.recv(2)
    if len(h) < 2:
        return None
    op = h[0] & 0x0F
    ln = h[1] & 0x7F
    if ln == 126:
        ln = struct.unpack('>H', s.recv(2))[0]
    elif ln == 127:
        ln = struct.unpack('>Q', s.recv(8))[0]
    buf = b''
    while len(buf) < ln:
        c = s.recv(ln - len(buf))
        if not c:
            break
        buf += c
    return op, buf

send({"jsonrpc": "2.0", "id": 1, "method": "session.active_list", "params": {}})
op, b = recv()
print('ACTIVE_LIST ->', b[:120])
send({"jsonrpc": "2.0", "id": 2, "method": "prompt.submit",
      "params": {"session_id": "20260920_080840_7d93f7", "text": "merhaba"}})
op, b = recv()
print('SUBMIT-ACK ->', b[:80])
for i in range(3):
    r = recv()
    if r is None:
        break
    print('EV', i, '->', r[1][:70])
s.close()
print('OK')
