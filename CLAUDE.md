# CLAUDE.md

Working agreement for Claude Code in the SkipperClub Android repo. Read this first; it captures the conventions that are easy to miss from the code alone.

For a complete tech reference see [`TECH_STACK.md`](./TECH_STACK.md). For the product surface see [`docs/prd/index.md`](./docs/prd/index.md).

---

## What this app is

Native Android client for **SkipperClub** — a sailing-focused social network + crew-recruitment platform. The backend exposes a REST API (OpenAPI 3.1 at [`docs/api/openapi.yaml`](./docs/api/openapi.yaml)) and Socket.IO namespaces for chat + notifications (AsyncAPI 3.0 at [`docs/api/asyncapi.yaml`](./docs/api/asyncapi.yaml)).

Today the app implements the **authentication surface only**:

- Email + password sign-in
- One-time-code (OTP) login via email
- Registration via admin-issued invitation codes (deep-link aware)
- Cloudflare Turnstile captcha challenge between client and API

Everything else (cruises, posts, messages, friends, reviews, notifications, …) lives in [`docs/prd/`](./docs/prd/) and is **not yet built**.

---

## Tech ground truth

Use these versions when adding dependencies. **All versions live in `gradle/libs.versions.toml`** — never hardcode a version in `app/build.gradle.kts`.

