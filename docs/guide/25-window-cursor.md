# 25 — Window cursor and icon customization

**Phase:** api · **Depends on:** 12, 18 · **Blocks:** 21

## Goal

Let applications choose a standard cursor shape or a small custom cursor image
without exposing GLFW cursors or letting callers make main-thread-only GLFW
calls. Cursor lifetime is owned by the service thread and is released with the
window.

## Deliverables

- `.../CursorShape.kt` — immutable enum of standard shapes.
- `.../CursorImage.kt` — immutable RGBA cursor image with validated hotspot.
- `.../WindowIcon.kt` — immutable RGBA window icon with validated dimensions.
- `.../internal/CursorHandle.kt` — native cursor creation, caching, and destroy.
- `.../internal/ImageLoader.kt` — stb_image decoding and ARGB conversion.
- `TerminalWindow` updates: cursor shape/image/visibility controls, a
  `CursorMode` if raw/disabled cursor mode is supported, and icon replacement.
- `library/src/integrationTest/kotlin/.../WindowCursorTest.kt` (`@Tag("gl")`).
- `docs/cursor.md` — coordinate, pixel-format, hotspot, and lifetime rules.
- `docs/window-icon.md` — icon pixel format, multiple sizes, and lifetime rules.

## API

```kotlin
enum class CursorShape { ARROW, IBEAM, CROSSHAIR, HAND, HRESIZE, VRESIZE, RESIZE_ALL, NOT_ALLOWED }
enum class CursorMode { NORMAL, HIDDEN, DISABLED }

class CursorImage private constructor(
    val width: Int,
    val height: Int,
    val hotspotX: Int,
    val hotspotY: Int,
    private val argb: IntArray, // 0xAARRGGBB, row-major, top-left origin
) {
    fun copyPixels(): IntArray = argb.copyOf()

    companion object {
        fun of(width: Int, height: Int, hotspotX: Int, hotspotY: Int, pixels: IntArray): CursorImage
    }
}

fun setCursor(shape: CursorShape)
fun setCursor(image: CursorImage)
fun setCursorVisible(visible: Boolean)
fun setCursorMode(mode: CursorMode)
fun setIcon(vararg icons: WindowIcon)
fun clearIcon()
```

`WindowIcon.of` and `CursorImage.of` take `0xAARRGGBB` pixels copied from the
caller. `WindowIcon.fromPath` and `CursorImage.fromPath` decode image files
through LWJGL's stb_image; the public API still exposes no LWJGL types.

`setCursorVisible` records whether the cursor should be visible. `CursorMode.DISABLED`
hides and grabs the cursor and reports virtual, unbounded movement; it overrides
recorded visibility until the mode returns to `NORMAL` or `HIDDEN`. The exact
enum surface is finalized in section 18; no GLFW type appears in a public
signature.

## Steps

1. **Shapes.** Map each `CursorShape` to a GLFW standard cursor. Create standard
   cursors on the service thread and cache process-wide handles; do not create a
   native cursor per frame or per window. Unsupported shapes fall back to
   `ARROW` and are documented.
2. **Custom images.** Validate `width > 0`, `height > 0`, `pixels.size == width *
   height`, and `0 <= hotspotX < width`, `0 <= hotspotY < height`. Convert ARGB
   to GLFW's non-premultiplied RGBA8, top-left-origin layout once, at creation.
   Do not mutate the caller's `IntArray`; copy it into the immutable value. Do
   not use a `data class`: array identity would make `equals`/`hashCode` wrong.
3. **Service-thread ownership.** `setCursor` and visibility operations enqueue a
   state op. The service thread creates or selects the `GLFWcursor`, calls
   `glfwSetCursor`, and never runs user code. The owner thread may call the
   public setters from any thread; they do not call GLFW directly.
4. **Lifetime.** One custom cursor is live per window. Replacing it queues
   destruction of the previous handle after the new one is installed. Closing
   the window destroys its custom cursor and releases any service-owned standard
   cursor cache only when the last window/service reference is gone.
5. **Failure.** `glfwCreateCursor` returning `NULL` maps to `GlfwException`
   (with the copied GLFW error) or `GlException` when no error was reported.
   Never leave a partially installed cursor or a leaked native handle.
6. **Tests.** Hidden window: default arrow, each standard shape, custom 2×2
   cursor with an in-bounds hotspot, custom image copy/validation, visibility
   toggle, replace-cursor destruction, icon replacement/clearing,
   use-after-close, and no GLFW calls from the owner thread. Pixel-format
   conversion and stb_image PNG decoding are pure unit tests.
7. **Window icons.** Add immutable `WindowIcon` values with the same ARGB
   layout. `setIcon` accepts one or more sizes and queues `glfwSetWindowIcon`;
   `clearIcon` restores the platform default. Convert to non-premultiplied
   RGBA only at the service-thread native boundary. The icon pixel data is
   copied by GLFW, so no native handle is retained. Invalid pixel counts are
   rejected before GLFW is called.
8. **Image files.** Use LWJGL's `lwjgl-stb` runtime dependency for
   `CursorImage.fromPath` and `WindowIcon.fromPath`. Decode on the calling
   thread into copied ARGB pixels; report unreadable or undecodable files as
   `IOException`. Do not expose stb types or retain native pixel buffers.
9. **Docs.** Document that cursor coordinates are screen coordinates, custom
   images are top-left RGBA, alpha is not premultiplied, GLFW applies the
   hotspot in screen coordinates, and icon size selection is platform-defined.

## Acceptance

- [x] No public signature references `org.lwjgl.*` or exposes a cursor handle.
- [x] Standard shapes and custom images are installed only on the service thread.
- [x] A custom cursor with an out-of-bounds hotspot or wrong pixel count is
      rejected before GLFW is called.
- [x] Replacing a custom cursor destroys the previous native cursor exactly once.
- [x] Closing a window destroys its custom cursor; double close is a no-op.
- [x] ARGB input is converted to the documented RGBA byte order exactly once.
- [x] Use after close throws `IllegalStateException`.
- [x] Cursor visibility and cursor mode are independent and documented.
- [x] Window icons accept one or more validated sizes, replace the current icon,
      and `clearIcon` restores the platform default.
- [x] Icon and cursor image files decode through stb_image; failures are
      `IOException` and no native pixel buffer escapes the call.

## Pitfalls

- `glfwSetCursor` is main-thread-only; owner and application threads must queue.
- GLFW cursor images are RGBA8, top-left origin, non-premultiplied; ARGB input is
  not the same memory layout.
- A hotspot is in cursor-image pixels, not window pixels; validate both axes.
- Do not create a standard cursor on every `setCursor` call; cache handles.
- `GLFW_CURSOR_DISABLED` is not the same as hiding the cursor; it changes raw
  motion behavior and may require a restore path on focus loss.
- Cursor images are unmanaged native resources until explicitly destroyed.
- Do not make cursor support a reason to expose GLFW types in the public API.

## Plan seed

> Plan section 25 only: add standard/custom cursor customization, cursor
> visibility/mode controls, window icon replacement/clearing, service-thread
> cursor lifetime and caching, stb_image-backed image-file decoding, pure
> ARGB-to-RGBA validation/conversion tests, a hidden-window GL test, and
> cursor/icon docs. No rendering or input-event changes beyond these controls.