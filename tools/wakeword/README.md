# "Hey Jarvis" doğrulama betikleri

Uygulamadaki `WakeWordEngine` (Kotlin/TFLite) bu betiklerdeki akışın birebir
karşılığıdır. Sentetik Türkçe (Piper tr_TR-fettah) ve İngilizce (Piper
en_US-lessac) söyleyişlerle ölçüm:

| Yöntem | Yakalama | Yanlış alarm |
|---|---|---|
| sherpa-onnx KWS (gigaspeech, İngilizce) | EN 12/12, TR söyleyiş zayıf (~%20) | 0 / 63 sn |
| **openWakeWord hey_jarvis, eşik 0.4 + art arda 2 çerçeve** | **52/54** | **0 / 125 sn** |

Zorlayıcı olumsuz örnekler: "Hey Travis", "Harvey", "Hey Jarrett", "Her mesaj
önemlidir", "Hermione", "Hey kardeşim"…

Çalıştırma: `pip install sherpa-onnx openwakeword ai-edge-litert`, Piper
modellerini indir, `python3 oww_manual_eval.py`.
