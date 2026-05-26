# AGENTS.md

Guidance for AI coding agents (Cursor, OpenAI Codex, Aider, etc.) working in the SkipperClub Android repo. This file mirrors the working agreement in [`CLAUDE.md`](./CLAUDE.md) in the format expected by tools that look for `AGENTS.md`.

If you are operating as Claude Code, prefer the more detailed [`CLAUDE.md`](./CLAUDE.md). The two files are kept in sync; if they disagree, treat `CLAUDE.md` as authoritative.

---

## Project snapshot

- **What:** native Android client for SkipperClub — a sailing social network + crew-recruitment platform.
- **Status:** early development. Only the authentication surface is implemented (login, OTP, password sign-in, invitation-based registration). The rest of the product surface is specified in [`docs/prd/`](./docs/prd/) but not yet built.
- **API:** REST (OpenAPI 3.1, [`docs/api/openapi.yaml`](./docs/api/openapi.yaml)) at `https://api.skipperclub.app/v1` + Socket.IO namespaces for chat / notifications (AsyncAPI 3.0, [`docs/api/asyncapi.yaml`](./docs/api/asyncapi.yaml)).
- **Languages shipped:** English (default), Polish (`values-pl/`).

For the full picture read [`TECH_STACK.md`](./TECH_STACK.md).

---

## Stack you must match

All versions are declared in `gradle/libs.versions.toml`. Do not hardcode versions in module Gradle files.

| Area              | Choice                                                                              |
| ----------------- | ----------------------------------------------------------------------------------- |
| Language          | Kotlin **2.3.21** (K2)                                                              |
| Build             | AGP **9.2.1**, Gradle 9.x, JDK 17 toolchain, Java 11 bytecode                       |
| SDK               | `minSdk 26`, `targetSdk 36`, `compileSdk 36.1`                                      |
| UI                | Jetpack Compose (BoM `2026.05.01`) + Material 3 + adaptive navigation suite         |
| Network           | OkHttp 5.3 + kotlinx.serialization 1.11 (Retrofit planned as code grows)            |
| Concurrency       | Coroutines 1.11 + `Flow`                                                            |
| Repos allowed     | `google()` + `mavenCentral()` only (`FAIL_ON_PROJECT_REPOS`)                        |
| Annotation procs  | **KSP** only — no kapt                                                              |

**Target stack (not yet wired up):** Hilt for DI, Jetpack Navigation 3 for routing, DataStore + Google Tink for secure persistence, Coil 3 for image loading, Roborazzi for screenshot tests, `io.socket:socket.io-client` for real-time. Choose these names when you introduce the corresponding capability. See [`TECH_STACK.md`](./TECH_STACK.md) §3–§9 for the rationale.

---

## Project structure

```
app/                                 ← single Android application module (today)
  src/main/java/app/skipperclub/
    MainActivity.kt                  ← Compose host + auth orchestration + deep-link intake
    data/                            ← AuthApi, AuthError, AuthModels, SessionStore (in-memory!)
    ui/auth/                         ← Login, Password, OTP, Invitation screens
    ui/turnstile/                    ← Cloudflare Turnstile WebView dialog
    ui/theme/                        ← Color, Type, Theme
  src/main/res/values/               ← English strings
  src/main/res/values-pl/            ← Polish strings
gradle/libs.versions.toml            ← single source of truth for versions
docs/api/                            ← OpenAPI + AsyncAPI + per-module API docs
docs/prd/                            ← Product Requirements Documents (13 modules)
docs/ux/                             ← Screen specs + flow diagrams
```

