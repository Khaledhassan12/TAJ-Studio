# 01_PROJECT_STRUCTURE.md

## Folder Structure (Root)
The Agora project is organized as a multi-module Gradle project with specialized directories for native dependencies, automation, and documentation.

```text
/ (Root)
├── app/                  # Core Android application module
├── build-logic/          # Custom Gradle convention plugins and bytecode transformations
├── thirdparty/           # Git submodules for external C++ projects (llama.cpp, proot, talloc)
├── docs/                 # Project documentation (Markdown files)
├── assets/               # Branding and store-listing graphic assets
├── scripts/              # Build and utility scripts (e.g., round_icon.py)
├── server/               # Infrastructure support (e.g., crash report handling)
├── fastlane/             # Deployment automation scripts
├── gradle/               # Gradle wrapper and version catalog
├── .github/              # GitHub Actions CI/CD workflows
├── build-proot.sh        # Build script for the PRoot environment
├── ARCHITECTURE.md       # Developer-facing architectural overview
├── README.md             # Primary user-facing documentation
├── mkdocs.yml            # Documentation site configuration
└── settings.gradle.kts   # Project structure and repository definitions
```

## Android Module Structure (`app/`)
The `:app` module follows the standard Android source set layout with deep nesting for specialized logic.

```text
app/
├── src/main/
│   ├── java/com/newoether/agora/
│   │   ├── api/          # LLM Provider implementations (OpenAI, Gemini, etc.)
│   │   ├── automation/   # Task/Loop engines, scheduling, and cron logic
│   │   ├── data/         # Repositories, Room DB, DataStore, and file management
│   │   ├── di/           # Manual Dependency Injection (AppContainer)
│   │   ├── model/        # Domain data classes and enums
│   │   ├── sandbox/      # PRoot-based Linux environment management
│   │   ├── service/      # Foreground services and WorkManager workers
│   │   ├── tool/         # Pluggable Tool implementations (Shell, Web, etc.)
│   │   ├── ui/           # Jetpack Compose screens and design system
│   │   ├── util/         # Security, networking, and utility classes
│   │   └── viewmodel/    # State management and business logic orchestrators
│   ├── cpp/              # JNI bindings and native C++ source (llama, proot)
│   ├── assets/           # Bundled models, rootfs, and static files
│   └── res/              # Android resources (drawables, layouts, values)
├── src/fdroid/           # F-Droid specific source code (Full Sandbox support)
├── src/play/             # Google Play specific source code (Sandbox stubs)
└── build.gradle.kts      # Module-level build configuration
```

## Package Breakdown (`com.newoether.agora`)
### API (`.api`)
Handles communication with diverse LLM backends.
- `openai/`, `anthropic/`, `gemini/`, `ollama/`, `local/`: Backend-specific adapters.
- `LlmProvider.kt`: Universal interface for all model providers.
- `HttpClient.kt`: Shared OkHttp instance and SSE streaming logic.

### Automation (`.automation`)
The agentic core of the platform.
- `TaskExecutionEngine.kt`: Headless generation driver for background agents.
- `LoopManager.kt`: Persistent conversation loop coordinator.
- `AutomationScheduler.kt`: `AlarmManager` wrapper for cron-based tasks.
- `CronExpression.kt`: Pure Kotlin cron parser for scheduling.

### Data (`.data`)
Persistence and data transformation.
- `local/ChatDatabase.kt`: Room database definition and migrations.
- `repository/`: Repositories (`ConversationRepository`, `TaskRepository`) wrapping DAOs.
- `SettingsManager.kt`: DataStore-backed preference management.
- `MemoryManager.kt`: File-based long-term memory system.

### Sandbox (`.sandbox`)
On-device Linux virtualization.
- `ProotSandboxManager.kt`: Lifecycle and package management for Alpine Linux.
- `SandboxDocumentsProvider.kt`: Exposes sandbox files via Android's SAF.

### UI (`.ui`)
Modern Jetpack Compose architecture.
- `chat/`: Main conversation interface, message items, and input bar.
- `settings/`: Hierarchical settings system with collapsing titles.
- `tasks/`: Management UI for automated agent tasks.
- `theme/`: Material 3 design system implementation.

## Build Logic Module (`build-logic/`)
Contains custom build-time tools.
- `RemoveFirstLastFix.kt`: ASM bytecode visitor that fixes binary incompatibilities between Android 15 SDK and older Android versions.

## Native Layer (`app/src/main/cpp`)
Bridges Kotlin with high-performance C++ libraries.
- `llama_chat_jni.cpp`: LLM inference bindings.
- `proot_jni.cpp`: Virtualization engine bindings.
- `CMakeLists.txt`: Orchestrates the native build process and submodule linking.
