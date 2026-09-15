# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Writeopia is both a **text editor application** (desktop, mobile, web) and a **multiplatform SDK** for rich text editing. The SDK is published to Maven Central under the MIT license (`io.writeopia:*`), while the application is a complete offline-first editor with AI integration.

**Current SDK Version**: 0.14.0
**Current App Version**: 0.58.0

## Architecture

This is a **Kotlin Multiplatform (KMP)** project using **Compose Multiplatform** for UI across all platforms.

### High-Level Module Structure

```
Writeopia/
├── SDK Layer (published to Maven Central)
│   ├── writeopia_models/          # Core data models
│   ├── writeopia/                 # Core SDK business logic
│   ├── writeopia_ui/              # UI components (Compose Multiplatform)
│   └── plugins/                   # Pluggable implementations
│       ├── writeopia_persistence_core      # Persistence interfaces
│       ├── writeopia_persistence_room      # Android Room impl
│       ├── writeopia_persistence_sqldelight # Desktop/Web/iOS SQLDelight impl
│       ├── writeopia_serialization         # JSON serialization
│       ├── writeopia_network               # Ktor HTTP client
│       ├── writeopia_export                # Export functionality
│       ├── writeopia_import_document       # Import functionality
│       └── writeopia_presentation          # Presentation utilities
│
├── Application Layer (not published)
│   ├── composeApp/                # Main KMP app (Desktop, Android, iOS)
│   ├── androidApp/                # Android-specific entry point
│   ├── web/                       # Web app (JS/WASM)
│   ├── core/                      # 22 core app modules
│   │   ├── persistence_bridge     # Platform-agnostic persistence
│   │   ├── auth_core              # Authentication
│   │   ├── genai                  # AI integration
│   │   ├── local_ai               # Ollama/local AI support
│   │   ├── documents              # Document management
│   │   ├── connection             # Network/sync
│   │   ├── common_ui              # Shared UI components
│   │   ├── theme                  # Design system
│   │   ├── navigation             # Navigation
│   │   └── ...
│   └── features/                  # Feature modules
│       ├── editor                 # Text editor UI
│       ├── auth                   # Auth screens
│       ├── account                # Account management
│       ├── search                 # Search functionality
│       ├── note_menu              # Document list
│       ├── documents_graph        # Graph visualization
│       ├── drawing                # Drawing tools
│       └── ...
│
└── Backend Layer (JVM only)
    ├── gateway/                   # API gateway (Ktor)
    ├── core/                      # Shared backend services
    │   ├── auth                   # Auth service
    │   ├── database               # SQLDelight + PostgreSQL
    │   ├── ai                     # AI orchestration
    │   ├── genai_service          # Google GenAI integration
    │   └── ...
    └── micro/                     # Independent microservices
        ├── auth                   # Auth microservice
        ├── documents              # Documents microservice
        ├── ai                     # AI microservice
        ├── export                 # Export microservice
        └── media                  # Media/file microservice
```

### Key Architectural Patterns

**Plugin Architecture**: Persistence, network, and serialization are pluggable with platform-specific implementations (Room for Android, SQLDelight for Desktop/Web/iOS).

**Persistence Bridge**: `application/core/persistence_bridge` abstracts platform differences, routing to Room on Android and SQLDelight elsewhere.

**Feature Modules**: Each feature (editor, auth, search) is an independent module with minimal coupling.

**Microservices Backend**: Gateway orchestrates independent microservices (Auth, Documents, AI, Export, Media) that can be deployed and scaled separately.

**Offline-First**: Documents are cached locally and synced when connected. Real-time sync uses WebSockets.

## Technology Stack

- **Language**: Kotlin 2.4.10
- **JVM Target**: Java 21
- **UI**: Compose Multiplatform 1.11.1, Material Design 3
- **Persistence**: SQLDelight 2.3.2 (JVM/WASM/iOS), Room 2.8.4 (Android), PostgreSQL (backend)
- **Networking**: Ktor 3.5.2 (client & server), WebSockets for real-time sync
- **AI/ML**: Google GenAI 1.70.0 (Gemini API), Ollama (local LLM)
- **Serialization**: kotlinx.serialization 1.11.0
- **Image Loading**: Coil 3.5.0
- **Testing**: kotlin-test (multiplatform), embedded PostgreSQL (backend tests)
- **Code Style**: KTlint 14.2.0
- **Documentation**: Dokka 2.2.0

### Platform Targets

**SDK** (`writeopia_*`): JVM, JS, WASM, iOS (arm64 + simulator), Android
**Application** (`composeApp`): JVM (desktop), Android, iOS
**Backend** (`backend/**`): JVM only

## Build & Development Commands

### Running the Application

**Desktop app**:
```bash
./gradlew application:composeApp:run
```

**Web app**:
```bash
./gradlew jsBrowserRun
```

**Android app**: Open project in Android Studio and run `androidApp` configuration.

### Testing

**Run all JVM tests** (most common):
```bash
./gradlew jvmTest
```

**Run all tests across all platforms** (JVM, JS, iOS simulator):
```bash
./gradlew allTests
```

**Run tests for specific modules**:
```bash
./gradlew :writeopia:jvmTest
./gradlew :application:features:editor:jvmTest
```

**JavaScript/Browser tests**:
```bash
./gradlew jsBrowserTest
```

**iOS simulator tests**:
```bash
./gradlew iosSimulatorArm64Test
```

**Run a single test** (example):
```bash
./gradlew :writeopia:jvmTest --tests "io.writeopia.normalization.merge.steps.StepToGroupMergerTest"
```

**Android instrumentation tests** (requires connected device/emulator):
```bash
./gradlew connectedAndroidTest
```

