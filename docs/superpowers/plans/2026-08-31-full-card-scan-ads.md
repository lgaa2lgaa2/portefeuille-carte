# Full Card Scan & Advertising Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver full-card visual scanning plus client banner/scrolling-text advertising with administration separated from the client application.

**Architecture:** Keep card capture and barcode decoding in the Android application, preserving complete card visuals in encrypted local storage. Split advertising into a read-only client component and a centralized authenticated administration API; no administrator secret or admin navigation is exposed in the normal client application.

**Tech Stack:** Kotlin, Android SDK, ML Kit code scanner/barcode support, Android encrypted/Keystore-backed existing SecurityStore, HTTP/JSON API for advertising.

**Spec:** `docs/superpowers/specs/2026-08-31-full-card-scan-ads-design.md`

## Global Constraints
- App brand remains PAULO PORTE CARTE.
- Existing password/biometric security and encrypted card storage remain enabled.
- Existing saved cards remain readable.
- Admin credentials must never be hard-coded into the APK.
- Advertising links are limited to HTTP/HTTPS.

---

### Task 1: Full-card scan model and tests

**Files:**
- Modify: `app/src/main/java/com/paulo/carte/WalletUiLogic.kt`
- Modify/Create tests under: `app/src/test/java/com/paulo/carte/`

- [ ] Write failing tests for complete-card scan state, merchant/category normalization and valid barcode/QR association.
- [ ] Run unit tests and confirm failure.
- [ ] Implement minimal scan-result model/helpers.
- [ ] Run tests and confirm pass.
- [ ] Commit.

### Task 2: Card-shaped camera capture and preview

**Files:**
- Modify: `app/src/main/java/com/paulo/carte/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml` only if camera/provider declarations are required.

- [ ] Add a failing testable state transition for capture → decode → preview → save.
- [ ] Implement full-card capture flow with visible card framing guidance.
- [ ] Preserve complete captured card image and decode barcode/QR.
- [ ] Add preview with merchant-name correction before save.
- [ ] Verify old-card compatibility and encrypted persistence.
- [ ] Commit.

### Task 3: Client advertising presentation

**Files:**
- Modify: `app/src/main/java/com/paulo/carte/MainActivity.kt`
- Create focused advertising model/client files under `app/src/main/java/com/paulo/carte/` as needed.
- Add tests under `app/src/test/java/com/paulo/carte/`.

- [ ] Write failing tests for enabled/disabled campaign display and URL validation.
- [ ] Add read-only advertising model: banner, scrolling text, link, enabled, start/end dates.
- [ ] Render a lightweight banner and horizontally scrolling text without obstructing cards/scanner.
- [ ] Ensure client navigation contains no Admin entry.
- [ ] Run tests and commit.

### Task 4: Central advertising API boundary

**Files:**
- Create Android API client/config files under `app/src/main/java/com/paulo/carte/`.
- Server-side files depend on the selected deployment target and must be isolated from the APK.

- [ ] Define public endpoint contract returning only currently active advertising.
- [ ] Define authenticated admin contract for create/update/enable/disable campaigns.
- [ ] Add client parsing/fallback tests.
- [ ] Implement Android read-only fetch with safe no-ad fallback when offline.
- [ ] Ensure no admin credentials are shipped in the APK.
- [ ] Commit.

### Task 5: Verification and release

**Files:**
- Modify: `app/build.gradle.kts` for release version bump.
- Modify workflow only if required for current GitHub Actions compatibility.

- [ ] Run all unit tests.
- [ ] Run debug APK assembly.
- [ ] Verify scan complete-card preview and saved-card rendering.
- [ ] Verify barcode/QR usability.
- [ ] Verify banner/text behavior and that Admin is absent from client UI.
- [ ] Open PR, review changes, merge only after green CI.
- [ ] Download and provide the verified APK artifact.
