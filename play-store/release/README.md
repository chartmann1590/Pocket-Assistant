# Release build

## TL;DR — producing a signed AAB for Play Console

```bash
./gradlew clean bundleRelease
# Windows: .\gradlew.bat clean bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

Upload that file under **Production → Create new release → App bundles** in
the Play Console.

## Signing

Signing is wired in `app/build.gradle.kts` via a root-level
`keystore.properties` file (gitignored):

```
storeFile=upload-keystore.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

`storeFile` is resolved relative to the `:app` module directory, so the
keystore itself lives at `app/upload-keystore.jks` (also gitignored via
`*.jks`).

If `keystore.properties` is missing, `bundleRelease` will still run but the
resulting bundle will be unsigned. You cannot upload an unsigned bundle to
Play Store.

### BACK UP YOUR UPLOAD KEY

If you lose `upload-keystore.jks` **or** forget its password, you lose the
ability to publish updates to this app on Google Play. There is a Play App
Signing reset process, but it is manual and slow.

Before uploading the first build:

1. Copy `app/upload-keystore.jks` to **at least two** offline locations
   (encrypted USB, password manager attachment, etc.).
2. Store the passwords from `keystore.properties` in a password manager.
3. Do not commit either file to git. The repo's `.gitignore` already
   excludes `*.jks` and `keystore.properties`.

## Versioning

`app/build.gradle.kts` currently has:

```kotlin
versionCode = 1
versionName = "1.0.0"
```

Bump `versionCode` for every upload (Play Store will reject duplicates) and
bump `versionName` on user-facing releases.

## Quick self-check before uploading

```bash
# Unit tests
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug

# Final release artifact
./gradlew bundleRelease

# Optional: inspect the bundle manifest
unzip -p app/build/outputs/bundle/release/app-release.aab BundleConfig.pb | xxd | head -30
```
