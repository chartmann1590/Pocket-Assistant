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

## Architecture

Single-module Android app (`:app`) using Kotlin, Jetpack Compose, and Hilt DI.

**Package layout** (`app/src/main/java/com/charles/pocketassistant/`):
- `ai/` — AI routing, local LLM engine, Ollama client, prompt templates, JSON parsing
- `data/` — Room database, DAOs, repositories, DataStore preferences, Retrofit API
- `domain/` — Data models (`AiExtractionResult`, `AssistantChatResult`)
- `ocr/` — ML Kit text recognition + PDF page rendering (max 5 pages)
- `ui/` — Compose screens, ViewModels, navigation (`AppNav.kt`)
- `worker/` — `ModelDownloadWorker` (WorkManager, Hugging Face downloads)
- `di/` — Hilt modules (`AppModule`)
- `util/` — Reminder scheduling, date parsing

**Key data flow:**
1. Input arrives via share intent or manual import → `ImportViewModel`
2. OCR extracts text (`OcrEngine`) → raw text stored as `ItemEntity`
3. `AiRepository.run()` → `AiRouter` decides LOCAL vs OLLAMA vs AUTO
4. Local path: `LocalLlmEngine` → `LiteRtLmBridge` (JNI to LiteRT-LM)
5. Remote path: `OllamaRepositoryImpl` → Retrofit HTTP to Ollama server
6. AI output parsed by `AiJsonParser` → `AiResultEntity` stored in Room
7. Detail screen shows summary, extracted entities, tasks, reminders

**AI routing logic** (`AiRouter`): SHORT text + local model available → LOCAL; LONG text or PDFs → OLLAMA (if configured); fallback → LOCAL.

**Database:** Room `pocket_assistant.db` (schema v2). Entities: Item, AiResult, Task, Reminder, ChatThread, ChatMessage. Migration 1→2 adds chat tables.

**Three downloadable local models** (configured in `ModelConfig.kt`): Qwen3 0.6B (~586MB), Qwen2.5 1.5B Instruct (~1.6GB), Gemma 3n E2B (~3GB, gated/requires HF token).

## Conventions

- Kotlin official style, 4-space indent
- Types/files: `PascalCase`; functions/variables: `camelCase`; constants: `UPPER_SNAKE_CASE`
- Compose screens: `*Screen.kt`; ViewModels: `*ViewModel` in `ui/`
- Packages aligned by feature area, not cross-cutting utilities
- Commit messages: imperative and scoped, e.g. `feat(ai): add ollama connection retry`
- Dependencies managed via version catalog: `gradle/libs.versions.toml`
- Local JAR for LiteRT-LM lives in `app/libs/`
