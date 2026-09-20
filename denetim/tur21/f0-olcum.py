# Tur-21 madde 5 kanıtı: kadın sesi f0 doğrulaması (otokorelasyon medyanı).
# Kullanılan WAV'lar scratch'te üretilmişti (piper + referans sesler).
# Koşum: scratch venv-sherpa python (numpy yeterli).
import wave, sys, os
import numpy as np

def median_f0(path):
    w = wave.open(path); sr = w.getframerate()
    a = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768.0
    f0s = []; win = int(sr * 0.04); hop = int(sr * 0.02)
    for i in range(0, len(a) - win, hop):
        seg = a[i:i+win]
        if np.sqrt((seg**2).mean()) < 0.02:
            continue
        seg = seg - seg.mean()
        spec = np.fft.rfft(seg, n=2*win); ac = np.fft.irfft(spec * spec.conj())[:win]
        if ac[0] <= 0:
            continue
        lo = int(sr/350); hi = min(int(sr/70), win-1)
        idx = lo + int(np.argmax(ac[lo:hi])); f0 = sr/idx
        if 60 < f0 < 400:
            f0s.append(f0)
    return float(np.median(f0s)), int(np.percentile(f0s, 25)), int(np.percentile(f0s, 75)), len(f0s)

for f in sys.argv[1:]:
    m, p25, p75, n = median_f0(f)
    print(f"{os.path.basename(f)}: median={m:.1f} Hz  p25={p25}  p75={p75}  (pencereler={n})")
