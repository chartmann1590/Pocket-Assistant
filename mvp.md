You are building a complete Android MVP app in Kotlin + Jetpack Compose called:

Pocket Assistant — Local AI Organizer

**Repository note:** This document is the product/build specification for the project. Implementation lives under `app/`. Reference UI captures for demos and docs are kept in `screenshots/device/` (hand captures) and `screenshots/automation/` (saved automation frames), not inside the Android module tree.

Your job is to generate a production-minded MVP Android app project that builds in Android Studio and is organized cleanly for future iteration.

CORE PRODUCT REQUIREMENTS

This app is a local-first AI organizer for Android.
It must:
1. Run OCR on screenshots, photos, and scanned documents locally on the device.
2. Use an on-device LLM for summarization and structured extraction.
3. Download the default on-device model during onboarding after first install.
4. Offer optional connection to the user’s own Ollama server for harder jobs.
5. Never require any backend hosted by us.
6. Store all app data locally on the device using Room.
7. Support sharing text and images into the app from other Android apps.
8. Let the user create reminders/tasks from extracted information.
9. Be architected cleanly with MVVM and repository pattern.
10. Be a complete working MVP, not pseudocode.

TECH STACK

Use:
- Kotlin
- Jetpack Compose
- Material 3
- MVVM architecture
- Room for local database
- WorkManager for background downloads and processing
- DataStore for user settings
- ML Kit Text Recognition v2 for OCR
- Google AI Edge / MediaPipe LLM Inference for local LLM
- OkHttp + Retrofit + Kotlinx Serialization for Ollama API
- Coil for image loading
- Android Sharesheet integration for importing shared content
- Document picker / image picker support
- Coroutines + Flow
- Hilt for dependency injection

MIN SDK / TARGET
- minSdk 26 or the lowest version that is still practical with the chosen libraries
- target latest stable SDK
- make all versions centralized in Gradle version catalog if possible

APP GOAL

The app helps users turn screenshots, photos, PDFs, pasted text, and notes into:
- summaries
- tasks
- reminders
- extracted dates/times
- extracted bill amounts / action items / contact names / locations

The app must work in three AI modes:
1. Local Only
2. Ollama Only
3. Auto (local for simple jobs, Ollama for heavy jobs)

MVP USE CASES

The MVP must support these concrete flows:

A. BILL FLOW
- User imports a screenshot/photo/PDF of a bill
- OCR extracts the text
- AI returns:
  - short summary
  - bill name
  - amount due
  - due date
  - suggested reminder
- User can tap:
  - Add task
  - Create reminder
  - Analyze deeper with Ollama

B. MESSAGE FLOW
- User shares text or screenshot of work/school/personal message into app
- OCR if needed
- AI returns:
  - short summary
  - action items
  - dates/times if present
  - follow-up suggestion

C. APPOINTMENT FLOW
- User imports message/screenshot/photo with appointment details
- AI extracts:
  - title
  - date/time
  - location
  - notes
- User can create reminder

D. BRAIN DUMP FLOW
- User pastes freeform text
- AI converts it into checklist/tasks

E. SCREENSHOT FLOW
- User shares a screenshot from another app into Pocket Assistant
- OCR runs locally
- local AI summarizes it and extracts next actions

APP SCREENS

Build these screens with Compose:

1. OnboardingScreen
- welcome to Pocket Assistant
- explain local-first AI
- explain optional Ollama support
- ask user to choose setup mode:
  - Local AI only
  - Connect Ollama
  - Both
- if Local AI or Both:
  - show model size estimate
  - show storage requirement
  - ask for consent to download model
  - recommend Wi-Fi
  - start model download
  - show progress
- if Ollama or Both:
  - input for base URL
  - optional API token / auth header
  - model name input or model list fetch
  - “Test Connection” button
- finish onboarding only after at least one inference mode is configured