### Code Quality

**Run ktlint checks**:
```bash
./gradlew ktlintCheck
```

**Format code with ktlint**:
```bash
./gradlew ktlintFormat
```

### Backend Development

**Start local PostgreSQL** (Docker):
```bash
cd docker/postgres
docker-compose up -d
```

**Run backend gateway locally** (from `backend/gateway`):
```bash
./gradlew run
```

**Run specific microservice** (example: documents):
```bash
./gradlew :backend:micro:documents:run
```

### Building

**Build desktop distribution**:
```bash
./gradlew :application:composeApp:packageDistributionForCurrentOS
```

**Build Android APK**:
```bash
./gradlew :application:androidApp:assembleDebug
```

**Publish SDK to Maven Local** (for testing):
```bash
./gradlew publishToMavenLocal
```

## Document Format

Writeopia uses a custom JSON format for documents (`.wrdoc.json` files). Each document contains an array of `StoryStep` objects representing content blocks.

### StoryStep Types

Common types used in document content:
- `title` (number: 11) - Document title (first element)
- `message` (number: 0) - Regular paragraph
- `unordered_list_item` (number: 16) - Bullet point
- `check_item` (number: 10) - Checkbox with `checked` field
- `code_block` (number: 23) - Code block
- `document_link` (number: 20) - Link to another document
- `image` (number: 2) - Image with `url` or `path`
- `divider` (number: 21) - Horizontal divider
- `space` (number: 7) - Empty space

### Text Formatting

**Tags** (block-level): `H1`, `H2`, `H3`, `H4`, `CODE_BLOCK`, `HIGH_LIGHT_BLOCK`, `COLLAPSED`

**Spans** (inline, character ranges): `BOLD`, `ITALIC`, `UNDERLINE`, `HIGHLIGHT`, `HIGHLIGHT_GREEN`, `HIGHLIGHT_RED`, `LINK`

Spans use `start` (inclusive) and `end` (exclusive) indices on the text. Links have an `extra` field for the URL.

See `CLAUDE_CODE_WRITEOPIA_MANUAL.md` for complete document format specification with examples.

## Key Development Patterns

### Persistence

**For platform-agnostic code**, use interfaces from `writeopia_persistence_core`:
- `DocumentRepository` - Document CRUD operations
- `WorkspaceRepository` - Workspace management
- `FolderRepository` - Folder operations

**Platform implementations** are injected via dependency injection:
- Android: Room implementation (`writeopia_persistence_room`)
- Desktop/Web/iOS: SQLDelight implementation (`writeopia_persistence_sqldelight`)

**In the application**, use the `persistence_bridge` module which automatically selects the correct implementation.

### Adding a New Feature Module

1. Create module in `application/features/your_feature`
2. Add to `settings.gradle.kts`: `include(":application:features:your_feature")`
3. Create `build.gradle.kts` with KMP setup
4. Depend on core modules as needed (`common_ui`, `theme`, `models`, etc.)
5. Export navigation routes in a dedicated file
6. Register routes in `application/composeApp` navigation graph

### Working with AI Features

**Local AI** (Ollama): Uses `application/core/local_ai` module with HTTP API to local Ollama server.

**Cloud AI** (Google GenAI): Uses `application/core/genai` module with Google Gemini API.

**Backend AI**: Microservice at `backend/micro/ai` orchestrates AI requests.

### Database Schema Changes

**SQLDelight** (used by most platforms):
1. Edit `.sq` files in `backend/core/database/src/main/sqldelight/`
2. Schema migrations are defined in the same `.sq` files
3. Run `./gradlew generateSqlDelightInterface` to regenerate Kotlin code

**Room** (Android only):
1. Edit entity classes in `application/core/persistence_room` or `plugins/writeopia_persistence_room`
2. Increment database version
3. Create migration in the Database class

## Testing Conventions

- Use `kotlin.test` for multiplatform tests (works across JVM, JS, Native)
- Place common tests in `src/commonTest/kotlin/`
- Platform-specific tests: `src/jvmTest/`, `src/androidTest/`, etc.
- Use `kotlinx.coroutines.test` for testing coroutines (`runTest` helper)
- Backend tests use embedded PostgreSQL for integration tests
- Mock repositories extend from interfaces in `writeopia_persistence_core`

## Important Notes

- **Never commit directly to `main`**: Create feature branches and open PRs
- **Multiplatform considerations**: When adding dependencies, check if they support all required targets (JVM, JS, WASM, iOS, Android)
- **Backend database**: Backend uses PostgreSQL, not SQLite. Schema is defined in SQLDelight for type safety.
- **SDK vs Application**: SDK modules (`writeopia*`, `plugins/*`) must not depend on `application/*` modules
- **Version bumping**: SDK version in `build.gradle.kts` extra property, app version in `application/composeApp/build.gradle.kts`
- **Code style**: Project uses ktlint. Run `ktlintFormat` before committing.
- **File naming**: Documents use `{Title}_{id}.wrdoc.json`, folders use `{Title}_{id}.wrfolder.json`

## Useful Files & Locations

- `gradle/libs.versions.toml` - Centralized dependency versions
- `settings.gradle.kts` - Module includes
- `build.gradle.kts` - Root build config, SDK version
- `CLAUDE_CODE_WRITEOPIA_MANUAL.md` - Complete document format reference
- `README.md` - Public documentation
- `.github/workflows/` - CI/CD pipelines
- `docker/postgres/docker-compose.yml` - Local development database
- `backend/core/database/src/main/sqldelight/` - Backend SQL schema
- `application/core/persistence_sqldelight/src/commonMain/sqldelight/` - App SQL schema
