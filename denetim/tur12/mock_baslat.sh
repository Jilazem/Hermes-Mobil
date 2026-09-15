#!/bin/bash
# Tur-12 mock voice_api (tembel motorlar) — emülatör 10.0.2.2:8199
# --token-any: uygulamanın tokini bilmiyoruz; mock yalnız varlık + sha8 kaydeder.
cd /Users/gokhanuzman/hermes-workspace/wt-android-uzman
python3 tools/tur12/mock_voice_api.py \
  --port 8199 \
  --token-any \
  --ogg app/src/debug/assets/sesli/kayit.ogg \
  --log denetim/tur12/mock.log \
  --cold-seconds 25 \
  --text "Bu bir sesli mesaj denemesidir."
