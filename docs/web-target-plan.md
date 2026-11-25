# Web target plan

Goal: ship Spheres as a desktop-first web app at a Firebase Hosting URL, built from the same `commonMain` code via a Kotlin/Wasm target. The large-screen layouts (rail/sidebar, list–detail panes, hover, context menus, keyboard handling) are reused as-is.

## Scope

In:
- Sign in with Google and Apple, full read/write of spheres, events, people, entries
- Radar, Due, Spheres, Map, People, Person, Settings, all edit sheets
- Avatar photo upload (file picker) and crop, location via browser geolocation
- JSON export (download), account deletion
- Browser history / deep links, web-appropriate keyboard shortcuts

Out (hidden on web, no replacement for now):
- Purchases: web reads the existing entitlement from Firestore, so buyers are unlocked; non-buyers see "Unlock in the iOS or Android app" with store links
- Contacts import / contact linking, call log integration
- Reminders / notifications (a tab-title overdue count is the only stand-in)
- Haptics, status bar styling

## Why this is cheap

- `FirestoreBackend` uses plain REST via Ktor (Firestore, Storage, Identity Toolkit, Cloud Functions). No Firebase SDK to replace.
- All `commonMain` dependencies publish `wasmJs` variants, including `posthog-kmp` 0.11.0.
- `commonMain` uses no JVM-only APIs (`Dispatchers.IO`, `java.*`, `synchronized`).
- Routes are already URL-shaped (`/people/{id}`, `/spheres/{id}`, `/events/{id}`).

## Phase 1 — Spike: it compiles and renders (~1 day)

1. `composeApp/build.gradle.kts`: add
   ```kotlin
   @OptIn(ExperimentalWasmDsl::class)
   wasmJs {
       outputModuleName = "spheres"
       browser { commonWebpackConfig { outputFileName = "spheres.js" } }
       binaries.executable()
   }
   ```
   and `wasmJsMain.dependencies { implementation(libs.ktor.client.js) }` (add `ktor-client-js` to `libs.versions.toml`).
2. `composeApp/src/wasmJsMain/kotlin/de/koenidv/spheres/main.kt`: `ComposeViewport(document.body!!) { App() }`.
3. `composeApp/src/wasmJsMain/resources/index.html`: full-viewport canvas host, white background, plus a pure-HTML splash reproducing the three bouncing pixel dots (pink/coral/purple from `Splash()` in `App.kt`), removed once Compose renders its first frame.
4. `platform/Platform.wasmJs.kt`: implement every `expect` in `platform/Platform.kt` with the trivial versions from the table below (stubs where noted). `BuildFlags` keeps working since it's generated into `commonMain`.
5. Run `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`, check that sign-in screen renders with the pixel fonts.

Done when: the app loads in Chrome, Firefox and Safari 18.2+, the sign-in screen renders, and both mobile targets still build.

### `expect` actuals on web

| API | Web implementation |
|---|---|
| `currentTimeMillis` | `Date.now()` |
| `randomUuid` | `crypto.randomUUID()` |
| `sha256Hex` | pure-Kotlin SHA-256 in `wasmJsMain` (WebCrypto is async-only, `expect` is sync) |
| `haptic`, `StatusBarEffect` | no-op |
| `PlatformStorage` | `localStorage` |
| `PlatformPhotoCache` | in-memory LRU first; IndexedDB later if avatar loads feel slow |
| `decodeImageBitmap` | `org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()` |
| `cropSquareAvatarJpeg` | Skia: draw cropped region to a `Surface`, `encodeToData(EncodedImageFormat.JPEG)`; fall back to an offscreen `<canvas>` + `toBlob` if skiko's web build lacks the JPEG encoder |
| `rememberPhotoPickerLauncher` | hidden `<input type="file" accept="image/*">`, read via `FileReader` → `ByteArray` |
| `rememberContactPickerLauncher` | no-op launcher; callers already gate on support |
| `shareJsonFile` | `Blob` + temporary `<a download>` |
| `openExternalUrl` | `window.open(url, "_blank", "noopener")` |
| `PlatformLocation` | `navigator.geolocation.getCurrentPosition`; `isGranted` via Permissions API |
| `PlatformNotifications`, `PlatformPhoneIntegration` | `isSupported() = false`, everything else no-op |
| `PlatformBilling` | `initialize` no-op; `queryProduct`/`purchaseUnlimited` return unavailable; `restorePurchases` → `NotFound` |
| `PlatformBackHandler` | registers with the history bridge (phase 3) |
| `signInWithGoogleNative`, `signInWithAppleNative`, `isAppleSignInSupported`, `launchGoogleAuthFlow`, `appleLogoBitmap` | phase 2 |

## Phase 2 — Real sign-in and data (~2 days)

Google:
- Implement `signInWithGoogleNative(serverClientId)` with Google Identity Services (`accounts.google.com/gsi/client`): `google.accounts.id.initialize({ client_id, callback })` + popup/button, resolve with the returned `credential` (ID token). This plugs into the existing `signInWithGoogleIdToken` → `signInWithIdp` path without touching `FirestoreBackend`.
- Google Cloud console: add the web origin(s) (localhost dev port, hosting domain) to the OAuth client used as `GOOGLE_OAUTH_CLIENT_ID`.
- `launchGoogleAuthFlow` is then unused on web; actual throws.

