# Local build dependencies

Use JDK 21, Android SDK Platform 35, Build Tools 35.0.0 and the Gradle wrapper. Configure your own SDK path in untracked `local.properties` or your environment.

## Mola compatibility SDK is not distributed

The source contains the legacy Mola compatibility path used in the local 7.6.1 preview, but this public update deliberately omits its third-party SDK binary.

To reproduce that full build, supply your own **authorized local copy** at:

```text
app/libs/mola-aauto.aar
```

Recorded SHA-256: `99337c3b591ac9670c12b508da38886aedba61dd494f39f5f166f02580ec584b`.

See [provenance](../third_party/mola/UPSTREAM.md) and [license scope](../THIRD_PARTY.md). This documentation does not grant SDK distribution rights. There is no automatic downloader, and the file is ignored by Git. A full build without this prerequisite is not claimed to succeed.

The original repository already carries its speech runtime/Java bridge dependencies. They retain their own license and component obligations. Model weights and voice downloads are separate from these runtime AARs.

## Build

After providing the required local files:

```bash
./gradlew --no-daemon --max-workers=2 testDebugUnitTest assembleDebug lintDebug
```

On Windows use `gradlew.bat`. Never publish signing keys, tokens, private exports or a locally supplied SDK just because a build succeeds. Use the same signing certificate when updating an existing installation.
