# Public releases, help, and tooltips design

## Purpose

Prepare TZF Viewer for a first public release while keeping rapid nightly delivery for the developer. Improve discoverability of the viewer tools without changing their short-tap behavior.

## Update channels and release promotion

- Every push to `main` creates a signed nightly APK. The workflow uses Gradle/Android caches and a concurrency group so a newer push cancels an obsolete pending/running nightly build.
- Nightly publication is fast: build and publish the signed APK and manifest to the existing prerelease `nightly`; validation remains a separate workflow/job and reports its result in GitHub Actions.
- Stable users request a stable manifest and APK. A device that has entered the developer channel requests the nightly manifest and APK instead.
- Long-pressing the version row in Settings opens a PIN dialog. PIN `1984` enables the nightly channel on that device; the selected channel is persisted. The entry point and PIN are not shown in the normal settings UI.
- A manual `Promote nightly to public release` GitHub Actions workflow promotes the current nightly assets without rebuilding: it downloads the latest nightly manifest/APK, verifies SHA-256, creates a public version tag/release, uploads the same APK and a stable manifest, and adds release notes supplied at dispatch.
- The phrase `ДЕЛАЙ ПУБЛИЧНЫЙ РЕЛИЗ` means dispatching that promotion workflow for the current nightly after confirming its desired version and release notes if they were not provided.

## Left tool rail help

- Every actionable button in the left rail receives a long-press listener and a tooltip text based on its accessible name plus a concise functional description.
- Long press provides haptic feedback and displays an anchored compact tooltip/card near the button. It closes on release, outside touch, or another tooltip opening.
- Short-tap actions, selection state, and the expanded-tablet rail remain unchanged.
- The tooltip is accessible: each tool keeps a content description and the displayed text is announced by accessibility services.

## Settings help and contact

- Add a `?` button to both phone and tablet Settings headers.
- It opens a dedicated Help activity with the existing dark instrument visual language: intro panel and grouped cards for viewing/navigation, scan import and X7, scan registration, measurement, clipping, project save/export, and updates.
- Each card states what the feature does and its primary interaction, in Russian.
- Add a `Связь с разработчиком` card in Settings with a clearly labelled `@macyok` button. It opens `https://t.me/macyok` through an `ACTION_VIEW` intent, with a browser fallback.

## Compact overflow menu

- Reduce overflow popup width from 330dp to 280dp on phone layouts.
- Tighten card and row horizontal padding while retaining a 48dp minimum interactive row height.
- Add a visual separation between the `Проекция` and `Сетка` actions; their state pills remain aligned and readable.
- Existing overflow actions and state updates stay intact.

## Error handling and verification

- Invalid PIN leaves the channel unchanged and shows a neutral retry message; no PIN is stored.
- Missing Telegram handler falls back to the browser; an unavailable link reports a short status message.
- Promotion fails before creating/updating stable metadata if the nightly manifest, APK, or digest verification fails.
- Tests cover update-channel selection/persistence, stable vs nightly manifest URLs, PIN gate behavior, release-manifest parsing, and tooltip registration helpers. Existing unit and native tests remain in CI.