2. HomeScreen
- top app bar with title
- cards for:
  - Add Photo
  - Add Screenshot
  - Add PDF
  - Paste Text
  - View Tasks
- section for recent items
- filter chips:
  - All
  - Bills
  - Messages
  - Appointments
  - Tasks
- floating action button for quick add

3. ImportScreen / CaptureEntryScreen
- choose source:
  - camera
  - gallery
  - file picker
  - paste text
- preview selected content before processing

4. ItemDetailScreen
- preview thumbnail if image/PDF page
- raw OCR text section collapsible
- AI summary card
- extracted entities card
- extracted tasks card
- suggested actions card
- buttons:
  - Add Task
  - Add Reminder
  - Re-run Local
  - Send to Ollama
  - Edit Results

5. TasksScreen
- tabs:
  - Today
  - Upcoming
  - Done
- manual add task
- tasks linked to source item

6. SettingsScreen
- AI mode selector: Local / Ollama / Auto
- local model status
- model download/delete button
- model version
- storage used
- Ollama server settings
- test Ollama connection
- selected Ollama model
- prompt/debug section toggle for advanced users
- privacy section describing local processing

7. ProcessingScreen / bottom sheet
- show OCR progress
- show AI progress
- show which mode was used: local or Ollama
- allow cancellation

DATA MODEL

Create Room entities and DAOs for:

ItemEntity
- id: String
- type: String (image, pdf, text, screenshot, transcript)
- sourceApp: String?
- localUri: String?
- thumbnailUri: String?
- rawText: String
- createdAt: Long
- classification: String? (bill, message, appointment, note, unknown)

AiResultEntity
- id: String
- itemId: String
- modeUsed: String (local, ollama)
- summary: String
- extractedJson: String
- modelName: String
- createdAt: Long

TaskEntity
- id: String
- itemId: String?
- title: String
- details: String?
- dueAt: Long?
- isDone: Boolean
- createdAt: Long

ReminderEntity
- id: String
- itemId: String?
- title: String
- remindAt: Long
- createdAt: Long

Settings should use DataStore, not Room:
- onboardingComplete
- aiMode
- localModelInstalled
- localModelVersion
- localModelPath
- ollamaBaseUrl
- ollamaApiToken
- ollamaModelName
- allowMeteredDownload
- localModelDownloadComplete
- localModelDownloadInProgress

AI OUTPUT FORMAT

For both local model and Ollama, force structured JSON output.
Create a shared schema like:

{
  "classification": "bill | message | appointment | note | unknown",
  "summary": "short summary",
  "entities": {
    "people": [],
    "organizations": [],
    "amounts": [],
    "dates": [],
    "times": [],
    "locations": []
  },
  "tasks": [
    {
      "title": "",
      "details": "",
      "dueDate": ""
    }
  ],
  "billInfo": {
    "vendor": "",
    "amount": "",
    "dueDate": ""
  },
  "appointmentInfo": {
    "title": "",
    "date": "",
    "time": "",
    "location": ""
  }
}

Build robust parsing with fallback handling if JSON is malformed.
Do not crash on bad model output.

LOCAL MODEL REQUIREMENTS

Implement local LLM support using Google AI Edge / MediaPipe LLM Inference.

Important:
- The app must NOT bundle the large model inside the APK.
- Instead, it must download the model on first-run onboarding if the user enables local AI.
- Put all model download configuration in one place, such as:
  - ModelConfig.kt
  - LocalModelManager.kt

Create a clearly documented config object for:
- model display name
- version string
- remote download URL
- expected checksum (optional placeholder if needed)
- local filename
- minimum free space required
- whether Wi-Fi is recommended

IMPORTANT IMPLEMENTATION DETAIL:
Use a placeholder/config-driven URL for the default model source and make it easy to swap.
Do NOT scatter the URL across the project.
The code should support downloading a MediaPipe-compatible .litertlm model file.

Create:
- LocalModelManager
- ModelDownloadWorker
- LocalLlmEngine

