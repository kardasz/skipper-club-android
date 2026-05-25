# SkipperClub — Android

[![Build platforms](https://github.com/kardasz/skipper-club-android/actions/workflows/build-platforms.yml/badge.svg?event=pull_request)](https://github.com/kardasz/skipper-club-android/actions/workflows/build-platforms.yml)
[![Unit tests + Lint](https://img.shields.io/github/actions/workflow/status/kardasz/skipper-club-android/build-platforms.yml?event=pull_request&label=unit%20tests%20%2B%20lint&logo=githubactions&logoColor=white)](https://github.com/kardasz/skipper-club-android/actions/workflows/build-platforms.yml)
[![Android build](https://img.shields.io/github/actions/workflow/status/kardasz/skipper-club-android/build-platforms.yml?event=pull_request&label=debug%20APK%20build&logo=android&logoColor=white)](https://github.com/kardasz/skipper-club-android/actions/workflows/build-platforms.yml)
[![Instrumented tests](https://img.shields.io/github/actions/workflow/status/kardasz/skipper-club-android/build-platforms.yml?event=pull_request&label=instrumented%20tests&logo=androidstudio&logoColor=white)](https://github.com/kardasz/skipper-club-android/actions/workflows/build-platforms.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)
![Compose BoM](https://img.shields.io/badge/Compose%20BoM-2026.05.01-4285F4?logo=jetpackcompose&logoColor=white)
![AGP](https://img.shields.io/badge/AGP-9.2.1-3DDC84?logo=android&logoColor=white)
![JDK](https://img.shields.io/badge/JDK-21-007396?logo=openjdk&logoColor=white)
![SDK](https://img.shields.io/badge/SDK-min%2026%20%7C%20target%2036-3DDC84?logo=android&logoColor=white)

Native Android client for **SkipperClub**, a platform that connects skippers organising sailing cruises with people looking for spots on board. The app combines crew recruitment with a sailing-focused social network: profile-driven trust, post-cruise blind reviews, real-time chat, AI-assisted content, and community-driven regional content.

> Status — **early development.** Only the authentication surface (login, OTP, password sign-in, invitation-based registration) is implemented. Everything else is documented in [`docs/`](./docs/) and waiting to be built.

---

## Highlights

- **100% Jetpack Compose UI** on Material 3, edge-to-edge, dark-mode-aware, EN + PL.
- **Adaptive layout** via Material 3 `NavigationSuiteScaffold` — phone, foldable, and tablet from one codebase.
- **Cloudflare Turnstile** captcha integrated through a Compose `Dialog` + `WebView` bridge.
- **App Links** auto-verified for invitation deep links (`https://skipperclub.app/*/register?invitation=…`).
- **RFC 7807** Problem-Details error mapping to a typed `AuthError` sealed class.
- Backed by an OpenAPI 3.1 + AsyncAPI 3.0 contract — see [`docs/api/`](./docs/api/).

For the full technical picture (versions, target architecture, modularization plan) see **[`TECH_STACK.md`](./TECH_STACK.md)**.

---

## Tech stack at a glance

| Area              | Choice                                                            |
| ----------------- | ----------------------------------------------------------------- |
| Language          | Kotlin **2.3.21** (K2)                                            |
| UI                | Jetpack Compose (BoM `2026.05.01`) + Material 3                   |
| Build             | Android Gradle Plugin **9.2.1**, Gradle 9.x                       |
| SDK               | `minSdk 26`, `targetSdk 36`, `compileSdk 36.1`                    |
| Network           | OkHttp 5.3 + kotlinx.serialization 1.11                           |
| Concurrency       | Coroutines 1.11 + `Flow`                                          |
| Architecture      | Unidirectional data flow, ViewModel + StateFlow (planned)         |
| DI                | Hilt (planned — currently object singletons)                      |
| Navigation        | Jetpack Navigation 3 (planned)                                    |

---

## Project layout

```
skipper-club-android/
├── app/                            ← single Android application module (today)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/app/skipperclub/
│       │   ├── MainActivity.kt     ← Compose host, auth orchestration, deep-link intake
│       │   ├── data/               ← AuthApi, AuthError, AuthModels, SessionStore
│       │   └── ui/
│       │       ├── auth/           ← Login, Password, OTP, Invitation screens
│       │       ├── turnstile/      ← Cloudflare Turnstile WebView dialog
│       │       └── theme/          ← Color, Type, Theme
│       └── res/
│           ├── values/             ← English strings
│           └── values-pl/          ← Polish strings
├── gradle/
│   └── libs.versions.toml          ← single source of truth for versions
├── docs/
│   ├── api/                        ← OpenAPI 3.1 + AsyncAPI 3.0 + per-module docs
│   ├── prd/                        ← Product Requirements Documents (13 modules)
│   └── ux/                         ← Screen specs + flow diagrams
├── build.gradle.kts                ← top-level (plugins only)
├── settings.gradle.kts
├── TECH_STACK.md                   ← technical reference (read this)
├── CLAUDE.md                       ← guidance for Claude Code
└── AGENTS.md                       ← guidance for other coding agents
```

The target multi-module structure (`:app`, `:core:*`, `:feature:*`) is described in [`TECH_STACK.md` §5](./TECH_STACK.md#5-module-structure).

---

## Getting started

### Requirements

- **JDK 21** — pinned in `gradle/gradle-daemon-jvm.properties`, fetched automatically via the Foojay toolchain plugin.
- **Android Studio Narwhal (2025.3.1)** or newer — required for AGP 9.
- An Android device or emulator running **API 26+**.

### Clone & open

```bash
git clone <repo-url> skipper-club-android
cd skipper-club-android
```

Open the directory in Android Studio. Gradle will sync and download the SDK platform / toolchain on first launch. No manual `local.properties` edits are needed beyond `sdk.dir`.

### Build & install a debug build

```bash
./gradlew :app:installDebug
```

Then launch **SkipperClub** from the launcher.

### Run tests

```bash
./gradlew :app:testDebugUnitTest                # JVM unit tests
./gradlew :app:connectedDebugAndroidTest        # instrumented tests (requires device/emulator)
```

### Useful Gradle tasks

| Task                                | Purpose                                  |
| ----------------------------------- | ---------------------------------------- |
| `:app:assembleDebug`                | Build the debug APK without installing.  |
| `:app:lintDebug`                    | Run Android Lint on the debug variant.   |
| `:app:dependencies`                 | Inspect the dependency tree.             |
| `./gradlew --refresh-dependencies`  | Force a clean dependency resolution.     |

A `Makefile` wraps the most common Gradle commands for ergonomics:

| Make target           | Equivalent Gradle invocation                          |
| --------------------- | ----------------------------------------------------- |
| `make help`           | Prints this table.                                    |
| `make assemble-debug` | `./gradlew :app:assembleDebug`                        |
| `make assemble-release` | `./gradlew :app:assembleRelease` (unsigned)         |
| `make bundle-release` | `./gradlew :app:bundleRelease` (Play AAB)             |
| `make install-debug`  | `./gradlew :app:installDebug`                         |
| `make run`            | Installs the debug APK and starts `MainActivity`.     |
| `make test`           | `./gradlew :app:testDebugUnitTest`                    |
| `make connected-check`| `./gradlew :app:connectedDebugAndroidTest`            |
| `make lint`           | `./gradlew :app:lintDebug`                            |
| `make clean`          | `./gradlew clean`                                     |
| `make dependencies`   | `./gradlew :app:dependencies`                         |

Release version metadata can be overridden without editing Gradle files:

```bash
make bundle-release VERSION_CODE=3 VERSION_NAME=0.2.1
```

### Continuous integration

The `Build platforms` workflow (`.github/workflows/build-platforms.yml`) runs on every pull request:

- **Unit tests & Lint** on `ubuntu-latest` with JDK 21 (`make test`, `make lint`).
- **Build Medium Phone / Medium Tablet** matrix that assembles a debug APK and runs `make connected-check` on emulator profiles `medium_phone` and `medium_tablet` (API 34, `google_apis`, `x86_64`) via `reactivecircus/android-emulator-runner`.

Reports (unit tests, lint, instrumented tests) and the debug APKs are uploaded as workflow artifacts.

---

## Configuration

Build-time configuration is exposed through `BuildConfig` fields declared in `app/build.gradle.kts`:

| Field             | Default value                            | Purpose                                       |
| ----------------- | ---------------------------------------- | --------------------------------------------- |
| `API_BASE_URL`    | `https://api.skipperclub.app`            | REST API root (versioned with `/v1` per call) |
| `TURNSTILE_URL`   | `https://skipperclub.app/turnstile`      | HTML page that hosts the Turnstile widget     |

There is no `.env`, no Gradle secrets, and no flavor matrix yet. When staging / production builds are needed, introduce build types — not product flavors — and override these fields from a Gradle property.

---

## Deep links

The manifest auto-verifies one App Link pattern:

```
https://skipperclub.app/<anything>/register?invitation=<code>
```

Tapping such a link opens the app on the **Join by invitation** screen with the code prefilled. The handler lives in `MainActivity.consumeInvitationLink()`.

To test locally:

```bash
adb shell am start -W -a android.intent.action.VIEW \
  -d "https://skipperclub.app/en/register?invitation=ABCD1234" \
  app.skipperclub
```

---

## Documentation

| Where                                                | What                                                                            |
| ---------------------------------------------------- | ------------------------------------------------------------------------------- |
| [`TECH_STACK.md`](./TECH_STACK.md)                   | Authoritative technical stack, architecture, modularization plan, known gaps.   |
| [`CLAUDE.md`](./CLAUDE.md)                           | Working agreement for Claude Code sessions in this repo.                        |
| [`AGENTS.md`](./AGENTS.md)                           | Equivalent guidance for non-Claude AI coding agents.                            |
| [`docs/prd/index.md`](./docs/prd/index.md)           | Product Requirements Documents (13 modules).                                    |
| [`docs/api/index.md`](./docs/api/index.md)           | API overview + OpenAPI 3.1 + AsyncAPI 3.0 contracts.                            |
| [`docs/ux/`](./docs/ux/)                             | Screen specs and flow diagrams.                                                 |

---

## Contributing

This is a small private project. Conventions worth knowing before opening a PR:

- **One feature = one focused PR.** Don't piggyback refactors onto unrelated changes.
- **Stateless Composables + previews.** Every screen ships `@Preview` variants for light, dark, and at least `en` + `pl` (see `LoginScreen.kt`).
- **No hardcoded strings.** Add a `string` resource and a Polish translation in `values-pl/strings.xml`.
- **Localize error messages** at the UI layer; the data layer returns typed `AuthError` instances, not human strings.
- **Don't pin Compose artifact versions** — they come from the BoM in `libs.versions.toml`.

---

## License

Proprietary. © SkipperClub. All rights reserved.
