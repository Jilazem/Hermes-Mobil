"""Authenticated EMA adapter for the existing Hermes OGG voice API."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import urllib.request


def endpoint():
    return os.environ.get('EMA_URL', 'http://127.0.0.1:8176').rstrip('/')


def headers():
    token = os.environ.get('EMA_TOKEN', '').strip()
    if not token:
        path = Path(os.environ.get('EMA_TOKEN_FILE', str(Path.home() / 'Library/Application Support/HermesEMA/ema-service-token')))
        token = path.read_text().strip()
    if not token:
        raise RuntimeError('EMA token is required')
    return {'X-Hermes-Session-Token': token, 'Content-Type': 'application/json'}


def health():
    try:
        req = urllib.request.Request(endpoint() + '/health', headers=headers())
        with urllib.request.urlopen(req, timeout=3) as response:
            return json.loads(response.read(8192)).get('ok') is True
    except (OSError, ValueError):
        return False


def synthesize_wav(text):
    req = urllib.request.Request(endpoint() + '/speak', headers=headers(),
        data=json.dumps({'text': text, 'sample_rate': 48000}).encode())
    with urllib.request.urlopen(req, timeout=120) as response:
        wav = response.read(40_000_001)
    if len(wav) > 40_000_000 or not wav.startswith(b'RIFF') or wav[8:12] != b'WAVE':
        raise RuntimeError('Invalid EMA WAV response')
    return wav


def synthesize_ogg(text, ffmpeg):
    wav = synthesize_wav(text)
    # Existing clients expect OGG/Opus. Temporary content is removed on every exit.
    with tempfile.TemporaryDirectory() as temp:
        src, dst = Path(temp) / 'voice.wav', Path(temp) / 'voice.ogg'
        src.write_bytes(wav)
        subprocess.run([str(ffmpeg), '-y', '-v', 'error', '-i', str(src), '-c:a', 'libopus',
            '-b:a', '48k', '-ar', '48000', '-ac', '1', str(dst)], check=True, capture_output=True, timeout=30)
        return dst.read_bytes()
