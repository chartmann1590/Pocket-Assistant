# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app (`:app`) built with Gradle Kotlin DSL.

- App code: `app/src/main/java/com/charles/pocketassistant/`
- Feature/domain grouping: `ui/`, `data/`, `ai/`, `ocr/`, `worker/`, `di/`, `util/`, `domain/`
- Android resources and manifest: `app/src/main/res/`, `app/src/main/AndroidManifest.xml`
- Build configuration: `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`
- Generated outputs: `build/`, `app/build/` (do not edit these)

## Build, Test, and Development Commands
Run commands from the repository root:

- `./gradlew assembleDebug` (or `gradlew.bat assembleDebug` on Windows): compile a debug APK.
- `./gradlew installDebug`: install debug build on a connected device/emulator.
- `./gradlew testDebugUnitTest`: run JVM unit tests in `app/src/test` (when present).
- `./gradlew connectedDebugAndroidTest`: run instrumentation/UI tests in `app/src/androidTest` (device required).
- `./gradlew lintDebug`: run Android lint checks for the debug variant.

## Coding Style & Naming Conventions
Use Kotlin official style (`kotlin.code.style=official`) with 4-space indentation and idiomatic Kotlin patterns.

- Types/files: `PascalCase` (for example `LocalModelManager.kt`)
- Functions/variables: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Compose screens end with `Screen` (for example `ImportScreen.kt`); ViewModels live in `ui/` and are named `*ViewModel`.
- Keep package boundaries aligned with feature areas (`ai`, `data`, `ui`) rather than creating cross-cutting utility dumps.

## Testing Guidelines
Testing dependencies are configured (JUnit4, AndroidX test, Espresso, Compose UI test), but test source folders are currently absent.

- Add unit tests under `app/src/test/...` with names like `ClassNameTest`.
- Add instrumentation/Compose tests under `app/src/androidTest/...` with names like `FeatureNameInstrumentedTest`.
- Prefer fast unit tests for parsing/routing/repository logic and instrumentation tests for end-to-end UI flows.

## Commit & Pull Request Guidelines
Git metadata is not available in this workspace snapshot (`.git` is missing), so follow this baseline:

- Commit messages: imperative and scoped, e.g., `feat(ai): add ollama connection retry`.
- Keep commits focused; avoid mixing refactors with behavior changes.
- PRs should include: summary, testing performed (commands/device), screenshots for UI changes, and linked issue(s) when applicable.

## Security & Configuration Tips
- Keep machine-specific settings in `local.properties` (`sdk.dir`) and never hardcode secrets/tokens.
- Validate Ollama endpoints and network settings through in-app settings rather than source constants.
