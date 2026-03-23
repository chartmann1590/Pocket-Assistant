# Pocket Assistant

Pocket Assistant is a **local-first Android** app that turns screenshots, photos, PDFs, and shared text into summaries, structured extractions, tasks, and reminders. OCR and optional on-device LLM inference stay on the device unless you configure **Ollama** and choose a remote or hybrid routing mode. There is no vendor-hosted backend for core features.

## Requirements

- **Android Studio** (recent stable) or Android SDK command-line tools
- **JDK 17** (matches `compileOptions` / `jvmTarget` in the app module)
- **Android SDK** with **API 35** for compile/target
- **minSdk 26** for running on devices

Machine-specific SDK paths belong in `local.properties` (create at the repo root if Android Studio has not already). Do not commit secrets or tokens; use in-app settings for Ollama URLs and optional API tokens.

## Features (high level)

- Import via share intent or in-app flow; **ML Kit** text recognition for images; PDF text extraction with a **five-page** cap for performance
- **Room** persistence for items, AI results, tasks, reminders, and **chat** threads/messages (schema v2)
- **DataStore** for preferences and AI routing (local / Ollama / auto)
- **Downloadable LiteRT-LM** models (see `ModelConfig.kt`) with **WorkManager** (`ModelDownloadWorker`) and download progress
- Optional **Ollama** over HTTP when a base URL and model name are configured
- **Neural semantic search** via MediaPipe Universal Sentence Encoder for natural language item retrieval
- **RAG-style assistant** — semantic search finds relevant items, builds structured context for the LLM, with direct-answer fallback
- **Item deletion** with cascade cleanup of related AI results, tasks, and reminders
- **AdMob integration** (banner, interstitial, and rewarded ads) with a credit-based ad-free reward system

## Ads & Building from Source

This app includes **Google AdMob** ads when published. If you build from source, **ads are disabled by default**.

### Setup

1. Copy `local.properties.template` to `local.properties`
2. Set `ADS_ENABLED=false` (default) to build ad-free, or `ADS_ENABLED=true` with your own AdMob IDs to enable ads
3. Build normally with `./gradlew assembleDebug`

The template file documents all available ad configuration fields. Your `local.properties` is gitignored and never committed.

### Reward System

When ads are enabled, users can watch rewarded video ads to earn credits and redeem them for ad-free time:

| Credits | Ad-Free Time |
|---------|-------------|
| 1 | 1 hour |
| 3 | 3 hours |
| 6 | 6 hours |

Credits never expire. Ad-free time stacks when redeemed while already active. Access the rewards screen via the star icon on the home screen.

## Project layout

Single Gradle module `:app` (Kotlin DSL). Main code lives under:

`app/src/main/java/com/charles/pocketassistant/`

| Area | Role |
|------|------|
| `ui/` | Compose screens, ViewModels, navigation |
| `data/` | Room, repositories, DataStore, Retrofit |
| `ai/` | Routing, local engine, Ollama client, prompts, JSON parsing, RAG |
| `ads/` | AdMob integration (banner, interstitial, rewarded), ad manager |
| `ml/` | ML Kit engines, neural embeddings, semantic search, text classification |
| `ocr/` | ML Kit + PDF rendering |
| `worker/` | Model download worker |
| `di/` | Hilt modules |
| `domain/` | Shared domain models |
| `util/` | Reminders, date parsing, helpers |

Resources and manifest: `app/src/main/res/`, `app/src/main/AndroidManifest.xml`. Version catalog: `gradle/libs.versions.toml`. LiteRT-LM JAR: `app/libs/` (see app `build.gradle.kts`).

## Screenshots

Device and UI reference captures live under **`screenshots/`** at the repo root (not under `app/src`):

| Path | Contents |
|------|----------|
| `screenshots/device/` | Hand-captured device screens (home, detail, scroll states, etc.) |
| `screenshots/automation/` | Saved frames from emulator/automation runs (debugging and regression context) |

Add new marketing or documentation images under `screenshots/device/` with clear names. Prefer keeping transient automation output in `screenshots/automation/` or a dated subfolder if the set grows large.

## Build and run

From the repository root:

| Action | Unix / macOS | Windows (cmd/PowerShell) |
|--------|----------------|---------------------------|
| Debug APK | `./gradlew assembleDebug` | `.\gradlew.bat assembleDebug` |
| Install debug | `./gradlew installDebug` | `.\gradlew.bat installDebug` |
| Unit tests | `./gradlew testDebugUnitTest` | `.\gradlew.bat testDebugUnitTest` |
| Instrumented tests | `./gradlew connectedDebugAndroidTest` | `.\gradlew.bat connectedDebugAndroidTest` |
| Lint | `./gradlew lintDebug` | `.\gradlew.bat lintDebug` |

Or open the folder in Android Studio, sync Gradle, and use **Run**.

### Current build health (Mar 2026 snapshot)

- `testDebugUnitTest`: passing
- `lintDebug`: currently blocked by a Kotlin metadata mismatch in `app/libs/litertlm-android-0.8.0-classes.jar` (jar metadata 2.2.0 vs project Kotlin metadata expectation 1.9.0)
- AGP emits a warning for `compileSdk 35` with AGP `8.3.2`; this is a warning and does not block `assemble`/unit tests

## Local models

Model IDs, sizes, and Hugging Face-related settings are centralized in:

`app/src/main/java/com/charles/pocketassistant/ai/local/ModelConfig.kt`

Onboarding and settings can download, delete, or re-download the selected artifact into app-specific storage.

## Ollama

1. In onboarding or settings, set the **base URL** (e.g. `http://192.168.1.50:11434/`).
2. If your proxy requires it, set an **API token** (sent as `Authorization: Bearer …`).
3. Set the **model name** as exposed by your Ollama instance.
4. Use the in-app connection test before relying on Ollama-only or auto routing.

Routing is implemented in `AiRouter`: LOCAL mode stays strictly local (no Ollama fallback). OLLAMA mode uses only the remote server. AUTO mode tries Ollama first and falls back to local when unreachable.

## Permissions (summary)

- `INTERNET` — model downloads, optional Ollama, and ads
- `ACCESS_NETWORK_STATE` — network-aware downloads
- `POST_NOTIFICATIONS` — reminders on Android 13+
- Foreground service types used for resilient model download notifications (see manifest)

## Limitations

- Local LLM behavior depends on the bundled LiteRT/MediaPipe runtime and the downloaded model.
- PDF OCR is capped at **five pages**.
- Reminders use local scheduling; there is no calendar sync in the current MVP.

## Contributing and conventions

See **[AGENTS.md](AGENTS.md)** for module boundaries, naming, and commit message style. **[CLAUDE.md](CLAUDE.md)** summarizes data flow and key classes for tooling and contributors.

## License

Specify your license here if you publish the repo publicly.
