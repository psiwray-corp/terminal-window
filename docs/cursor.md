# Cursor customization

`TerminalWindow` can select a standard cursor shape, install a custom cursor
image, and control cursor visibility and input mode. No public API exposes a
GLFW cursor handle or any `org.lwjgl.*` type.

## Threading and lifetime

All cursor controls are safe from any application thread:

```kotlin
window.setCursor(CursorShape.IBEAM)
window.setCursorVisible(false)
window.setCursorMode(CursorMode.HIDDEN)
```

The call only enqueues work. The GLFW cursor is selected, created, or destroyed
on the service thread during the next `TerminalWindowService.pump()` or
`TerminalWindow.frame()` opportunity. Use-after-close throws
`IllegalStateException`.

There is at most one custom cursor per window. Setting a custom image replaces
the previous custom cursor and destroys the previous native handle after the
new one is installed. `TerminalWindow.close()` destroys the window's custom
cursor. Standard shapes are cached process-wide on the service thread and are
released when the last service reference is closed.

## Standard shapes

| Shape | Expected cursor |
|---|---|
| `ARROW` | Default arrow. |
| `IBEAM` | Text insertion. |
| `CROSSHAIR` | Crosshair. |
| `HAND` | Pointing hand. |
| `HRESIZE` | Horizontal resize. |
| `VRESIZE` | Vertical resize. |
| `RESIZE_ALL` | Two-axis resize. |
| `NOT_ALLOWED` | Not allowed. |

A shape the platform cannot create falls back to `ARROW`. An unsupported shape
does not fail the window.

## Custom images

`CursorImage` uses the same public pixel layout as the rest of the library:

- `0xAARRGGBB`, row-major.
- Top-left origin.
- Alpha is not premultiplied.
- The hotspot is in cursor-image pixels, not window pixels; the operating
  system applies it in screen coordinates.

`CursorImage.of` copies and validates the pixel array. `CursorImage.fromPath`
decodes PNG, JPEG, BMP, TGA, GIF, and other formats supported by LWJGL's
stb_image. Both require a hotspot inside the image bounds.

```kotlin
val cursor = CursorImage.of(
    width = 2,
    height = 2,
    hotspotX = 0,
    hotspotY = 0,
    pixels = intArrayOf(
        0xFFFF0000.toInt(), 0xFF00FF00.toInt(),
        0xFF0000FF.toInt(), 0xFFFFFFFF.toInt(),
    ),
)

window.setCursor(cursor)
```

The caller may retain and reuse an image. The library converts ARGB to GLFW's
non-premultiplied RGBA8 layout and creates the native cursor on the service
thread.

## Visibility and mode

`setCursorVisible` and `setCursorMode` are separate controls:

- `setCursorVisible(true)` records that the cursor should be visible.
- `setCursorVisible(false)` records that it should be hidden.
- `setCursorMode(NORMAL)` allows the recorded visibility to apply.
- `setCursorMode(HIDDEN)` hides the cursor while preserving normal movement.
- `setCursorMode(DISABLED)` hides and grabs the cursor and reports virtual,
  unbounded movement.

`DISABLED` overrides recorded visibility. While disabled, changing visibility
only updates the recorded value. Returning to `NORMAL` or `HIDDEN` applies that
recorded value. `setCursorVisible` is a convenience for the common visibility
toggle and does not call GLFW directly.

Applications that use `DISABLED` should provide a restore path on focus loss
and a way to return to `NORMAL`.

## Failure behavior

- Invalid dimensions, pixel counts, or hotspots are rejected by
  `CursorImage.of` before any native call.
- `glfwCreateCursor` failure becomes `GlfwException` when GLFW reports an
  error, or `GlException` when it does not.
- A failed replacement leaves the previous cursor installed.
- The native cursor is never partially installed: creation precedes
  `glfwSetCursor`, and the old handle is destroyed only after installation.

## Related

[Public API](public-api.md) · [Threading](threading.md) ·
[Window icons](window-icon.md) · [Frame contract](frame.md)