Apple:
- Create an Apple **Services ID** for the web domain, verify the domain, configure the return URL. Add the Services ID to Firebase Auth's Apple provider.
- Implement `signInWithAppleNative` with Sign in with Apple JS (`AppleID.auth.init({ clientId, scope: "name email", redirectURI, usePopup: true, nonce: sha256(rawNonce) })`, then `AppleID.auth.signIn()`), map to `AppleSignInCredential(idToken, rawNonce, fullName)`. `isAppleSignInSupported() = true`.

Backend config:
- Firebase Auth → authorized domains: add hosting domain(s).
- `functions/index.js`: add CORS (allowlist of web origins) to `deleteAccount` (`verifyPurchase` isn't called from web).
- Check Firebase Storage CORS for avatar uploads/downloads from the web origin; set bucket CORS via `gsutil cors set` if needed.
- Check `photon.komoot.io` (used by `data/Geocoder.kt`) answers browser CORS requests; if not, proxy through a small Cloud Function.
- Restrict the Firebase API key with HTTP referrers for the web domains (it's a separate key or an added restriction — don't break mobile).

Security note: the refresh token lives in `localStorage`. Acceptable for v1; keep the page free of third-party scripts except GIS, Apple JS and PostHog, and add a strict CSP in phase 4.

Done when: a real account signs in with Google and Apple on web, data loads, edits round-trip to Firestore and show up on the phone, avatar upload works, export downloads, delete account works.

## Phase 3 — Feel like a web app (~2–3 days)

History and URLs:
- A small `WebNavigationBridge` in `wasmJsMain`, wired into `Shell` through a `CompositionLocal` or `expect` hook (no-op on mobile):
  - on nav change, `history.pushState` with the top route (in pane mode the detail path, e.g. `/people/abc`), `replaceState` for tab switches so tabs don't flood history
  - on `popstate`, translate into the Shell's pop / navigate
  - on load, parse `location.pathname` and rebuild a sensible stack (primary + detail), so refresh and shared links land correctly after sign-in
- Page title per route ("Anna · Spheres"), prefixed with the overdue count when > 0: "(3) Spheres".

Keyboard (browsers reserve ⌘N/⌘L/⌘F/⌘1–4 and they can't be reliably overridden):
- On web, use single-key shortcuts when no text field is focused: `N` new connection, `L` log interaction, `/` search, `G` then `R`/`D`/`S`/`M` for tabs, `,` settings, `?` shortcut overlay. Keep Esc and ↑/↓.
- Implement as a web-specific mapping in `Shortcuts.kt` (select by a `platformKind` flag), reusing `runShortcut`.
- Search hint in the sidebar shows `/` instead of `⌘F` on web.

Rendering and input:
- Bundle fallback fonts (Noto Sans subset + Noto Color Emoji) and register them with `FontFamily.Resolver` preloading on web only. The canvas has no system font fallback, so emoji/non-Latin names otherwise render as tofu. Load lazily after first frame to keep startup light.
- Suppress the browser context menu on the canvas (the app has its own right-click menu).
- Check wheel scrolling, trackpad pinch on the Map, text input/IME (German umlauts, dead keys), copy/paste in fields.
- Hide UI for unsupported features: contact import/link buttons, call integration and notification settings, haptic toggles. Most already check `isSupported()`; audit `AddPersonSheet`, `PersonScreen`, `SettingsScreen`, `UnlockSheet`.
- `UnlockSheet` on web: explain the unlock lives in the mobile apps, show App Store / Play Store links, keep "Restore" hidden.

Done when: back/forward/refresh/deep links behave, shortcuts work without fighting the browser, emoji names render, no dead buttons.

## Phase 4 — Ship (~1 day)

- `firebase.json`: add `hosting` with `public` → `composeApp/build/dist/wasmJs/productionExecutable`, SPA rewrite `** → /index.html`, long-cache headers for hashed `.wasm`/`.js`, `Content-Type: application/wasm`, and a CSP allowing GIS, Apple JS, PostHog EU host, Firestore/Storage/Identity Toolkit, Cloud Functions, Photon, map tiles if any.
- Build: `./gradlew :composeApp:wasmJsBrowserDistribution`; deploy: `firebase deploy --only hosting`.
- PostHog: confirm events arrive from web with a `platform = web` property.
- Smoke test matrix: Chrome, Firefox, Safari 18.2+ on macOS; Chrome on Windows; window widths 900–2560.
- Unsupported-browser page (no WasmGC): a plain HTML message from `index.html` before the module loads.

## Later

- IndexedDB photo cache, offline shell via a service worker
- Web push reminders (FCM web push + scheduled Cloud Function)
- Desktop-only views from the large-screen exploration: command palette, People table with bulk edit, weekly review dashboard
- `js()` fallback target only if analytics show meaningful traffic from browsers without WasmGC

## Risks to check in the spike

- Skiko web JPEG encoding (avatar crop)
- Compose web text input quality for German keyboard layouts
- Startup size: measure the production `.wasm` (expect several MB); split fonts out of the main bundle if needed
- GIS popup behavior in Safari with third-party cookie restrictions (use the button/popup flow, not One Tap, if One Tap is flaky)