- Kotlin **2.2.10** (K2 compiler, Compose plugin = `org.jetbrains.kotlin.plugin.compose`)
- AGP **9.2.1**, Gradle 9.x
- `minSdk 26`, `targetSdk 36`, `compileSdk 36.1`
- Compose BoM **`2025.12.00`** (Compose 1.11 core, April '26 release)
- Material 3 + `material3-adaptive-navigation-suite`
- OkHttp **4.12** + `kotlinx.serialization` 1.9
- Coroutines **1.10**
- Edge-to-edge is on (`enableEdgeToEdge()` in `MainActivity`)
- Dynamic color is **off by default** — brand palette wins

When you reach for a library that isn't already in `libs.versions.toml`, check [`TECH_STACK.md`](./TECH_STACK.md) first — the **target** stack (Hilt, Retrofit, Navigation 3, Coil 3, DataStore + Tink, Roborazzi, Socket.IO client) is named there. Match those choices unless there's a reason not to.

---

## Code map

```
app/src/main/java/app/skipperclub/
├── MainActivity.kt              ← Compose host. Owns auth state, deep-link parsing, Turnstile gate.
├── data/
│   ├── AuthApi.kt               ← Raw OkHttp calls against /v1/auth/* and /v1/invitations/register
│   ├── AuthError.kt             ← Typed errors mapped from RFC 7807 problem+json
│   ├── AuthModels.kt            ← @Serializable DTOs (requests + SessionResponse)
│   └── SessionStore.kt          ← In-memory MutableStateFlow<SessionResponse?>  (NOT persisted yet)
└── ui/
    ├── auth/
    │   ├── AuthDestination.kt   ← Sealed destinations + mapSaver for rememberSaveable
    │   ├── AuthScaffold.kt      ← Shared scaffold for auth screens
    │   ├── AuthErrorMessage.kt
    │   ├── LoginScreen.kt
    │   ├── PasswordScreen.kt
    │   ├── OtpVerifyScreen.kt
    │   ├── OtpCodeInput.kt
    │   └── InvitationRegisterScreen.kt
    ├── turnstile/TurnstileDialog.kt   ← Compose Dialog wrapping a WebView + JS bridge
    └── theme/                          ← Color, Type, Theme
```

The auth navigation is **deliberately a sealed class** (`AuthDestination`) saved through `mapSaver`, not Navigation 3. That's fine while only this flow exists — migrate to Navigation 3 when the post-login surface arrives ([`TECH_STACK.md` §4](./TECH_STACK.md#4-architecture)).

---

## Conventions worth following

### Compose screens

- Each screen is **stateless**. A public `LoginScreen(...)` wrapper owns the `rememberSaveable` state; a private `LoginScreenContent(...)` takes plain state + callbacks and is what `@Preview`s render. Match that shape when adding new screens.
- Every screen has **at least three previews**: light EN, dark, and a PL preview. The PL preview should populate fields and an error message so screenshots cover the busy state.
- Use **Material 3** components only. Reach for `OutlinedTextField`, `Button`, `Surface`, etc. — no Material 2 imports.
- Respect insets: `imePadding()` + `safeDrawingPadding()` + a `verticalScroll(rememberScrollState())` on auth screens. The window soft input mode is `adjustResize`.

### Strings & i18n

- **Never hardcode user-facing text.** Add a string in `res/values/strings.xml` **and** `res/values-pl/strings.xml`. The app ships both locales.
- Error messages are resolved in the UI layer (`MainActivity.SkipperClubApp` does the mapping). The data layer raises typed `AuthError` instances; do not put localized text in `data/`.
- API requests already include `Accept-Language: ${Locale.getDefault().toLanguageTag()}` — keep that header on every new endpoint.

### Networking

- The current implementation uses **raw OkHttp + `Request.Builder`**, not Retrofit. That's intentional for four endpoints; if you're adding a new feature with several endpoints, **introduce Retrofit + a `:core:network` extraction in the same change** rather than copying the OkHttp boilerplate.
- Error mapping must follow `application/problem+json` per [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807). See `AuthApi.toAuthError()` for the pattern (read `type`, `detail`, and `violations[].propertyPath`).
- Captcha-gated endpoints require the `X-Turnstile-Token` header; obtain the token by showing `TurnstileDialog` with an action string and awaiting `onSuccess`.

### State, ViewModel, DI

- We currently have **no `ViewModel` and no DI framework.** Auth state is hoisted to a Composable in `MainActivity`. This is acceptable only because the surface is tiny.
- For any new feature: **introduce a `ViewModel` + Hilt** at the same time. Don't bolt new state onto `SkipperClubApp` — `MainActivity` should become an empty shell that hosts a Navigation 3 graph.
- ViewModels expose `StateFlow<UiState>` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Initial)`. UI sends intents through plain callbacks (no shared `Channel<Event>` unless you genuinely need fire-and-forget signals).

### Persistence

- **Do not use `EncryptedSharedPreferences`** — it's deprecated in Jetpack Security 1.1.0+. Use **DataStore (Preferences)** with **Google Tink** for any secret-grade data ([migration guide](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a)).
- The first persistence task is fixing `SessionStore`: replace the in-memory `MutableStateFlow` with a DataStore-backed implementation that survives process death.

### Testing

- Run unit tests with `./gradlew :app:testDebugUnitTest`.
- Run instrumented tests with `./gradlew :app:connectedDebugAndroidTest` (needs a device/emulator).
- When you add Roborazzi (planned), drive screenshot tests from existing `@Preview`s via `ComposablePreviewScanner` rather than hand-writing each test.

---

## Working with this repo

### When to ask vs. just do it

- **Just do it**: edits to existing screens, adding strings, fixing a bug, adding a `@Preview`, expanding tests, refactoring inside a single file.
- **Ask first**: introducing a new library, changing `libs.versions.toml`, creating a new module, restructuring packages, touching the signing config or release build type, changing `minSdk` / `targetSdk`.

### Commits & branches

- Default branch is `main`. The recent history is a flat sequence of small focused commits (`feat:`, fix, refactor) — match that style.
- Don't create a commit unless the user asks. When they do, write the message yourself; don't dump the diff into the message body.

### Things that look wrong but aren't

- `MainActivity` is large (~450 LOC) because it owns the entire auth orchestration. It will shrink to a shell once Navigation 3 + Hilt land — don't pre-emptively split it.
- `dynamicColor = false` is the default in `SkipperClubTheme`. That's intentional: brand identity wins over Material You here.
- `TurnstileDialog` uses a `WebView` with `javaScriptEnabled = true`. Yes, that's a deliberate, scoped, single-purpose web view loading our own page — don't try to "modernize" it without a replacement plan.
- Raw OkHttp instead of Retrofit. Intentional for the auth surface. See [Networking](#networking) above.
- `SessionStore` is in-memory only. Known gap, tracked in [`TECH_STACK.md` §10](./TECH_STACK.md#10-roadmap--known-gaps).

### Build & verify before claiming done

For UI changes, run at least:

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

A `Makefile` wraps the common Gradle invocations (`make assemble-debug`, `make test`, `make lint`, `make connected-check`, `make run`). The `Build platforms` GitHub Actions workflow (`.github/workflows/build-platforms.yml`) runs unit tests + lint on JDK 21, then `connectedDebugAndroidTest` on `pixel_10` and `pixel_tablet` emulator profiles (API 34) for every PR. **Match the Makefile targets when adding CI steps** — the workflow calls `make` rather than `./gradlew` directly.

For changes that touch a Composable, also render the affected `@Preview`s in Android Studio or capture them via Roborazzi once that's set up. Don't report "done" on a UI change you haven't seen render.

---

## Don'ts

- ❌ Pin Compose artifact versions individually — they come from the BoM.
- ❌ Add JitPack / JCenter / third-party Maven mirrors. Repos are `google()` + `mavenCentral()` only.
- ❌ Introduce kapt for new annotation processors — use **KSP**.
- ❌ Use `Dispatchers.IO` directly outside `:core:common` (once that module exists). Inject an `AppDispatchers` wrapper.
- ❌ Wrap suspending calls in `withContext(Dispatchers.IO)` when the underlying library is already main-safe (OkHttp is).
- ❌ Add a `README.md` for every package "for documentation". Code + this file + `TECH_STACK.md` are the docs.
- ❌ Bypass `proguard-rules.pro` once R8 is enabled — fix the keep rule, don't disable minification.

---

## Pointers for common tasks

| If you're about to…                              | Read this first                                                  |
| ------------------------------------------------ | ---------------------------------------------------------------- |
| Add a new screen                                 | `ui/auth/LoginScreen.kt` (stateless content + preview pattern)   |
| Add a new API endpoint                           | `data/AuthApi.kt` + `data/AuthError.kt`                          |
| Add a captcha-gated mutation                     | `ui/turnstile/TurnstileDialog.kt` + `MainActivity` pending-action machinery |
| Handle a new deep link                           | `MainActivity.consumeInvitationLink` + `AndroidManifest.xml`     |
| Add a localized string                           | `res/values/strings.xml` + `res/values-pl/strings.xml`           |
| Pick a library for a new concern                 | [`TECH_STACK.md`](./TECH_STACK.md) §3–§9                         |
| Understand a product feature                     | [`docs/prd/PRD-XXX-*.md`](./docs/prd/)                           |
| Understand an API call                           | [`docs/api/openapi.yaml`](./docs/api/openapi.yaml) + the per-module folder under `docs/api/` |
