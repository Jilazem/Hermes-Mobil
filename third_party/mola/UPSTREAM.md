# Mola legacy projection compatibility reference

User-selected source: https://github.com/moris545454/araba-
Revision: `3f45ad3557a25822c2a3a9f152f32ef82f57e005`.
Repository license: GPL-3.0, preserved in LICENSE and APK assets/licenses.

The unmodified `fermata/lib/auto/aauto.aar` is included as `app/libs/mola-aauto.aar`.
SHA-256: `99337c3b591ac9670c12b508da38886aedba61dd494f39f5f166f02580ec584b`.
Its Google SDK package names are retained. No Xposed binary or Fermata player/UI
source has been imported. The native Hermes view, host caller gate and consent
integration are original implementation against the compatibility SDK contract.

This is a preview compatibility path, not Google endorsement or a guarantee that
any specific Android Auto build accepts the installation. The ordinary Car App
Library path remains selectable separately. The user-selected repository itself
documents device-dependent installer requirements and lacks real-car acceptance
for the referenced release. Its README statements are source evidence, not a
verification result for Hermes.
