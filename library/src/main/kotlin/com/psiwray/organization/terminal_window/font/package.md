# Package com.psiwray.organization.terminal_window.font

Immutable `.twf` font data and strict parsing entry points. `BitmapFont` owns
validated glyph masks and fallback metadata; `FontFormatException` reports the
source path and one-based line and Unicode-scalar column.

Font values are immutable and may be shared by any number of windows. Parsing
does not require GLFW or a current OpenGL context. Atlas baking and GL upload
are internal implementation details.