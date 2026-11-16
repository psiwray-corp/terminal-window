# v2.1 ideas

**Status:** early ideas only. This document is not a commitment, schedule, or
API proposal. OpenGL 3.3 core remains the current backend; no v2.1 behavior is
guaranteed.

These are areas worth exploring after v2.0. The ordering is not fixed.

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

## Clipboard

A future version could offer text clipboard access for copy and paste. The
useful scope—plain text, selection handling, platform-specific limitations,
threading, and how it integrates with input events—is undecided.

Clipboard behavior differs across X11, Wayland, Windows, and macOS, and may
require dependencies the current OpenGL-only classpath does not have. Any
implementation should remain opt-in and must not make the core library depend
on AWT or platform-specific UI toolkits. It is not committed for v2.1.

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
- the clipboard API and platform support boundaries;
- the Vulkan packaging and backend-selection strategy;
- support, test, and release scope for all items.

These ideas should be split or dropped if they cannot be implemented without
regressing the current OpenGL path.

## Related

[Font format](font-format.md) · [Performance](performance.md) ·
[Architecture](architecture.md)