The **target** multi-module layout (`:app`, `:core:common`, `:core:designsystem`, `:core:network`, `:core:data`, `:core:datastore`, `:core:domain`, `:core:model`, `:core:ui`, `:core:testing`, `:feature:auth`, `:feature:cruises`, `:feature:posts`, `:feature:messages`, `:feature:notifications`, `:feature:friends`, `:feature:reviews`, `:feature:profile`, `:feature:spots`, `:feature:check-ins`, `:feature:sailing-brief`) is described in [`TECH_STACK.md` §5](./TECH_STACK.md#5-module-structure). Mirrors the Now in Android sample.

---

## Architecture rules

- **Unidirectional data flow.** State flows down, events flow up.
- **Three layers:** UI (Composables + ViewModel + UiState) → Domain (optional use cases) → Data (repositories → network / local). No layer reaches across — ViewModels do not call OkHttp / DataStore directly.
- **Offline-first.** When persistence enters the picture the local store (DataStore / Room) is the single source of truth; network sync updates the store; UI observes the store.
- **Single Activity.** `MainActivity` is the only Activity. New surfaces are Composables hosted via the navigation graph.
- **Stateless screen Composables.** A `…Screen(...)` wrapper owns state; a private `…ScreenContent(...)` takes plain state + callbacks and is what `@Preview`s render. See `app/src/main/java/app/skipperclub/ui/auth/LoginScreen.kt`.
- **Edge-to-edge.** `enableEdgeToEdge()` is on; use `imePadding()` + `safeDrawingPadding()`. Window soft input mode is `adjustResize`.

---

## Code-style conventions

### Compose & UI

- Material 3 components only.
- Every new screen ships at least three `@Preview`s: light EN, dark, and PL (busy state with an error message).
- No hardcoded user-facing strings. Add an entry to **both** `res/values/strings.xml` and `res/values-pl/strings.xml`.
- Image loading uses **Coil 3** (`AsyncImage`) once introduced. No Glide.

### Networking & errors

- Errors are typed sealed classes mapped from `application/problem+json` ([RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807)). See `data/AuthError.kt` and `AuthApi.toAuthError()` for the pattern.
- Localized error text is resolved in the UI layer, not in `data/`. Repositories raise `AuthError` (or future `ApiError`) and the screen maps it to a `stringResource(...)`.
- Captcha-gated endpoints require the `X-Turnstile-Token` header obtained from `TurnstileDialog`.
- Every request includes `Accept-Language: ${Locale.getDefault().toLanguageTag()}` — keep that header on every new endpoint.

### Concurrency

- All async work is `suspend` + `Flow`. ViewModels expose `StateFlow<UiState>` with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Initial)`.
- Inject `CoroutineDispatcher`s via an `AppDispatchers` wrapper once `:core:common` exists. Do not call `Dispatchers.IO` directly in feature code.
- Do not wrap an already main-safe suspending call (e.g. OkHttp) in `withContext(Dispatchers.IO)`.

### State, ViewModel, DI

- Today there is no `ViewModel` and no DI framework — auth state is hoisted to a Composable in `MainActivity`. Acceptable only because the surface is tiny.
- **For any new feature: introduce a `ViewModel` + Hilt at the same time.** Do not bolt new state onto `SkipperClubApp`.

### Persistence

- Do **not** use `EncryptedSharedPreferences` — deprecated in Jetpack Security 1.1.0+.
- Use **DataStore (Preferences)** with **Google Tink** for secret-grade data.
- Fixing the in-memory `SessionStore` is the first persistence task: replace `MutableStateFlow<SessionResponse?>` with a DataStore-backed implementation that survives process death.

---

## Build & test commands

```bash
./gradlew :app:assembleDebug                    # build a debug APK
./gradlew :app:installDebug                     # install on the connected device/emulator
./gradlew :app:lintDebug                        # Android Lint
./gradlew :app:testDebugUnitTest                # JVM unit tests
./gradlew :app:connectedDebugAndroidTest        # instrumented tests (device required)
./gradlew :app:debugUnitTestCoverage            # JaCoCo HTML/XML coverage for JVM unit tests
./gradlew :app:debugCombinedTestCoverage        # JaCoCo coverage for unit + connected tests
./gradlew --refresh-dependencies                # force a clean dependency resolution
```

A `Makefile` exposes the same commands as `make assemble-debug`, `make install-debug`, `make run`, `make test`, `make connected-check`, `make coverage`, `make coverage-connected`, `make lint`, `make clean`, `make dependencies`. Run `make help` for the full list. The `Build platforms` workflow (`.github/workflows/build-platforms.yml`) drives CI through these `make` targets on JDK 21 with `pixel_10` and `pixel_tablet` emulators (API 34) — keep them in sync when adding new CI checks.

A UI change is not "done" until at least:

1. `./gradlew :app:assembleDebug` succeeds.
2. `./gradlew :app:lintDebug` is clean.
3. `./gradlew :app:testDebugUnitTest` is green.
4. The affected `@Preview`s have been rendered (or captured via Roborazzi once introduced).

If you cannot run the build or render the previews, say so explicitly in the PR description rather than claiming success.

---

## When to ask vs. just do it

- **Just do it:** edits to existing screens, adding strings, fixing a bug, adding a `@Preview`, expanding tests, refactoring inside a single file.
- **Ask first:** introducing a new library, changing `libs.versions.toml`, creating a new module, restructuring packages, touching the signing config or `release` build type, changing `minSdk` / `targetSdk`.

---

## Things that look wrong but aren't

- `MainActivity` is large because it owns the entire auth orchestration. It will shrink to a shell once Navigation 3 + Hilt land — don't pre-emptively split it.
- `SkipperClubTheme` has `dynamicColor = false` by default. Brand identity wins over Material You here.
- `TurnstileDialog` runs a scoped `WebView` with JavaScript enabled. That's deliberate — it loads our own page to satisfy the Turnstile challenge. Don't "modernize" it without a replacement plan.
- Raw OkHttp instead of Retrofit. Intentional for four auth endpoints. When the second feature lands, introduce Retrofit + extract `:core:network` in the same change.
- `SessionStore` is in-memory only. Known gap, tracked in [`TECH_STACK.md` §10](./TECH_STACK.md#10-roadmap--known-gaps).

---

## Don'ts

- ❌ Pin Compose artifact versions individually (use the BoM).
- ❌ Add JitPack / JCenter / third-party Maven mirrors.
- ❌ Use kapt — KSP only.
- ❌ Add `EncryptedSharedPreferences` (deprecated).
- ❌ Add a `README.md` per package "for documentation". Code + this file + `TECH_STACK.md` are the docs.
- ❌ Disable R8 once it has been enabled — fix the keep rule instead.
- ❌ Create a commit unless the user asks for one.

---

## Where to look

| If you're about to…                              | Read this first                                                          |
| ------------------------------------------------ | ------------------------------------------------------------------------ |
| Pick a library or version                        | [`TECH_STACK.md`](./TECH_STACK.md) + `gradle/libs.versions.toml`         |
| Add a new screen                                 | `app/src/main/java/app/skipperclub/ui/auth/LoginScreen.kt`               |
| Add a new API endpoint                           | `app/src/main/java/app/skipperclub/data/AuthApi.kt` + `AuthError.kt`     |
| Add a captcha-gated mutation                     | `app/src/main/java/app/skipperclub/ui/turnstile/TurnstileDialog.kt`      |
| Handle a new deep link                           | `MainActivity.consumeInvitationLink` + `app/src/main/AndroidManifest.xml`|
| Add a localized string                           | `res/values/strings.xml` + `res/values-pl/strings.xml`                   |
| Understand a product feature                     | [`docs/prd/PRD-XXX-*.md`](./docs/prd/)                                   |
| Understand an API call                           | [`docs/api/openapi.yaml`](./docs/api/openapi.yaml) + the per-module folder under `docs/api/` |