Responsibilities:
LocalModelManager:
- check if model exists
- return model path
- validate file exists and basic size sanity
- delete/re-download model
- expose status flow

ModelDownloadWorker:
- download with progress
- save into app-specific storage
- support resume if practical
- fail gracefully
- update progress notifications/state
- set installed flag on success

LocalLlmEngine:
- lazy initialize MediaPipe LLM with local model path
- expose summarizeAndExtract(text: String): AiExtractionResult
- handle initialization failure gracefully
- release resources if needed

ONBOARDING MODEL DOWNLOAD FLOW

Implement the exact first-run onboarding behavior:
1. App launches for first time
2. Show onboarding
3. User selects Local AI only or Both
4. App checks available storage
5. App explains approximate model size and that it will be downloaded from the internet
6. User taps Download Model
7. Download begins using WorkManager
8. Show progress UI
9. On success:
   - save model path
   - mark local AI installed
   - allow user to continue onboarding
10. On failure:
   - show retry
   - allow user to continue with Ollama only if configured
11. If user selected Ollama only, skip local download entirely

Also support:
- “Set up local AI later” only if Ollama is already configured
- “Delete local model” in settings
- “Redownload model” in settings

MODEL DOWNLOAD IMPLEMENTATION NOTES

- Put the model file in app-specific internal or external storage, whichever is more appropriate
- Prefer app-specific external files dir if size is large and app still has ownership
- Do not require dangerous storage permissions if avoidable
- Use a temp file then rename after success
- If checksum support is implemented, verify checksum before finalizing
- Expose progress as Flow / StateFlow for UI
- Ensure process death does not lose download state

OCR REQUIREMENTS

Use ML Kit Text Recognition v2.
Build:
- OcrEngine
- TextCleanupUtil

OcrEngine must support:
- image from file URI
- bitmap
- screenshot imports
- possibly PDF page rendering for OCR

TextCleanupUtil should:
- normalize whitespace
- join broken lines where reasonable
- preserve dates/amounts
- make text more LLM-friendly

PDF SUPPORT

Support selecting PDFs.
For MVP:
- render first page and optionally all pages sequentially
- extract text by converting pages to bitmaps and running OCR
- if PDF is long, limit local AI processing and recommend Ollama
- store combined OCR text

SHARE INTENTS

Implement Android share targets so the app can receive:
- shared plain text
- shared image
- shared PDF if practical

When a user shares into the app:
- open import flow
- auto-detect content type
- run OCR if needed
- create ItemEntity
- process with selected AI mode

AI ROUTING LOGIC

Create AiRouter with these behaviors:

Local Only
- always use local model
- if model unavailable, show setup prompt
- if input is too large, show “Connect Ollama for advanced analysis”

Ollama Only
- always send to configured Ollama server

Auto
- use local for:
  - OCR text under a configurable threshold
  - pasted notes
  - screenshots
- suggest or route to Ollama for:
  - long PDFs
  - very long text
  - retry on local failure if Ollama is configured

Make thresholds configurable in one place.

OLLAMA SUPPORT

Implement a full optional Ollama integration layer.

Create:
- OllamaApiService
- OllamaRepository
- OllamaConnectionTester

Support:
- base URL
- optional bearer/API token
- selected model name
- timeout handling
- fetching model list if endpoint supports it
- test connection
- chat endpoint usage for extraction prompt
- clean error handling for unreachable server, auth failure, invalid model

Ollama endpoints to support:
- list models if available
- chat/generate inference
- optional embeddings later, but not required for MVP

Do not assume localhost.
Users may use:
- LAN IP
- HTTPS reverse proxy
- Tailscale/private hostname
- custom port

Allow self-signed certificate handling ONLY behind an advanced toggle and isolate it clearly so it is not default behavior.

PROMPTING DESIGN

Create a centralized PromptFactory for both local and Ollama use.

Prompts should:
- instruct model to return strict JSON
- classify item
- summarize briefly
- extract tasks
- extract bill info if present
- extract appointment info if present

