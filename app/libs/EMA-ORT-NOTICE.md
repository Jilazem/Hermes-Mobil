# EMA Android runtime

`onnxruntime-java-bridge-1.28.0.aar` contains official ONNX Runtime 1.28.0 Java classes and its JNI sources rebuilt with Android NDK r27d (API 26, 16 KB alignment) against the existing sherpa-onnx 1.13.8 runtime (ORT 1.28.2, C API 28). No second libonnxruntime.so is packaged, no binary is patched and no pickFirst rule hides a conflict.

Rebuild: `relay/build_ort_java_bridge.py --help`. Inputs are the official Microsoft v1.28.0 GitHub source archive, Maven Central onnxruntime-android 1.28.0 AAR, current sherpa AAR, NDK and SDK Android JAR. ORT is MIT licensed; LICENSE is embedded in the bridge AAR. The resulting AAR SHA-256 is b9c96beef8d45d50e730849113986d328e8585650f20e6fa1ed1bea446e424b2 (ZIP timestamps can differ on rebuild).

EMA Lightning 1.0.1 is Apache-2.0: https://github.com/canberk7/ema-lightning. The Android pipeline in EmaOfflineEngine ports its text planning, four acoustic steps and overlapping waveform windows. The source/model attribution and full license accompany the downloadable model. Weights are pinned by immutable Git commit, individual sizes and SHA-256 inside EmaModelStore.

The Android Turkish frontend handles Turkish casing, basic numbers, times and common abbreviations; it does not yet reproduce every specialised normalizer-tr notation rule. It uses Java Gaussian noise, so a numeric seed does not produce identical samples to PyTorch's RNG. The voice/model is unchanged.
