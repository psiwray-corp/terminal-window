# 12 — Window lifecycle and GL context

**Phase:** runtime · **Depends on:** 08, 10, 11 · **Blocks:** 13, 15

## Goal

Create exactly one GLFW window + OpenGL 3.3-core context per `TerminalWindow`, expose capability info, and define a close protocol that respects the main-thread rule. No rendering yet.

## Deliverables

- `.../TerminalWindow.kt` (window, context, matrix, lifecycle)
- `.../TerminalWindowConfig.kt` (`data class` with defaults + `init` validation)
- `.../ContextInfo.kt` (`data class`)
- `.../internal/WindowHandle.kt` (owns the raw handle, close state)
- `library/src/test/kotlin/.../WindowLifecycleTest.kt` (tagged `gl`)
- `docs/threading.md` updated with the close/transfer state diagram.

## Steps

1. **Config.** Kotlin named arguments, no builder:
   ```kotlin
   data class TerminalWindowConfig(
       val font: BitmapFont,
       val title: String = "terminal-window",
       val columns: Int = 80,
       val rows: Int = 24,
       val resizable: Boolean = true,
       val visible: Boolean = true,
       val vsync: Boolean = true,
       val scale: Int = 1,
       val defaultForeground: Int = 0xFFFFFFFF.toInt(),
       val defaultBackground: Int = 0xFF000000.toInt(),
       val ownership: GlfwOwnership = GlfwOwnership.OWNED,
   ) { init { require(columns >= 1); require(rows >= 1); require(scale >= 1) } }
   ```
   Java callers use the full constructor; this is a Kotlin-first API.
2. **Hints.** `GLFW_CONTEXT_VERSION_MAJOR=3`, `MINOR=3`, `OPENGL_PROFILE=CORE`, `OPENGL_FORWARD_COMPAT=true`, `RESIZABLE`, `VISIBLE`, `SCALE_FRAMEBUFFER` (GLFW 3.4). macOS: 3.3 core is unavailable; ladder 3.3 → 4.1 core, then fail. Clear hints after creation.
3. **Create.** On the service thread: `glfwCreateWindow(screenW, screenH, title, NULL, NULL)` where `screenW = columns*font.cellWidth*scale`, `screenH = rows*font.cellHeight*scale` (screen coordinates, not pixels). `glfwMakeContextCurrent`, `glfwSwapInterval(if (vsync) 1 else 0)`, `GL.createCapabilities()`, query `GL_MAX_TEXTURE_SIZE`, read `GL_VERSION`/`GL_RENDERER`/`GL_VENDOR`/`GL_SHADING_LANGUAGE_VERSION` into `ContextInfo`.
4. **Validate.** Parse the version string; require `>= 3.3`. On failure, destroy the window and throw `GlException` listing requested vs actual version, renderer, and the platform hint (macOS needs 4.1).
5. **Matrix.** Read `glfwGetFramebufferSize` and `glfwGetWindowContentScale` on the service thread; derive `cellPxW = max(1, round(cellW*scale*contentScaleX))`, `cellPxH` likewise, `columns = fbW/cellPxW`, `rows = fbH/cellPxH`; create `TerminalMatrix(columns, rows, ...)`. Cache framebuffer size and content scale in `@Volatile` fields. Section 17 owns the same formula for runtime changes.
6. **Ownership transfer.** `releaseContext()` (owner thread: `glfwMakeContextCurrent(NULL)`) and `acquireContext()` (new thread: make current). Track the owning thread in a `@Volatile var owner: Thread?`; both methods `check(...)` state.
7. **Window state ops and repaint controls.** `setTitle`, `requestClose`, `setCloseRequested(Boolean)`, `setVisible`, `setResizable`, `forceRepaint`, and `requestRepaint()` enqueue to the service/owner path as appropriate. `shouldClose` reads a cached `@Volatile` flag; never call `glfwWindowShouldClose` off-thread. `forceRepaint` is a continuous control; `requestRepaint()` is a one-shot request using the current bounds and coalesces until the next rendered frame.
8. **Close protocol.** Owner thread: release context, enqueue destroy, mark closed (`AtomicBoolean`), make later calls throw `IllegalStateException`. Service thread on next pump: `glfwDestroyWindow`, clear handle, release service refcount. `close()` is idempotent; `awaitClosed()` blocks only when called off the service thread.
9. **Tests.** Hidden window on Xvfb: context ≥3.3; `ContextInfo` non-empty; matrix dims match formula; acquire/release from a worker thread; close from owner thread then pump; double close; use-after-close. Repaint controls are queued without calling GL directly; section 14 tests their frame effects.

## Acceptance

- [x] A hidden 640×384 window (80×24 cells at 8×16) reports `GL_VERSION >= 3.3` and `maxTextureSize >= ContextInfo.MIN_MAX_TEXTURE_SIZE` (the OpenGL 3.3 core minimum).
- [x] Creation from a non-service thread throws with a message quoting the GLFW rule.
- [x] `close()` from a worker thread does not call `glfwDestroyWindow` until the service thread pumps.
- [x] No `glfw*` call is made after close except `glfwGetError`.
- [x] `ContextInfo` is immutable and contains no GLFW handle.
- [x] Matrix dimensions equal `max(1, fbW/cellPxW)` × `max(1, fbH/cellPxH)` with `cellPx = round(cell*scale*contentScale)`, including content scale 2.

## Pitfalls

- macOS: request 4.1 core; 3.3 core is not offered by Cocoa. Keep the ladder explicit, not a retry loop of random versions.
- `GL.createCapabilities()` resets function pointers; call it once per context, after making it current.
- On HiDPI, window size ≠ framebuffer size; derive cells from the framebuffer.
- `glfwSetWindowSize` uses screen coordinates, not pixels; never use it for cell math.
- Destroying a window whose context is current on another thread is illegal; require `releaseContext` first.
- `data class` with a `BitmapFont` field generates `equals`/`hashCode`; that is fine (identity), but do not rely on config equality.

## Plan seed

> Plan section 12 only: implement `TerminalWindow` creation in Kotlin with a 3.3-core (→4.1-core on macOS) context, capability capture, matrix sizing, ownership transfer, queued state ops including `forceRepaint`/`requestRepaint()`, and a safe two-phase close. Tests use a hidden window. No renderer, no input, no frame loop.
