# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.0.0] - 2026-11-16

### Added

- Standard and custom cursor customization through `CursorShape`,
  `CursorImage`, `CursorMode`, and the cursor controls on `TerminalWindow`.
- Window icon replacement and clearing through immutable `WindowIcon` values
  and `TerminalWindow.setIcon` / `clearIcon`.
- `CursorImage.fromPath` and `WindowIcon.fromPath` image decoding with
  stb_image, with copied ARGB pixels and no public LWJGL types.
- A manual `:benchmarks` offscreen frame benchmark with 80x24, 240x67, and one-cell-update scenarios, measuring ns/frame, upload bytes, draw calls, and per-frame allocation; `docs/performance.md` records the budget and measured numbers.
- Four runnable examples: an interactive `HelloTerminal` editor, a two-window `TwoThreads` context-transfer demo, a `CustomFont` sampler, and a randomized `RandomStress` renderer benchmark, with Gradle run tasks, host natives, and committed screenshots.
- Frozen Kotlin public API with explicit visibility, KDoc, package documentation, a reflection-based API surface audit, and the automatic JPMS module name `com.psiwray.organization.terminal_window`.
- Normative `.twf` v1 bitmap-font specification with valid and invalid fixtures.
- Strict UTF-8 `.twf` parser (`BitmapFont`, `FontFormatException`) with `TW-001` through `TW-011` diagnostics.
- Deterministic gutter-free R8 atlas baking (`AtlasLayout`, `Atlas`, `AtlasBaker`).
- Mutable row-major `TerminalMatrix` with ARGB cell colors, dirty tracking, code-point writes, and resize policies.
- `Colors` ARGB construction helpers.
- Process-global `TerminalWindowService` with `OWNED`/`EXTERNAL` GLFW ownership, reference-counted lifetime, service-thread checks, checked `GlfwException` mapping, queued state/destroy work, and a `runEventLoop()` that returns after the last window closes.
- `TerminalWindow`, `TerminalWindowConfig`, and immutable `ContextInfo` with 3.3-core (4.1-core on macOS) creation, capability capture, matrix geometry, explicit context transfer, close/await lifecycle, cached `shouldClose`, and queued state/repaint controls. `WindowRegistry` is now the source of truth for open windows.
- Framebuffer-driven resize and content-scale handling, with `ResizePolicy` preservation or clear behavior and one matrix upload per resize.
- `GlException` for GL environment failures that are not raw GLFW errors.
- Typed, immutable input events, `InputListener` dispatch on the owner thread, a bounded coalescing queue, full GLFW key/button/modifier mappings, and direct key/button/cursor snapshots in `TerminalWindow`.
- `docs/input.md` and the input threading contract.
- Demand-driven `FrameScheduler` with input-first ordering, one upload/draw/swap per
  rendered tick, skipped clean ticks, one-shot/forced repaint controls, and immutable
  `RenderStats` counters.
- GLSL 330 terminal shaders, a compile/link/uniform wrapper with diagnostics,
  an R8 glyph-atlas texture, and the 16-byte instanced attribute layout.
- Gradle 9.9.1 wrapper with a pinned distribution checksum.
- Kotlin 2.4.21 build for a JDK 25 toolchain with `:library` and `:examples`.
- Version catalog for Kotlin, LWJGL, JOML, JUnit, Dokka, detekt, and ktlint.
- A fresh-visitor README with installation, a checked quickstart, threading
  and font summaries, and the measured performance table; `CONTRIBUTING.md`
  documents the local and CI gates. The `docs/` pages are cross-linked.

### Changed

- Rewrote the library in Kotlin for JVM 25; Kotlin is the API and Java interop
  is best-effort, not a Java-first source-compatibility promise.
- Relicensed version 2 from Apache-2.0 to MIT before any 2.x release.
- Added the `lwjgl-stb` runtime dependency and host-native artifact for
  image-file decoding in cursor and window-icon construction.

### Removed

- The v1 tileset and texture-window API surface is not carried into 2.0. The
  replacement is the `TerminalMatrix` plus one instanced glyph-atlas renderer;
  there is no compatibility bridge for the old window types.

### Fixed

- Standard cursor creation falls back to the default arrow when a non-arrow
  shape is unavailable, while preserving an already installed cursor.
- Replacing a custom cursor destroys the previous native handle only after the
  new cursor is installed, so a failed replacement leaves the old cursor intact.
