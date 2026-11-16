# Testing

## Unit tests

```bash
./gradlew test
```

Unit tests do not create a window, initialize GLFW, or load a GL context. They
run without a display and are the only tests executed by the Windows and macOS
CI jobs.

## Minimum acceptance and full profiles

The default unit and GL suites are the minimum acceptance run. Long-running
tests must be tagged `long`; both `test` and `integrationTest` exclude that tag,
while `fullUnitTest` and `fullIntegrationTest` include it.

```bash
# Minimum acceptance: unit suite + default GL suite
xvfb-run -a ./gradlew minimumAcceptanceTest

# Full suite: every unit and GL test, including @Tag("long")
xvfb-run -a ./gradlew fullTest
```

`minimumAcceptanceTest` depends on `:library:test` and
`:library:integrationTest`. `fullTest` depends on `:library:fullUnitTest` and
`:library:fullIntegrationTest`. `ci/integration-test.sh` is the CI entry point
for the minimum GL suite; `ci/full-test.sh` runs the full suite. Both set
`LIBGL_ALWAYS_SOFTWARE=1` and use `xvfb-run -a`. `ci/build.sh` compiles the
examples with `:examples:build` and the benchmark with `:benchmarks:build`; the
demos and benchmark are not run in CI because the matrix has no display.
`ci/install-gl-deps.sh` installs Xvfb, Mesa, and GLFW's X11 runtime libraries on
Debian/Ubuntu.

Do not put a long test in the default suite. Tag it `long` and make its
assertions deterministic so the full run is repeatable.

## Performance benchmark

The `:benchmarks` module is a manual, non-JMH benchmark. It opens hidden
vsyncless windows, binds an RGBA8 FBO, warms up with 2,000 frames, and measures
10,000 frames per scenario:

```bash
LIBGL_ALWAYS_SOFTWARE=1 ./gradlew :benchmarks:run
```

`ci/build.sh` only compiles `:benchmarks:build`; it never runs the benchmark.
Allocation above 1 KiB/frame or a non-1 upload/draw count fails the benchmark
process itself, not CI. See `docs/performance.md` for the budget and measured
numbers.

## GL integration tests

The integration harness creates a hidden GLFW window with an OpenGL 3.3 core,
forward-compatible context, makes it current, and calls
`GL.createCapabilities()`. The smoke harness uses a 64x64 window and renders
into an RGBA8 FBO. `WindowLifecycleTest` creates hidden `TerminalWindow`
instances on a dedicated service thread and transfers their contexts to an
owner worker.

Run the default suite under a virtual display:

```bash
xvfb-run -a ./gradlew integrationTest
```

Without an X server (or Xvfb), the suite fails in `@BeforeAll` with:

```text
GL integration tests require a display or Xvfb
```

The harness logs `GL_RENDERER` so an unexpected software or hardware driver is
visible in CI output. It is logged, not asserted.

## Test layout

| Piece | Responsibility |
|---|---|
| `GlTestContext` | One hidden window and core context per test class; initializes GLFW once and terminates it after the class. |
| `TestFramebuffer` | RGBA8 color-only FBO; top-left-origin `readPixel`/`readAll`; restores FBO 0 on close. |
| `GlAssertions` | Exact ARGB pixel/rectangle assertions and teardown `glGetError()` checks. |
| `HeadlessGlSmokeTest` | `@Tag("gl")` clear-color test proving byte-exact RGBA8 readback, plus a top/bottom split proving the top-left readback origin. |
| `WindowLifecycleTest` | `@Tag("gl")` hidden-window creation, context transfer, deferred destruction, close/await, repaint controls, and event-loop termination. |
| `ShaderProgramTest` | `@Tag("gl")` GLSL 330 link, attribute/uniform introspection, instance attribute pointers, resource loading, and compile diagnostics. |
| `GlyphAtlasTest` | `@Tag("gl")` R8 texture upload, nearest/clamped parameters, exact byte readback, and combined atlas/VAO/VBO deletion. |
| `RendererPixelTest` | `@Tag("gl")` instanced draw at 1x, 2x, and 3x into an FBO, exact colors, upright orientation, renderer cleanup, and per-frame allocation. |
| `TerminalWindowFrameTest` | `@Tag("gl")` `frame()` on the service and owner threads, stats, clean and forced ticks, and owner-thread misuse. |
| `ResizeTest` | `@Tag("gl")` resize/DPI formula combinations, native window growth, top-left preservation, blank new cells, and one upload per resize. |
| `WindowGeometryTest` | Pure content-scale/cell-grid formula tests; runs without a display. |
| `FramePipelineTest` | Pure frame-ordering, repaint-trigger, resize, minimized, failure, and `RenderStats` tests with a recording sink; runs without a display. |
| `ApiSurfaceTest` | Compiles the public-package class inventory and checks Kotlin visibility, no LWJGL types in public signatures, and no accidental public/internal boundary changes. |

All integration tests must be tagged `gl`. The minimum suite includes `gl` and
excludes `long`; the full integration task includes `gl` without the exclusion.
Both use `maxParallelForks = 1`, and each test class owns one context. A fresh
FBO is created for each test and the previous one is closed before the next
test.

## Assertion policy

Exact bytes are intentional only for operations with a specified, direct write:
`glClear`, scissored clears, unblended flat fills, and nearest-neighbour texel
reads with no shader arithmetic. The current tests use only `glClear`, with
dithering, blending, and sRGB conversion disabled, so vendor interpolation and
coverage rules are not involved.

Do not use exact pixels as a general renderer-output contract. Interpolated
geometry, glyph alpha coverage, blending, multisampling, and sRGB paths can
differ across drivers at the last ULP. Those tests should use a small per-channel
tolerance, coverage/region assertions, or a perceptual comparison instead. Log
`GL_RENDERER`; never assert it or make vendor-specific golden images.

`readAll()` returns row-major ARGB with row zero at the top. The helper flips
OpenGL's bottom-left origin; test code never does.

Dithering, blending, and `GL_FRAMEBUFFER_SRGB` are disabled on the FBO. The
smoke test clears to `0xFF102030` and asserts every pixel through `assertRect`,
plus corner reads through `assertPixel`. Any GL error left in the queue fails
teardown.

## Constraints

- `GlTestContext` keeps GLFW and the context on the JUnit test thread. JUnit 6's
  `@Timeout` defaults to same-thread execution; do not set `SEPARATE_THREAD` for
  those tests.
- `WindowLifecycleTest` uses a dedicated thread named `main` as the service
  thread, because `TerminalWindowService` binds GLFW to that thread. Context
  transfer to the owner worker is explicit.
- Call `GL.createCapabilities()` once per thread that makes the context current.
  `GlTestContext` calls it on the JUnit thread; `TerminalWindow.acquireContext()`
  calls it on the new owner thread. Do not call it twice on the same thread for
  the same context.
- The suite runs on the host that runs Gradle. It is wired for Linux Xvfb in CI.
  macOS needs `-XstartOnFirstThread` if the suite is run there manually.
- Integration tests are not part of `./gradlew test`; run them explicitly,
  `minimumAcceptanceTest`, `fullTest`, or the matching `ci/*.sh` entry point.

## Related

[Quality gates](quality.md) · [Performance](performance.md) ·
[Frame contract](frame.md) · [Threading](threading.md)
