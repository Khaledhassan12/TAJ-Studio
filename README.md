# TAJ Studio

**TAJ Studio** is a community-developed Android application development environment built on the Sketchware Pro codebase. This repository contains the Android app source, Gradle build configuration, and CI workflows used to validate and package the project.

TAJ Studio is an independent community project and is not affiliated with or endorsed by the Sketchware Pro maintainers.

## Project status

- **Primary development branch:** `TAJ-STUDIO-AI`
- **Stable/base branch:** `Master`
- **Android application ID:** `pro.sketchware`
- **Minimum Android version:** Android 8.0 (API 26)
- **Build toolchain:** Gradle 8.13, JDK 17, Android SDK 36
- **Language:** Primarily Java, with Kotlin support in the build

[![Android CI Build](https://github.com/Khaledhassan12/TAJ-Studio/actions/workflows/android-ci.yml/badge.svg?branch=TAJ-STUDIO-AI)](https://github.com/Khaledhassan12/TAJ-Studio/actions/workflows/android-ci.yml)
[![Android CI](https://github.com/Khaledhassan12/TAJ-Studio/actions/workflows/android.yml/badge.svg?branch=TAJ-STUDIO-AI)](https://github.com/Khaledhassan12/TAJ-Studio/actions/workflows/android.yml)

## Build locally

### Requirements

- JDK 17
- Android SDK platform 36 and Android build tools
- A working internet connection for Gradle dependency resolution

### Build commands

On Linux or macOS:

```bash
./gradlew --no-daemon assembleDebug
./gradlew --no-daemon assembleRelease
```

On Windows:

```bat
gradlew.bat --no-daemon assembleDebug
gradlew.bat --no-daemon assembleRelease
```

The APKs are generated under:

- `app/build/outputs/apk/debug/`
- `app/build/outputs/apk/release/`

The current release configuration uses the repository's test/debug signing setup. It is suitable for CI validation and testing, **not a production signing key for a public release**. Keep any private production signing material outside the repository and out of logs.

## Continuous integration

- **Android CI Build** builds both Debug and Release APKs and uploads them as workflow artifacts.
- **Android CI** builds the Release APK, uploads the artifact, and can send it to Telegram when the required repository secrets are configured.
- Telegram delivery requires `TELEGRAM_API_ID`, `TELEGRAM_API_HASH`, `TELEGRAM_BOT_TOKEN`, and `TELEGRAM_CHAT_ID`. `TELEGRAM_TOPIC_ID` is optional. Configure these in GitHub Actions secrets; never commit them to source control.

Workflow runs and downloadable artifacts are available from the [Actions page](https://github.com/Khaledhassan12/TAJ-Studio/actions).

## Development guidelines

- Keep changes focused and reviewable.
- Prefer fixing the underlying cause over masking errors.
- Preserve project compatibility and test build-affecting changes with CI.
- Do not commit generated build output, local SDK configuration, credentials, or production signing keys.
- Use clear Conventional Commit messages, for example `fix(editor): prevent invalid child indexes` or `chore(ci): pin build tooling`.

## Upstream code and licensing

TAJ Studio is derived from [Sketchware Pro](https://github.com/Sketchware-Pro/Sketchware-Pro). Existing upstream code and third-party components retain their respective copyright and licensing terms. Read [LICENSE.md](LICENSE.md) before reusing or redistributing code; do not assume that all code in this repository is available under one permissive license.

## Contributing

Before submitting a change, explain the problem it solves, keep the patch scoped, and verify relevant build or runtime behavior. For compiler, project-generation, or editor changes, include reproduction steps and test evidence whenever possible.

---

TAJ Studio is maintained as an independent community project. Names and trademarks belonging to upstream projects remain the property of their respective owners.
