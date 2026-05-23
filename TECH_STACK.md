# Tech Stack & Engineering Requirements

This document describes the technical stack, toolchain, target architecture, and engineering requirements for the SkipperClub Android client. It is the source of truth for **what** we build with and **how** the codebase is organised.

For product scope see [`docs/prd/`](./docs/prd/index.md). For API contracts see [`docs/api/`](./docs/api/index.md).

---

## 1. Platform Targets

| Property              | Value                                              |
| --------------------- | -------------------------------------------------- |
| `minSdk`              | **26** (Android 8.0 Oreo)                          |
| `targetSdk`           | **36** (Android 16)                                |
| `compileSdk`          | **36.1** (Android 16 QPR1, AGP 9 release API)      |
| Form factors          | Phone (portrait/landscape), foldable, tablet       |
| Orientation           | All (UI must be adaptive)                          |
| Languages             | English (default), Polish (`values-pl`)            |
| Edge-to-edge          | Required (`enableEdgeToEdge()` in `MainActivity`)  |
| Dark mode             | Required (system-driven)                           |
| Dynamic color         | Supported, off by default (brand palette wins)     |

Minimum SDK was chosen to cover the bulk of active Android devices in the sailing-enthusiast demographic while keeping the Compose/Coroutines surface modern.

---

## 2. Build Toolchain

| Tool                     | Version          | Notes                                                                                             |
| ------------------------ | ---------------- | ------------------------------------------------------------------------------------------------- |
| Android Gradle Plugin    | **9.2.1**        | Declared in `gradle/libs.versions.toml`. Uses the new `compileSdk { release(...) }` DSL.          |
| Kotlin                   | **2.3.21**       | K2 compiler. Compose plugin is `org.jetbrains.kotlin.plugin.compose`.                             |
| Gradle                   | **9.x**          | Wrapper pinned in `gradle/wrapper/`. JDK selection via Foojay toolchain plugin.                   |
| JDK                      | **21 (toolchain)** + Java 11 source/target | `gradle/gradle-daemon-jvm.properties` pins `toolchainVersion=21`; bytecode targets 11. |
| KSP                      | To be added when DI / Room is introduced | Prefer KSP over kapt for all annotation processors.                       |
| Convention plugins       | Planned (`build-logic` included build) | Adopt once a second module is created.                                      |

### Dependency management

- Single **version catalog** at `gradle/libs.versions.toml`. **All** dependency versions live here — no hardcoded versions in module `build.gradle.kts` files.
- Repositories are restricted to `google()` and `mavenCentral()` with `FAIL_ON_PROJECT_REPOS`. Do not introduce JCenter, JitPack, or per-module repos without discussion.
- Use **Compose BoM** (`androidx-compose-bom`) for every `androidx.compose.*` dependency so versions stay aligned. Do not pin individual Compose artifacts.

### Build configuration

- `buildConfigField` exposes runtime endpoints:
  - `API_BASE_URL` → `https://api.skipperclub.app`
  - `TURNSTILE_URL` → `https://skipperclub.app/turnstile`
