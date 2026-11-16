# 01 — Project charter and decision records

**Phase:** decisions · **Depends on:** — · **Blocks:** all

## Goal

Lock identity, runtime, and the architectural choices every later section cites. No build files, no Kotlin.

## Deliverables

- `docs/adr/0000-template.md` (MADR style: Context / Decision / Consequences / Alternatives).
- `docs/adr/0001-artifact-identity.md`
- `docs/adr/0002-license.md`
- `docs/adr/0003-runtime-and-language.md`
- `docs/adr/0004-threading-model.md`
- `docs/adr/0005-rendering-strategy.md`
- `docs/adr/0006-font-format.md`
- `docs/adr/0007-module-identity.md`
- `docs/architecture.md` — package map, object map, frame pipeline.
- `docs/compatibility.md` — what "2.x compatible" means.

## Steps

1. **Identity.** Coordinates `com.psiwray.organization:terminal-window`, release `2.0.0`, GitHub `psiwray-corp/terminal-window`, package root **`com.psiwray.organization.terminal_window`** (identical to v1's root; v1 and v2 must not coexist on one classpath). Record v1 facts: `1.4.1` (2021-10-24, 9 versions), Java 11, LWJGL 3.2.0 + JOML 1.10.2, GPL-3.0.
2. **License.** v1 shipped GPL-3.0. Decide whether 2.x stays GPL-3.0 (continuity, but discourages library adoption) or relicenses (allowed only for code you wrote). State that published v1 artifacts remain under their original license.
3. **Runtime.** Kotlin 2.4.21 on JDK 25, JVM target 25. Gradle 9.9.1 runs on 25 (supported since Gradle 9.1). Rationale: current LTS, null safety, data/sealed classes, no checked-exception noise. Java interop is best-effort, not a goal.
4. **Math.** Include JOML 1.10.9 (`org.joml:joml`) as `api` per the owner's request. The core renderer still emits clip-space quads and needs no matrix math; JOML is the sanctioned type library for 2-D/3-D vectors in public helpers and examples. No wrapper types around it.
5. **GL baseline.** OpenGL 3.3 core, GLSL 330. Justify: `glVertexAttribDivisor` is core only in 3.3; `gl_VertexID` is core in 3.0; 3.0 would need `ARB_instanced_arrays` or per-vertex expansion. macOS exposes at most 4.1 core, so 3.3 is portable. Context ladder: request 3.3 core → retry 4.1 core → fail with diagnostics.
6. **Threading.** Encode the GLFW contract (main thread: init/terminate/create/destroy/poll and window state; any thread: make-current/swap/swap-interval). Design: service thread owns GLFW, owner thread owns rendering; ownership transfer is explicit (`releaseContext`/`acquireContext`); all window state setters are queued to the service thread.
7. **Rendering.** One instanced draw per frame; 4 vertices derived from `gl_VertexID`; per-instance data 16 B (`u16 x, u16 y, u16 glyph, u16 pad, RGBA8 fg, RGBA8 bg`); single `R8` atlas; no depth, no blending, no mipmaps; one upload per frame.
8. **Font format.** `.twf`, text, `version 1`, pixels `.`/`#`, `//` comments, strict row lengths, optional `fallback`. Point at section 07.
9. **Module identity.** Kotlin emits no `module-info`; use `Automatic-Module-Name: com.psiwray.organization.terminal_window` in the JAR manifest. Full JPMS is deferred until a `module-info.java` can be added without a dual-source-set build. Record the tradeoff.
10. **Scope.** In: one window per thread, matrix, colors, bitmap font, input events, resize. Out for 2.0: TTF/OTF, shaping/IME, clipboard, scrollback, multiple fonts per window, Vulkan/EGL, coroutines, Java-first API.
11. **Compatibility.** Semver for the public Kotlin API; `internal` is exempt; thread affinity and GL requirements are part of the contract. Binary compatibility is checked against Kotlin metadata, not just JVM signatures.

## Acceptance

- [x] All seven ADRs exist and are accepted (no `TBD`).
- [x] ADR-0003 explains JDK 25 + Kotlin 2.4.21 and why Java interop is secondary.
- [x] ADR-0003 or ADR-0007 states why JOML is included and why the renderer still uses none.
- [x] ADR-0004 matches the GLFW documentation verbatim on which calls are main-thread-only.
- [x] ADR-0007 records the automatic module name and the deferred JPMS decision.
- [x] `docs/architecture.md` shows the package map and a frame sequence diagram.
- [x] License decision is final and consistent with `docs/compatibility.md`.
- [x] No Kotlin or Gradle files were created.

## Pitfalls

- Do not promise "OpenGL 3.0 compatible"; the instancing path does not exist there.
- Do not add GLM; it does not exist for LWJGL 3. JOML is the math dependency.
- Do not claim v1 can be removed or redirected from Central; it cannot. README and section 21 handle v1 messaging.
- Do not let the "per-thread" wording imply per-thread `glfwInit`; GLFW init is process-global and main-thread-only.
- Package root equals v1's: a mixed classpath is unsupported; say so loudly.

## Plan seed

> Plan section 01 only. Produce seven ADRs plus `docs/architecture.md` and `docs/compatibility.md` for a Kotlin 2.4.21 / JDK 25 / LWJGL 3.4.3 / JOML 1.10.9 / OpenGL 3.3-core terminal library published as `com.psiwray.organization:terminal-window:2.0.0`, package root `com.psiwray.organization.terminal_window`. Decisions are given in the section file; do not re-litigate them. No code.