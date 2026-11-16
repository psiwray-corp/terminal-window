# 06 — Headless GL harness

**Phase:** foundations · **Depends on:** 03, 05 · **Blocks:** 15, 16, 17

## Goal

Deterministic, display-free GL tests in Kotlin: create a hidden 3.3-core context under Xvfb/Mesa, render into an FBO, read pixels back, and assert exact colors. This is the only proof the renderer works.

## Deliverables

- `library/src/integrationTest/kotlin/**` (JVM test suite `integrationTest`).
- `GlTestContext` helper: `glfwInit` guard, hidden window, `GL.createCapabilities()`, close.
- `TestFramebuffer`: RGBA8 color attachment, `readPixel(x,y)`, `readAll()`; no depth/stencil.
- `GlAssertions`: `assertPixel`, `assertRect`, `assertNoGlError`.
- Gradle wiring: `testing { suites { integrationTest } }`, Kotlin test deps, natives on its runtime classpath, tag filter `gl`, `maxParallelForks = 1`.
- `docs/testing.md`: local/CI commands.

## Steps

1. **Context creation.** Hidden (`GLFW_VISIBLE=false`) 64×64 window, hints 3.3 core + forward-compatible, `glfwMakeContextCurrent`, `GL.createCapabilities()`. Fail with one clear message if no display: "GL integration tests require a display or Xvfb".
2. **Determinism.** `LIBGL_ALWAYS_SOFTWARE=1` in CI; assert `GL_RENDERER` is logged, not asserted. Use `GL_NEAREST` and `glDisable(GL_DITHER)` so pixel reads are exact.
3. **FBO helper.** `glGenFramebuffers`, `glTexImage2D(GL_RGBA8)`, check `GL_FRAMEBUFFER_COMPLETE`, bind, set viewport to the FBO size. Restore default FBO (0) after each test.
4. **Readback.** `glReadPixels(..., GL_RGBA, GL_UNSIGNED_BYTE)` into a direct `ByteBuffer`; expose ARGB `Int`s. GL origin is bottom-left; flip `y` in the helper, not in tests.
5. **Isolation.** One context per test class (`@BeforeAll`/`@AfterAll`), fresh FBO per test; always `glGetError()` at teardown, fail on non-zero.
6. **Kotlin test setup.** `testImplementation(kotlin("test"))`, JUnit 5 BOM + Jupiter, `useJUnitPlatform()`. Backtick test names are allowed; `assertThrows`, `assertEquals`.
7. **Timeouts.** JUnit `@Timeout(20)` per test; a hung GLFW call must fail the build, not hang CI.
8. **Smoke test.** Clear the FBO to `0xFF102030` and assert the readback equals it exactly (no blending, no sRGB).

## Acceptance

- [x] `xvfb-run -a ./gradlew integrationTest` passes locally and in CI.
- [x] Without a display the suite fails fast with the documented message (not an NPE or a 60 s hang).
- [x] The clear-color test proves exact byte round-trip.
- [x] `glGetError() == GL_NO_ERROR` after every test.
- [x] `test` (unit) still runs with no display and no natives on macOS/Windows CI.

## Pitfalls

- `GL.createCapabilities()` must be called after the context is current and exactly once per context; calling it twice resets cached pointers.
- Default framebuffer size differs from window size on HiDPI; this harness renders to an FBO, so use the FBO size only.
- Mesa's default framebuffer may be sRGB-capable; use an RGBA8 FBO and no `GL_FRAMEBUFFER_SRGB` to keep byte-exact assertions.
- Kotlin lambdas in test helpers can box; irrelevant for tests, but do not copy the style into `src/main`.
- Do not share one FBO across parallel tests; keep forks at 1 initially.

## Plan seed

> Plan section 06 only: add a Kotlin `integrationTest` JVM test suite plus helpers to create a hidden OpenGL 3.3 core context under Xvfb/Mesa, render to an RGBA8 FBO, and assert exact pixels. Include the no-display failure path and CI wiring. No renderer code.