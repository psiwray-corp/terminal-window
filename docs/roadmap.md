# Future plans

**Status:** early ideas only. This document is not a commitment, schedule, or
API proposal. OpenGL 3.3 core remains the current backend; no future behavior
is guaranteed.

These are areas worth exploring after v2.0. Some are v2.1-sized library
changes; others are separate artifacts or examples. The ordering is not fixed.
Delivered items stay listed here, marked as such.

## Partial dirty updates

Today a matrix change marks the whole matrix dirty, and the renderer packs and
uploads the full instance buffer. That keeps the path simple, but even a
one-cell change currently pays for a whole-matrix upload.

A future version could investigate dirty-region tracking and partial buffer
updates. Possible directions include a dirty rectangle, row spans, or another
representation, with full uploads still used where they are cheaper. Whether
this helps, what the API should expose, and how it interacts with resizing,
statistics, and both graphics backends are all undecided.

The existing performance benchmark is the starting point for evaluating this.
No partial-update behavior is promised for v2.1 yet.

## Title statistics

The window title is currently a fixed string. A future version could allow a
custom title template that automatically includes selected runtime statistics,
such as dimensions, frame rate, frame time, upload activity, or draw activity.

The template syntax, available values, update cadence, threading details,
error handling, and how it coexists with `setTitle` are intentionally
undecided. Any design must avoid flooding the window manager with updates and
must remain optional for callers that want a plain title.

## Vulkan backend

A Vulkan backend is a possible long-term addition. It would be opt-in and would
need to preserve the terminal-facing API and the existing rendering
expectations where practical, while treating Vulkan lifetime, synchronization,
swapchain, and platform support as first-class concerns.

This is a substantial change: it touches window creation, backend selection,
resource ownership, shaders, packaging, testing, and platform support. Backend
configuration, public context information, native dependency packaging, and the
macOS strategy are all undecided. OpenGL should remain available regardless of
how this develops.

Vulkan is the highest-risk item in this list; exploring it does not imply a
v2.1 delivery.

## TTF/OTF fonts

The current font path is the bitmap `.twf` format. A future version could
investigate optionally loading TrueType or OpenType fonts, with rasterization
and atlas generation handled by the library or a companion component.

Font metrics, hinting, fallback behavior, glyph coverage, licensing, public API,
and whether this becomes a separate font source or a converter to `.twf` are
all undecided. Existing `.twf` support should not be removed to make room for
it. This is a larger change than the title or statistics work and should not be
treated as a small API addition.

## Clipboard — delivered

`TerminalWindow.setClipboardText`, `clipboardText`, `requestClipboardText`, and
`ClipboardListener` provide plain-text copy and paste through GLFW's
`glfwSetClipboardString` and `glfwGetClipboardString` on the service thread. The
core library still depends on no AWT or platform UI toolkit. See
[clipboard.md](clipboard.md).

Rich text, images, the X11 primary selection, selection tracking, and clipboard
history remain out of scope.

## Terminal emulator companion

A new companion library could parse VT100/xterm control sequences from a
program's output, render the resulting screen in a `TerminalWindow`, and send
keyboard and mouse input back to that program. It would be a separate artifact,
not part of the core library: the core stays a cell matrix plus input events,
while the emulator owns screen state, scrollback, terminal modes, and input
encoding.

Packaging is part of the desideratum. The emulator must ship as an executable
`.jar`, ideally with a GraalVM native-image build so the result starts with
effectively no JVM startup time. That requires native-image configuration for
reflection and resources, a native-image CI job, and a decision on whether the
jar and the native image are one artifact or two.

Sequence coverage (VT100 only versus xterm extensions), PTY creation on each
platform, key encoding (legacy sequences versus `modifyOtherKeys` or the kitty
protocol), scrollback, selection, and how much lives in the library versus the
launcher are undecided. This is a larger effort than the library items above;
platform-specific process and PTY code must not leak into the core artifact.

## Software rasterizer example

An example could render the same cell matrix with a CPU rasterizer instead of
the OpenGL path. It would demonstrate the matrix as a standalone data model and
serve as a reference for callers without a usable GPU.

Whether the example writes into a framebuffer uploaded once per frame or into a
native window surface, which font source it consumes, and whether it reuses
`InstanceFormat` are undecided. It must remain an example: the library draw
path stays OpenGL-only and gains no software fallback.

## Project bootstrapping

Two starting points for users creating a new terminal-window application: the
Maven archetype is delivered; a Gradle starter example is still planned.

### Maven archetype — delivered

`com.psiwray.organization:terminal-window-archetype` is a separate
`maven-archetype` artifact under the library's group with its own release
process. It generates a Kotlin/JVM terminal-window executable with the library
coordinates, a host LWJGL native classifier, a bundled `.twf` font, and a
minimal `TerminalWindow` `main`. Versions move in lockstep with the library.
The build attaches the POM metadata and sources/Javadoc jars Central requires;
the release profile signs them and uploads for manual publishing, and
`mvn verify` generates a project and packages it as an integration test.

### Gradle starter example

A checked-in Gradle example will bootstrap a user's application: Kotlin/JVM,
the `terminal-window` dependency, host LWJGL native classifiers, a bundled
`.twf` font, and a minimal `main` with a `run` task. It is meant to be copied
or used as a template, not generated, and CI builds it so it cannot drift from
the published API.

Its location (under `examples/` or a separate template directory), whether it
belongs to the main multi-project build, and how much it shares with the Maven
archetype are undecided.

## Constraints for any future work

- OpenGL 3.3 core remains supported.
- v1 and v2 package identity and compatibility rules do not change.
- No feature here should force existing OpenGL consumers to adopt Vulkan.
- CI should not acquire flaky performance thresholds or hardware-specific
  requirements.

## Deferred decisions

The following are deliberately left open until there is a concrete design and
measurements:

- the partial-update data model and its statistics;
- the title-template grammar and public entry point;
- the TTF/OTF loading, rasterization, and fallback model;
- the Vulkan packaging and backend-selection strategy;
- the emulator's sequence coverage, PTY layer, and input encoding;
- executable-jar and GraalVM native-image packaging for the emulator;
- the software rasterizer example's output path and font source;
- the Gradle starter's location, build wiring, and shared template structure;
- support, test, and release scope for all items.

These ideas should be split or dropped if they cannot be implemented without
regressing the current OpenGL path.

## Related

[Font format](font-format.md) · [Performance](performance.md) ·
[Architecture](architecture.md)