- Release builds **must** enable `isMinifyEnabled = true` + R8 (currently disabled — see [§10 Roadmap](#10-roadmap--known-gaps)).
- Signing config is local-only today; production signing will be wired through Gradle properties / Play App Signing.

---

## 3. UI & Design System

| Concern                | Choice                                                                                          |
| ---------------------- | ----------------------------------------------------------------------------------------------- |
| UI toolkit             | **Jetpack Compose** (BoM `2026.05.01`)                                                           |
| Design language        | **Material 3** (`androidx.compose.material3`)                                                   |
| Adaptive scaffolding   | `material3-adaptive-navigation-suite` → `NavigationSuiteScaffold`                               |
| Theming                | `app/src/main/java/app/skipperclub/ui/theme/` — `SkipperClubTheme`, brand palette, typography   |
| Iconography            | Vector drawables in `res/drawable/ic_*.xml`. Prefer Material Symbols where possible.            |
| Image loading          | **Coil 3** (`io.coil-kt.coil3:coil-compose` + `coil-network-okhttp`)                            |
| Activity model         | **Single Activity** (`MainActivity`) hosting all Compose content                                |
| System UI              | Edge-to-edge with `safeDrawingPadding()`/`imePadding()`; window soft input mode `adjustResize`. |

### Component conventions

- Screen Composables are **stateless** — they receive state and callbacks. State is held in a `…Screen` wrapper that owns `rememberSaveable` / a `ViewModel`.
- Every screen ships a `@Preview` for **light**, **dark**, and at least one **locale** (`en`, `pl`). See `LoginScreen.kt` for the pattern.
- Strings live in `res/values/strings.xml` (English) and `res/values-pl/strings.xml` (Polish). **No hardcoded user-facing text** in Kotlin.
- Spacing, corner radii, and elevation use the Material 3 tokens where available. Custom values are constants in `ui/theme/`.

---

## 4. Architecture

We follow the official Android architecture guidance: a reactive, unidirectional data flow (UDF) split into three layers.

```
┌──────────────────────────────────────────────────────────────┐
│  UI layer            Composables + ViewModel + UiState       │
│                      (StateFlow<UiState>, Channel<Event>)    │
├──────────────────────────────────────────────────────────────┤
│  Domain layer        Use cases (suspend functions)           │
│                      Pure Kotlin, no Android deps            │
├──────────────────────────────────────────────────────────────┤
│  Data layer          Repositories → Network / Local sources  │
│                      kotlinx.coroutines.Flow boundaries      │
└──────────────────────────────────────────────────────────────┘
```

Principles:

- **State down, events up.** Composables observe `StateFlow<UiState>` and emit user intents to a `ViewModel`. ViewModels never expose mutable state to the UI.
- **Single source of truth.** For persisted data the local cache (DataStore / Room) is authoritative; network sync updates the cache, the UI observes the cache. This is the **offline-first** posture recommended for 2026.
- **Repositories are the only callers of data sources.** ViewModels do not call OkHttp / DataStore directly.
- **Use cases are optional.** Introduce one only when logic is shared across ViewModels or genuinely complex.
- **No Android types in `domain/`.** Repositories may expose `kotlinx.coroutines.Flow` but not `Context`, `LiveData`, or `View`.

### Navigation

- Target: **Jetpack Navigation 3** (stable in 2026, Compose-first, type-safe, back-stack-owned-by-app). Adopt it as soon as the second navigation graph is needed.
- Current: a sealed `AuthDestination` + `rememberSaveable(stateSaver = …)` in `MainActivity`. This is acceptable while only the auth flow exists; migrate when the post-login surface lands.
- **Deep links** must continue to route to `App Links` (`android:autoVerify="true"`). The invitation flow at `https://skipperclub.app/*/register?invitation=…` is the reference implementation.

---

## 5. Module Structure

The app is currently a **single `:app` module**. The target structure mirrors the official Now in Android sample:

```
:app                       ← entry point: MainActivity, theme bootstrap, Hilt app, app-level nav graph
:core
  :core:common             ← dispatchers, Result wrappers, pure-Kotlin utilities
  :core:designsystem       ← SkipperClubTheme, colors, typography, reusable components
  :core:ui                 ← shared Composables that depend on data types (avatars, error banners)
  :core:domain             ← cross-feature use cases
  :core:data               ← repository implementations
  :core:network            ← OkHttp/Retrofit client, interceptors, Turnstile, Socket.IO
  :core:datastore          ← DataStore wiring + Tink-backed encryption
  :core:model              ← shared data classes (network DTOs live in :core:network)
  :core:testing            ← test fakes, fixtures, Compose test rules
:feature
  :feature:auth            ← login, OTP, password, invitation registration (existing screens)
  :feature:cruises         ← cruise list, detail, create, manage participants
  :feature:posts           ← social feed, post detail, composer
  :feature:messages        ← chat list, conversation, Socket.IO presence
  :feature:notifications   ← in-app center + push handling
  :feature:friends
  :feature:reviews
  :feature:profile
  :feature:spots
  :feature:check-ins
  :feature:sailing-brief
```

Rules:

- Feature modules **depend on `:core:*` modules**, never on each other.
- `:app` depends on every feature; features stay decoupled and reach each other through the `:app` navigation graph.
- Each feature owns its own `viewmodel/`, `ui/`, `navigation/`, and (if needed) `data/` packages.
- A feature module never exposes `Activity` or `Fragment` — only Composables and a `NavGraph` extension function.

When the project crosses ~3 features, add an included build at `build-logic/` with **convention plugins** (`skipperclub.android.library`, `skipperclub.android.feature`, `skipperclub.kotlin.library`, `skipperclub.android.application`) so module boilerplate stops being copy-pasted.

---

## 6. Data, Networking & Real-Time

### REST client

| Concern             | Today                                       | Target                                                                   |
| ------------------- | ------------------------------------------- | ------------------------------------------------------------------------ |
| HTTP engine         | Raw **OkHttp 5.3** with manual `Request.Builder` | OkHttp 5.x **+ Retrofit** with `kotlinx.serialization` converter         |
| Serialization       | `kotlinx.serialization` 1.11                | unchanged                                                                |
| Error model         | `AuthError` sealed class mapping RFC 7807 `application/problem+json` | Generalise to `ApiError` shared by all features |
| Auth header         | Per-request bearer (not yet implemented)    | Authenticator/interceptor adding `Authorization: Bearer …` from `SessionStore` + refresh-token flow |
| Localization        | `Accept-Language: ${Locale.getDefault().toLanguageTag()}` interceptor | unchanged                                          |
| Captcha             | `X-Turnstile-Token` per request from the WebView dialog | unchanged                                                  |

The raw-OkHttp approach is fine for four auth endpoints. Migrate to Retrofit when the second feature lands so we get type-safe interfaces, `suspend` returns, and shared interceptors for free.

### WebSocket / real-time

The server exposes Socket.IO namespaces (`/chat`, `/notifications`) per [`docs/api/asyncapi.yaml`](./docs/api/asyncapi.yaml). Use:

- `io.socket:socket.io-client` (official Java client) wrapped behind a `RealtimeClient` interface in `:core:network`.
- Lifecycle binding: connect on first subscriber, disconnect on idle, scope to the user session in `SessionStore`.
- Expose events as `Flow<…>` so ViewModels can `collect` without callback plumbing.

### Persistence

| Use case                           | Library                                                                                                |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------ |
| Tokens, small key/value, settings  | **DataStore (Preferences)** + **Google Tink** (AEAD primitive backed by Android Keystore)              |
| Structured cache (feed, cruises)   | **Room** with Paging 3 (introduce when first feature needs offline)                                    |
| Large media                        | `cacheDir`, served via Coil's disk cache                                                               |

`EncryptedSharedPreferences` is **deprecated** (Jetpack Security `1.1.0-alpha07`+). Do not adopt it. Use DataStore + Tink for new secure storage.

Current `SessionStore` is an in-memory `MutableStateFlow` — replace it with a DataStore-backed implementation **before** the app ships, otherwise sessions die with the process.

---

## 7. Dependency Injection

- Target: **Hilt** (Google-recommended, KSP-based, compile-time verified). One `@HiltAndroidApp` Application class, ViewModels via `hiltViewModel()` from feature Composables.
- Hilt is **not yet wired up** — the current auth flow uses object singletons (`AuthApi`, `SessionStore`). Introduce Hilt at the same time as the `:core:network` and `:core:datastore` extraction.
- For pure-Kotlin modules (`:core:domain`, `:core:model`) prefer **constructor injection** with no DI framework annotations leaking in.

---

## 8. Concurrency

- All async work uses **Kotlin Coroutines 1.11+** + **`Flow`**.
- ViewModels expose state as `StateFlow<UiState>` using `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.
- Inject `CoroutineDispatcher`s via a `Dispatchers` wrapper (`AppDispatchers(io, default, main)`) so tests can override with `UnconfinedTestDispatcher`. Do not call `Dispatchers.IO` directly outside `:core:common`.
- Network calls are `suspend` functions. WebSocket events become `Flow`s via `callbackFlow`.

---

## 9. Testing & Quality

| Layer                 | Tooling                                                                                                  |
| --------------------- | -------------------------------------------------------------------------------------------------------- |
| Unit tests            | JUnit 4 + `kotlinx-coroutines-test` + Turbine (for `Flow`) + MockK                                       |
| Compose UI tests      | `androidx.compose.ui:ui-test-junit4` with Hilt test rule                                                 |
| Screenshot tests      | **Roborazzi** (JVM-fast, Robolectric-backed) — capture every screen Preview                              |
| End-to-end            | **Maestro** flows checked into `e2e/` (when adopted)                                                     |
| Static analysis       | **Detekt** + **ktlint** via Spotless (to be added)                                                       |
| Dependency analysis   | `com.autonomousapps.dependency-analysis` Gradle plugin (recommended)                                     |

Conventions:

- Every screen Composable has at least one Roborazzi test driven from its `@Preview` (use `ComposablePreviewScanner` to auto-discover them).
- ViewModel tests use `runTest { … }` and a fake repository — never hit the network.
- Treat the `:core:network` `AuthApi`-style tests as integration tests against MockWebServer.

---

## 10. Roadmap / Known Gaps

These are intentionally noted here so they are not lost between conversations:

1. **R8 / minification** is disabled on `release` builds — re-enable and add `proguard-rules.pro` entries once obfuscation breaks anything.
2. **Session persistence** — `SessionStore` is in-memory only; replace with DataStore + Tink before first internal release.
3. **Refresh-token flow** is not implemented — needs an OkHttp `Authenticator` once the `/v1/auth/refresh` endpoint is wired in.
4. **Navigation 3** — adopt when the post-login surface lands; current `AuthDestination` sealed-class scheme is intentional placeholder.
5. **Hilt** — introduce alongside the first non-auth feature.
6. **Push notifications** — Firebase Cloud Messaging registration + token sync (see `docs/api/notifications/push-notifications.md`).
7. **Convention plugins** — extract once we have ≥3 modules.
8. **Baseline Profile** — generate from a Macrobenchmark module when the home feed exists, to keep cold-start fast.

---

## 11. References

- [Guide to app architecture](https://developer.android.com/topic/architecture) — official, updated for 2026
- [Guide to Android app modularization](https://developer.android.com/topic/modularization)
- [Now in Android sample](https://github.com/android/nowinandroid) — modularization + convention plugin reference
- [Jetpack Navigation 3 is stable](https://developer.android.com/blog/posts/jetpack-navigation-3-is-stable)
- [Jetpack Compose April '26 release notes](https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html)
- [Coil 3 docs](https://coil-kt.github.io/coil/) — image loading
- [Goodbye EncryptedSharedPreferences (2026 migration guide)](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a)
- [Roborazzi](https://github.com/takahirom/roborazzi) — JVM screenshot testing
