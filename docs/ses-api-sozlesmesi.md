# SES API SÖZLEŞMESİ v1 (15.09.2026) — uygulama <-> Mac ses hattı

Yerel: http://192.168.1.101:8174 · Dış (sonra eklenecek): https://hermes.winterfell07.keenetic.pro/voice-api
Kimlik: başlık `X-Hermes-Session-Token: <uygulama tokeni>` — yanlış/eksikse 403 (fail-closed).
GET /health -> {"ok":true,"stt":"acik|kapali","engines":{"kahya":"...","chatterbox":"...","kadin":"..."}}
POST /transcribe — multipart form, alan `audio` (ogg/opus önerilir, <=60sn) -> {"text":"...","lang":"tr"}
POST /synthesize — JSON {"text":"...","engine":"kahya|chatterbox|kadin"} (varsayılan kahya) -> gövde: audio/ogg (Opus) baytları
Hatalar: JSON {"error":"..."} + uygun kod. Referans: whisper-server 8171.