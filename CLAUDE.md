# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

All commands from repository root (use `gradlew.bat` on Windows):

```bash
./gradlew assembleDebug                # Build debug APK
./gradlew installDebug                 # Install on connected device/emulator
./gradlew testDebugUnitTest            # JVM unit tests
./gradlew connectedDebugAndroidTest    # Instrumentation tests (device required)
./gradlew lintDebug                    # Lint checks
```

After app changes the user should see on a phone/emulator, run **`installDebug`** (not only `assembleDebug`) when a device is connected—unless they say otherwise.

## Architecture

Single-module Android app (`:app`) using Kotlin, Jetpack Compose, and Hilt DI.

**Package layout** (`app/src/main/java/com/charles/pocketassistant/`):
- `ai/` — AI routing, local LLM engine, Ollama client, prompt templates, JSON parsing
- `data/` — Room database, DAOs, repositories, DataStore preferences, Retrofit API
- `domain/` — Data models (`AiExtractionResult`, `AssistantChatResult`)
- `ml/` — ML Kit entity extraction (dates, money, contacts) complementary to LLM output
- `ocr/` — ML Kit text recognition + PDF page rendering (max 5 pages)
- `ui/` — Compose screens, ViewModels, navigation (`AppNav.kt`)
- `worker/` — `ModelDownloadWorker` (WorkManager, Hugging Face downloads)
- `di/` — Hilt modules (`AppModule`)
- `util/` — Reminder scheduling, date parsing, notifications, optional calendar helpers

**Repo assets:** UI and automation screenshots are under `screenshots/device/` and `screenshots/automation/` (root of repo), not inside `:app`.

**Key data flow:**
1. Input arrives via share intent or manual import → `ImportViewModel`
2. OCR extracts text (`OcrEngine`) → raw text stored as `ItemEntity`
3. `AiRepository.run()` → `AiRouter` decides LOCAL vs OLLAMA vs AUTO
4. Local path: `LocalLlmEngine` → `LiteRtLmBridge` (JNI to LiteRT-LM)
5. Remote path: `OllamaRepositoryImpl` → Retrofit HTTP to Ollama server
6. AI output parsed by `AiJsonParser` → `AiResultEntity` stored in Room
7. Detail screen shows summary, extracted entities, tasks, reminders

**AI routing logic** (`AiRouter`): LOCAL mode → local only; OLLAMA mode → remote only; AUTO (both) → Ollama first when URL + model are configured, else local when installed. `AiRepository` / assistant chat fall back to local when Ollama is unreachable in AUTO mode. Ollama model names come from `GET /api/tags` in Settings/onboarding (not typed).

**Database:** Room `pocket_assistant.db` (schema v2). Entities: Item, AiResult, Task, Reminder, ChatThread, ChatMessage. Migration 1→2 adds chat tables.

**Three downloadable local models** (configured in `ModelConfig.kt`): Qwen3 0.6B (~586MB), Qwen2.5 1.5B Instruct (~1.6GB), Gemma 3n E2B (~3GB, gated/requires HF token).

**Current quality checks (Mar 2026):**
- `testDebugUnitTest` passes in current workspace state.
- `lintDebug` currently fails at `:app:lintAnalyzeDebug` due to Kotlin metadata mismatch from `app/libs/litertlm-android-0.8.0-classes.jar` (2.2.0 metadata vs 1.9.0 expected by project tooling).

## Conventions

- Kotlin official style, 4-space indent
- Types/files: `PascalCase`; functions/variables: `camelCase`; constants: `UPPER_SNAKE_CASE`
- Compose screens: `*Screen.kt`; ViewModels: `*ViewModel` in `ui/`
- Packages aligned by feature area, not cross-cutting utilities
- Commit messages: imperative and scoped, e.g. `feat(ai): add ollama connection retry`
- Dependencies managed via version catalog: `gradle/libs.versions.toml`
- Local JAR for LiteRT-LM lives in `app/libs/`
