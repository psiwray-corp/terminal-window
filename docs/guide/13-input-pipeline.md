# 13 — Input pipeline

**Phase:** runtime · **Depends on:** 12 · **Blocks:** 14

## Goal

Deliver GLFW input as typed, immutable Kotlin events to listener code on the window owner thread, with bounded buffering and no GLFW leakage. Also expose direct key, mouse-button, and cursor-state queries backed by service-thread snapshots, so callers do not have to reconstruct current device state from events.

## Deliverables

- `.../input/InputListener.kt` (interface with default no-op methods)
- `.../input/InputEvent.kt` (`sealed interface` + `data class` events; `data object CloseRequestedEvent`)
- `.../input/{Key,MouseButton,KeyAction}.kt` (enums), `Modifiers.kt` (`@JvmInline value class`)
- `.../internal/InputQueue.kt` (bounded queue + coalescing slots)
- `.../internal/GlfwCallbacks.kt`
- `.../input/CursorPosition.kt` (screen and cell coordinates)
- `TerminalWindow` updates: direct device-state queries
- `library/src/test/kotlin/.../input/*Test.kt`
- `docs/input.md`: event ordering, coalescing, coordinate units, direct-state semantics.

## Steps

1. **Registration.** On window creation (service thread) install callbacks: key, char, cursor-pos, cursor-enter, mouse-button, scroll, framebuffer-size, content-scale, focus, close. Use `glfwSetWindowUserPointer` to map to the window; callbacks only copy primitives into slots/queues (no user code, no thrown exceptions).
2. **Event model.**
   ```kotlin
   sealed interface InputEvent
   data class KeyEvent(val key: Key, val scancode: Int, val action: KeyAction,
                       val modifiers: Modifiers, val repeat: Boolean) : InputEvent
   data class CharEvent(val codePoint: Int) : InputEvent
   data class MouseMoveEvent(val pixelX: Int, val pixelY: Int,
                             val column: Int, val row: Int) : InputEvent
   data class CursorEnterEvent(val entered: Boolean) : InputEvent
   data class MouseButtonEvent(val button: MouseButton, val action: KeyAction,
                               val modifiers: Modifiers, val column: Int, val row: Int) : InputEvent
   data class ScrollEvent(val xOffset: Double, val yOffset: Double) : InputEvent
   data class ResizeEvent(val framebufferWidth: Int, val framebufferHeight: Int) : InputEvent
   data class ContentScaleEvent(val x: Float, val y: Float) : InputEvent
   data class FocusEvent(val focused: Boolean) : InputEvent
   data object CloseRequestedEvent : InputEvent
   ```
   `CharEvent` has no modifiers because GLFW's char callback has none; document it.
3. **Listener.**
   ```kotlin
   interface InputListener {
       fun onKey(event: KeyEvent) {}
       fun onChar(event: CharEvent) {}
       fun onCursorEnter(event: CursorEnterEvent) {}
       fun onMouseMove(event: MouseMoveEvent) {}
       fun onMouseButton(event: MouseButtonEvent) {}
       fun onScroll(event: ScrollEvent) {}
       fun onResize(event: ResizeEvent) {}
       fun onContentScale(event: ContentScaleEvent) {}
       fun onFocus(event: FocusEvent) {}
       fun onCloseRequested(event: CloseRequestedEvent) {}
   }
   ```
4. **Queue.** `ArrayBlockingQueue<InputEvent>(1024)` for ordinary events; `AtomicReference<MouseMoveEvent?>` for the latest move; `AtomicReference` slots for cursor enter/leave, resize, content-scale, focus, close (never dropped). Ordinary overflow drops and increments `droppedEvents`.
5. **Dispatch.** `internal fun drainEvents()` (called by the frame pipeline in 14) drains on the owner thread, calls listeners in registration order. A listener added during dispatch receives subsequent events, not the current one. Removal during dispatch is safe (copy-on-write list or index snapshot).
6. **Key mapping.** Hand-written `Key` enum with the GLFW code; covers letters, digits, F1–F25, arrows, navigation, modifiers, space/enter/tab/backspace/escape. `Key.UNKNOWN` for anything else. Mapping test asserts uniqueness and full enumeration.
7. **Modifiers.** `@JvmInline value class Modifiers(val bits: Int)` with companion constants `SHIFT`, `CONTROL`, `ALT`, `SUPER`, `CAPS_LOCK`, `NUM_LOCK`, `NONE`.
8. **Direct device state.** After each service-thread poll, copy `glfwGetKey`, `glfwGetMouseButton`, and `glfwGetCursorPos` into immutable per-window snapshots. Expose `TerminalWindow.isKeyPressed(key: Key)`, `TerminalWindow.isMouseButtonPressed(button: MouseButton)`, and `TerminalWindow.cursorPosition(): CursorPosition`; they read the snapshot and never call GLFW from an owner or application thread. GLFW's `glfwGetKey` and `glfwGetMouseButton` are themselves cached event state, not OS device polls; `glfwGetCursorPos` is a platform query in normal cursor mode and virtual state in disabled mode. Cursor coordinates are screen coordinates; `CursorPosition` also carries the current cell-derived column and row when the matrix is available. None of these queries provides event history: sticky keys are not enabled, and a press followed by a release before the next poll is observed as released.
9. **Close semantics.** The close callback sets a flag and offers `CloseRequestedEvent`; listeners may veto via `window.setCloseRequested(false)` (queued to the service thread). Default: close.
10. **Tests.** Direct queue injection (no display): ordering, coalescing, overflow counter, latest-state slots, listener mutation during dispatch, key mapping completeness, negative cursor coordinates (outside the window), `data class` equality of events. Direct-state tests use a fake backend: key/button press and release snapshots, cursor screen coordinates, and no GLFW calls from the query thread.

## Acceptance

- [x] No public signature references `org.lwjgl.*`.
- [x] Every `Key` constant maps from exactly one `GLFW_KEY_*`; the mapping test enumerates all of them.
- [x] `drainEvents()` delivers events in enqueue order, except coalesced moves (latest wins).
- [x] A full queue drops a non-critical event and increments the counter; a close event is still delivered.
- [x] Character input with `"😀"` arrives as one `CharEvent` with code point `0x1F600`.
- [x] Listener code runs only on the owner thread (assert in tests).
- [x] `droppedEvents` is exposed read-only and is monotonic.
- [x] `isKeyPressed`, `isMouseButtonPressed`, and `cursorPosition()` read a service-thread snapshot; no query calls GLFW from an owner or application thread.
- [x] A press and release between two polls is reported as released by the direct-state query, matching GLFW's non-sticky behavior.

## Pitfalls

- GLFW cursor position is in screen coordinates; on HiDPI, multiply by content scale for pixels.
- `glfwSetCharCallback` fires outside key events; do not synthesize modifiers.
- Callbacks must not call `glfwSetWindowShouldClose` directly; go through the queue for the veto protocol.
- `ArrayBlockingQueue.offer` never blocks; never use `put` in a callback.
- Do not use `data object` for events with no state if Java interop needs a class identity; here it is fine.
- `glfwGetKey`, `glfwGetMouseButton`, and `glfwGetCursorPos` are main-thread-only. Owner and application threads must read the published snapshot; never forward their calls to GLFW.
- Direct-state queries describe the latest poll, not every transition. Use the event stream when edge history matters.

## Plan seed

> Plan section 13 only: implement typed Kotlin input events, GLFW callback registration, a bounded coalescing queue, listener dispatch on the owner thread, key/button/modifier mappings, the close-veto protocol, and service-thread-backed direct key/button/cursor queries, with no-display tests. No rendering changes.