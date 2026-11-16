# Window icons

`TerminalWindow` can replace or clear the window icon without exposing GLFW
image structures or handles.

## Threading and lifetime

`setIcon` and `clearIcon` are safe from any application thread:

```kotlin
window.setIcon(
    WindowIcon.fromPath(Path.of("icons/terminal-32.png")),
    WindowIcon.fromPath(Path.of("icons/terminal-64.png")),
)

window.clearIcon()
```

The operation is queued to the service thread. The `WindowIcon` values are
immutable and copy their pixel arrays; they may be retained and reused. The
library passes a temporary native image array to GLFW and frees it immediately;
GLFW copies the pixel contents. There is no native icon handle to close and no
per-window icon resource to release.

Use-after-close throws `IllegalStateException`. `setIcon` with no arguments is
rejected; use `clearIcon()` to restore the platform default.

## Pixel format

`WindowIcon` uses the same public pixel layout as the rest of the library:

- `0xAARRGGBB`, row-major.
- Top-left origin.
- Alpha is not premultiplied.
- The caller's `IntArray` is copied by `WindowIcon.of`; later mutation does not
  affect the icon.

`WindowIcon.fromPath` decodes PNG, JPEG, BMP, TGA, GIF, and other formats
supported by LWJGL's stb_image. The image is decoded on the calling thread, not
on the service thread.

## Multiple sizes

GLFW accepts an array of icon images. Supply several sizes when the window
manager should choose a better fit for title bars, taskbars, or high-DPI
displays:

```kotlin
window.setIcon(
    WindowIcon.fromPath(Path.of("icons/terminal-16.png")),
    WindowIcon.fromPath(Path.of("icons/terminal-32.png")),
    WindowIcon.fromPath(Path.of("icons/terminal-64.png")),
)
```

The exact image selected is platform- and window-manager-dependent. The
library does not promise a particular size or a particular visual result.

## Format and failure rules

- Dimensions must be positive and the pixel count must match the dimensions.
- Invalid images are rejected by `WindowIcon.of` before any native call.
- `fromPath` reports unreadable or undecodable files as `IOException`.
- A failed icon operation does not change the terminal matrix or trigger a
  repaint.
- Icon rendering and taskbar behavior are operating-system features; the
  library only forwards the images to GLFW.

## Related

[Public API](public-api.md) · [Cursor customization](cursor.md)