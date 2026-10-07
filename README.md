# EMA Lightning Android ONNX v1
EMA 1.0.1 acoustic + decoder weights, converted to three ONNX opset-18 CPU stages for Hermes Mobil.
Original model: https://huggingface.co/canberkkkkkk/ema-lightning at revision 7a6ba1ad216bb2f1da9863f80ac8770a6a807632. Apache-2.0, see LICENSE.
Export source: Hermes-Mobil/relay/export_ema_android.py. No retraining or voice substitution.
`manifest.json` contains sizes and SHA-256. `export-proof.json` records numerical parity against PyTorch for two different-length Turkish inputs. Android uses its own Turkish frontend and Gaussian noise generator; specialised Python normalizer-tr notation rules are not all ported.
