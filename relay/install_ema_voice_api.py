"""Install a reviewable EMA hook into the existing Hermes voice_api.py.
Usage: python install_ema_voice_api.py /path/to/voice_api.py
Creates an immutable backup and copies the adjacent adapter module.
"""
from pathlib import Path
import shutil
import sys


def patch(source):
    if 'import hermes_ema_adapter' in source:
        return source
    changes = [
        ('ARACLAR = Path(', 'import hermes_ema_adapter\n\nARACLAR = Path('),
        ('MOTORLAR = {', 'MOTORLAR = {\n    "ema": {"url": hermes_ema_adapter.endpoint(), "baslat": None, "durdur": None,\n            "ses": None, "agir": False, "yukleme_s": 24, "tipik_rtf": 1.0,\n            "aciklama": "EMA Lightning — yeni asistan sesi"},'),
        ('os.environ.get("SES_VARSAYILAN_MOTOR", "chatterbox")', 'os.environ.get("SES_VARSAYILAN_MOTOR", "ema")'),
        ('for v in MOTORLAR.values()}', 'for v in MOTORLAR.values() if v["durdur"] is not None}'),
        ('def _saglik(url: str) -> str:\n', 'def _saglik(url: str) -> str:\n    if url == hermes_ema_adapter.endpoint():\n        return "hazir" if hermes_ema_adapter.health() else "kapali"\n'),
        ('    bilgi = MOTORLAR[ad]\n', '    if ad == "ema":\n        if not hermes_ema_adapter.health():\n            raise MotorYukleniyor("EMA servisine ulasilamadi; baska ses motoruna gecilmedi")\n        return True\n    bilgi = MOTORLAR[ad]\n'),
        ('    onb = _onbellek_yol(metin, engine)\n', '    if engine == "ema":\n        return hermes_ema_adapter.synthesize_ogg(metin, FFMPEG), False\n\n    onb = _onbellek_yol(metin, engine)\n'),
        ('"engines": {"kahya": kahya', '"engines": {"ema": _saglik(MOTORLAR["ema"]["url"]), "kahya": kahya'),
        ('"16 GB: ayni anda tek TTS motoru ikamet eder"', '"EMA ayri servis; eski motorlar kendi yoneticisi ile"'),
    ]
    for old, new in changes:
        if source.count(old) != 1:
            raise ValueError('Unsupported voice API version: expected unique hook ' + old[:50])
        source = source.replace(old, new, 1)
    compile(source, 'voice_api.py', 'exec')
    return source


if __name__ == '__main__':
    path = Path(sys.argv[1])
    updated = patch(path.read_text())
    backup = path.with_name('voice_api.pre-ema.py')
    if not backup.exists():
        shutil.copy2(path, backup)
    shutil.copy2(Path(__file__).with_name('hermes_ema_adapter.py'), path.with_name('hermes_ema_adapter.py'))
    path.write_text(updated)
    print('EMA hook installed; original backup preserved')
