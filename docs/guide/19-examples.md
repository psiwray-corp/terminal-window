# 19 — Examples

**Phase:** api · **Depends on:** 17, 18 · **Blocks:** 21

## Goal

Three runnable Kotlin demos that prove the library end to end, including the per-thread claim, and produce the screenshots used by the README.

## Deliverables

- `examples/build.gradle.kts`: `application` plugin, `JavaExec` tasks, runtime natives, dependencies.
- `examples/src/main/kotlin/.../HelloTerminal.kt`
- `examples/src/main/kotlin/.../TwoThreads.kt`
- `examples/src/main/kotlin/.../CustomFont.kt`
- `examples/src/main/kotlin/.../RandomStress.kt`
- `examples/fonts/*.twf` (or reuse test fixtures)
- `docs/assets/hello.png`, `docs/assets/two-threads.png` (committed, small)

## Steps

1. **HelloTerminal.** 80×24 window, `fun main()`. Writes a colored banner; tracks a cursor; handles `CharEvent` (insert), Backspace, Enter, arrows; `Ctrl+Q` closes; `F1` cycles a palette; draws the cursor by swapping fg/bg of one cell; title shows `cols×rows` and FPS. Demonstrates input → matrix → single upload. Use `TerminalWindow.use { }`.
2. **TwoThreads.** Main thread initializes the service, creates two windows (service thread), then hands each window to its own worker (`releaseContext`/`acquireContext`); each worker animates a counter at its own pace:
   ```kotlin
   val render = thread(name = "render-1") { window.use { w -> while (!w.shouldClose) w.frame() } }
   ```
   Main thread runs `service.pump()` until both close. Proves independent contexts and the ownership protocol.
3. **CustomFont.** Loads a `.twf` path from args (default 8×16), renders a glyph sampler grid with code points and colors, demonstrates `BitmapFont.parse` and the fallback glyph.
4. **Gradle.** Tasks `hello`, `twoThreads`, `customFont`; JVM args for natives as in 03. Choose classpath execution (simpler than module path) and document it.
5. **Natives.** Examples add the host native classifier from 03; never publish.
6. **JOML.** Use JOML `Vector2i`/`Vector2f` in the demos where a 2-D pair is natural (mouse cell, cursor), proving the dependency is not dead weight.
7. **Capture.** Run `hello` on Linux under X11/Wayland, save a PNG screenshot, commit it (crop to the window). Do not commit GIFs larger than ~2 MB.
8. **CI.** Examples compile in CI (`:examples:build`) but do not run (no display in the matrix job).
9. **RandomStress.** 80x24 window; every rendered frame writes a random
   defined code point and random opaque foreground/background color into every
   cell. Keep exactly one upload and one draw per frame. Log FPS, matrix-update
   time, upload throughput, draw count, and dropped events once per second.
   `Ctrl+Q`/`Cmd+Q` close; support the same auto-close and screenshot options.

## Acceptance

- [x] `./gradlew :examples:hello` opens a window, typing works, resize adapts the matrix, close is clean.
- [x] `./gradlew :examples:twoThreads` shows two windows updating independently; no GLFW thread errors in stderr.
- [x] `./gradlew :examples:customFont --args="examples/fonts/ascii-8x16.twf"` renders the sampler.
- [x] `./gradlew :examples:randomStress` renders an 80x24 random-cell frame every tick and logs performance metrics.
- [x] All four exit with code 0 and no `glfwTerminate` warnings.
- [x] Screenshots exist and match the current UI.
- [x] `Ctrl+Q` is documented for Linux/Windows and `Cmd+Q` for macOS; both are handled.

## Pitfalls

- `TwoThreads`: the main thread must keep pumping; if it blocks on `join()` without pumping, input freezes. Show the pump loop.
- Kotlin `thread {}` returns a `Thread`; join after the pump loop exits, and rethrow worker exceptions.
- Do not create windows from worker threads; creation is service-thread-only.
- `use {}` closes the window on the worker thread; the destroy is queued and completed by the pump. Do not stop pumping until both queues are drained.
- Screenshot tools capture the desktop, not the GL backbuffer; use `glReadPixels` in a debug helper or the OS tool with the window focused.

## Plan seed

> Plan section 19 only: add the Kotlin `:examples` demos (HelloTerminal, TwoThreads, CustomFont), Gradle run tasks, host natives, JOML usage where natural, and screenshots. No library changes except bug fixes proved by the demos.