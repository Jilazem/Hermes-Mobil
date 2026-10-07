"""Hermes command TTS provider: file input avoids speech text in process arguments."""
import argparse
from pathlib import Path
import os
import subprocess
import tempfile
import hermes_ema_adapter


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--format', choices=['wav', 'ogg', 'opus', 'mp3', 'flac', 'aac'], default='ogg')
    args = parser.parse_args()
    text = Path(args.input).read_text(encoding='utf-8').strip()
    wav = hermes_ema_adapter.synthesize_wav(text)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    if args.format == 'wav':
        output.write_bytes(wav)
        return
    ffmpeg = os.environ.get('EMA_FFMPEG', '/Volumes/EX/007-HERMES-M4-LIVE/20-ARACLAR/ffmpeg/ffmpeg')
    codecs = {'ogg': 'libopus', 'opus': 'libopus', 'mp3': 'libmp3lame', 'flac': 'flac', 'aac': 'aac'}
    with tempfile.TemporaryDirectory() as temp:
        src = Path(temp) / 'input.wav'
        src.write_bytes(wav)
        subprocess.run([ffmpeg, '-y', '-v', 'error', '-i', str(src), '-c:a', codecs[args.format],
            '-f', args.format if args.format != 'aac' else 'adts', str(output)],
            check=True, capture_output=True, timeout=30)


if __name__ == '__main__':
    main()