Have separate prompt builders for:
- bill prompt
- message prompt
- appointment prompt
- general extraction prompt

Start with one generalized extractor prompt and maybe specialized variants based on classifier.

TASKS AND REMINDERS

For MVP:
- store tasks in Room
- allow one-tap convert extracted task into TaskEntity
- allow reminder creation

Reminder implementation:
- simplest acceptable MVP is local reminder scheduling with AlarmManager or WorkManager plus notification
- no cloud sync
- no external backend

If calendar integration is added, make it optional and keep MVP safe if permissions are denied.

REPOSITORY LAYER

Create repositories:
- ItemRepository
- AiRepository
- TaskRepository
- SettingsRepository
- ModelRepository
- OllamaRepository

VIEWMODELS

Create ViewModels:
- OnboardingViewModel
- HomeViewModel
- ImportViewModel
- ItemDetailViewModel
- TasksViewModel
- SettingsViewModel

Each should expose Compose-friendly immutable UI state.

PROJECT STRUCTURE

Use a clean package structure, for example:

com.example.pocketassistant
- app
- di
- data
  - db
  - datastore
  - model
  - repository
  - remote
  - local
- domain
  - model
  - usecase
- ai
  - local
  - ollama
  - prompt
  - routing
  - parser
- ocr
- ui
  - onboarding
  - home
  - import
  - detail
  - tasks
  - settings
  - components
  - theme
- util
- worker

ERROR HANDLING

Be careful and defensive.
Handle:
- model download failure
- insufficient storage
- no network during onboarding
- OCR returning empty text
- malformed AI JSON
- Ollama timeout
- invalid Ollama model
- no model configured for selected mode
- PDF too large
- app restart during download

Every user-facing error should have a readable message and recovery action.

PRIVACY

This app is privacy-first.
Add a privacy note in onboarding and settings:
- local AI and OCR run on-device
- Ollama is optional and user-configured
- app does not require our own backend
- data stays local unless user chooses remote Ollama processing

UI / DESIGN

Create a polished but simple Material 3 UI.
Design goals:
- modern
- clean cards
- calm productivity aesthetic
- dark mode support
- good loading states
- no ugly placeholder text in final UI

Use sensible icons and spacing.
Make the app feel like a real shipped MVP.

TEST DATA / DEMO SUPPORT

Add some developer preview/sample data so the app is easy to demo in emulator or device:
- one sample bill
- one sample work message
- one sample appointment note

Optionally add a hidden developer menu or fake processing toggle for previews only, but ensure real implementation exists.

DELIVERABLES REQUIRED

Generate the complete Android Studio project with:
1. build.gradle / settings files
2. manifest
3. all Kotlin source files
4. Room database
5. Hilt setup
6. Compose navigation
7. onboarding flow
8. model download worker and local model manager
9. OCR pipeline
10. local AI integration abstraction
11. Ollama integration
12. all screens
13. repositories and viewmodels
14. README with setup steps

README REQUIREMENTS

The README must explain:
- what the app does
- architecture overview
- how local model download works
- where to set the default model URL/config
- how to connect an Ollama server
- permissions used
- known limitations
- how to build and run

IMPORTANT ENGINEERING RULES

- Do not output pseudocode
- Do not leave TODOs for core functionality
- Use real Kotlin code
- If a library requires setup that may vary, still wire the project as fully as possible and isolate the configurable pieces clearly
- Prefer working abstractions with clearly documented placeholders rather than vague notes
- Make sure onboarding handles the first-install model download flow completely
- Make the project coherent and runnable
- Avoid overengineering beyond MVP
- Keep model URL/config easy to change in one file
- Build the app so local AI can be disabled gracefully on unsupported devices

FINAL TASK

Generate the full project now.

If any dependency/API details are uncertain, isolate them behind clean interfaces and produce the most practical current implementation possible rather than stopping.