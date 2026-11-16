# ADR-0008: Debug and release build profiles

- **Status:** Accepted
- **Date:** 2026-11-15
- **Deciders:** project owner
- **Supersedes:** none

## Context

The build needs two distinct variants, analogous to CMake's build types, without
the C preprocessor. Semantics are fixed here; the Gradle implementation belongs
to the build sections.

Two corrections to the CMake analogy are recorded up front:

1. CMake defines `NDEBUG` for `Release` and `RelWithDebInfo`, **not** for
   `Debug`. Defining `NDEBUG` in a debug build would disable exactly the
   assertions the debug profile exists for. This ADR uses the standard
   polarity: debug keeps checks, release removes them.
2. The JVM has no preprocessor and no `NDEBUG` macro. The equivalent is a
   build-time constant that the Kotlin compiler constant-folds, plus LWJGL's
   own check toggles for the project's run and test tasks.

Linux adds a platform constraint. RenderDoc capture in this project's workflow
requires X11; Wayland is not supported. GLFW selects Wayland before X11 by
default on Linux. GLFW 3.5.1 (bundled by LWJGL 3.4.3) supports the
`GLFW_PLATFORM` init hint, which can force X11. The hint is process-global and
must be set before `glfwInit`.

Debug builds must never reach Maven Central: they carry extra validation, may
fail earlier on invalid input, and force a platform backend.

## Decision

We will expose one build-time Gradle property:

```
terminalWindow.profile = debug | release
```

| Profile | Default | Publishable | `BuildInfo.DEBUG` | LWJGL checks | Linux platform hint |
|---|---|---|---|---|---|
| `debug` | local builds | no | `true` | enabled, `org.lwjgl.util.Debug=true` | `GLFW_PLATFORM_X11` |
| `release` | CI, publication | yes | `false` | disabled, `org.lwjgl.util.NoChecks=true` | none (GLFW default) |

Rules:

1. **Publication is release-only.** Every `publish*` task fails when the
   profile is `debug`. A debug JAR must not be uploadable by accident.
2. **Debug checks are compile-time gated.** Internal fast-fail validation is
   written as `if (BuildInfo.DEBUG) { ... }`, where `BuildInfo.DEBUG` is a
   generated `const val`. The release compiler removes the branch; there is no
   runtime cost and no reflection. Valid input behaves identically in both
   profiles.
3. **Kotlin `assert` is not the mechanism.** JVM assertion state depends on the
   consumer's `-ea` flag, which the library does not control. Library
   invariants use explicit checks; the JVM `-ea` flag is not part of the
   profile contract.
4. **LWJGL toggles belong to tasks, not to library code.** `Configuration`
   options are process-global and read once. The library never sets system
   properties at runtime (invariant 6). The project's own run and test tasks
   pass:
   - debug: `-Dorg.lwjgl.util.Debug=true`, checks left enabled;
   - release: `-Dorg.lwjgl.util.NoChecks=true`.
5. **Linux debug forces X11.** Before `glfwInit`, the service thread checks the
   profile and `os.name`. If the profile is `debug` and the OS is Linux, it sets
   `glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11)`. No other OS and no other
   profile sets a platform hint.
6. **No silent fallback.** If the X11 hint is set and initialization fails
   because X11 is unavailable, the error names the forced platform, the debug
   profile, and the X11/RenderDoc reason. The service thread does not retry with
   Wayland.
7. **Release uses GLFW's default selection.** On Linux this may select Wayland
   or X11 according to GLFW's own priority; the library does not override it.
8. **The profile is not public API.** It changes no package, public signature,
   instance layout, font format, or wire format. It is not exposed in
   `ContextInfo`.

The platform-selection rule is a pure function of `(profile, os.name)` so it can
be unit-tested without initializing GLFW.

## Consequences

### Positive

- RenderDoc can capture Linux debug builds without the user changing their
  session: the library requests X11 before GLFW initializes.
- Release bytecode contains no debug branches; the constant is folded away.
- A debug JAR cannot be published, so Central never receives a variant with
  forced platform selection.
- The debug/release split is explicit and reproducible instead of relying on
  whatever JVM flags happen to be set.

### Negative

- On a Wayland-only Linux session with no XWayland, a debug build fails to open
  a window. This is intentional and diagnosed.
- Release builds on Wayland cannot be captured with RenderDoc; use a debug
  build or start an X11 session. No runtime override exists in 2.0.
- The forced hint is process-global, so it affects every GLFW window in the
  process, not just this library's window.
- Two profiles mean CI must exercise both; a release-only matrix would miss
  debug-only validation bugs.

### Neutral

- The profile is build-time, not runtime. Consumers of a published JAR always
  get release semantics regardless of their own build.
- Debug and release JARs differ in bytecode; this is not a compatibility
  concern because debug artifacts are not published.
- CI may run debug tests under Xvfb or XWayland; the exact runner setup is
  owned by the CI section.

## Alternatives

| Alternative | Why rejected |
|---|---|
| Define `NDEBUG` in debug | Inverted polarity: it disables the checks the debug profile exists to enable. |
| Always force X11 on Linux | Penalizes every release consumer on Wayland with an XWayland dependency; contradicts GLFW's default. |
| Runtime system-property override | Hidden process-global state, outside the public API; can be added additively later if demand exists. |
| Publish separate debug and release artifacts | Maven Central release semantics do not support mutable debug variants; doubles the ABI surface. |
| Gradle source sets for debug/release | Duplicate compilation and Kotlin metadata drift with no consumer benefit. |
| Rely on JVM `-ea` and `assert` | Consumer-controlled and global; assertions would be off in most production runs and on in some, with no build-time guarantee. |
| Set LWJGL `Configuration` statics from library code | Mutates global state the caller did not request, and the values are read once at class initialization. |

## References

- GLFW 3.4 release notes: `GLFW_PLATFORM` init hint, runtime platform selection.
- LWJGL `Configuration.DEBUG` (`org.lwjgl.util.Debug`) and
  `Configuration.DISABLE_CHECKS` (`org.lwjgl.util.NoChecks`).
- ADR-0004 for the service thread that sets the hint before `glfwInit`.
- `docs/compatibility.md` for the exclusion of debug artifacts.
