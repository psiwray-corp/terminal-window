# AGENTS.md

Instructions for coding agents in this repository. The user owns Git history.

## Git: never commit

This rule overrides every other file. If `GUIDE.md`, a section file, a task
description, or an automation prompt says to commit, it means "leave the work
for the user"; it never authorizes an agent commit.

- Do **not** run `git commit`, `git add`, `git stash`, `git reset`, `git checkout --`, or `git restore`.
- Do **not** use the agent `commit` namespace.
- Do **not** create a todo item, plan step, or acceptance criterion for committing.
- Do **not** amend, rebase, squash, tag, or rename branches.
- Leave all work in the working tree. At the end, report the changed paths and a suggested commit subject/body; the user commits.
- Read-only Git commands (`status`, `diff`, `log`, `show`) are allowed.
- The branch is `master`. Do not rename it to `main`.
- Never add build output, `.gradle/`, `.kotlin/`, or IDE metadata to the tree.

## How work is scoped

- `GUIDE.md` is the plan, fixed facts, invariants, and progress tracker.
- Read `GUIDE.md`, then exactly one `docs/guide/NN-*.md` for the section being implemented. Do not read sibling section files.
- Implement only that section's deliverables. Do not pre-implement later sections; add a private seam if a later section needs a hook.
- A section is done only when every `## Acceptance` box is demonstrated, not inferred. Update the section checkboxes and the `GUIDE.md` progress entry in the same pass, but leave the commit to the user.
- If a user instruction conflicts with the guide, the user wins. Remove the stale requirement from the guide and the docs it affects.
- Section 25 is window cursor customization. There is no v1 migration section.

## Project facts

- Coordinates: `com.psiwray.organization:terminal-window`; development version `2.0.0-SNAPSHOT`, release `2.0.0`.
- Package root: `com.psiwray.organization.terminal_window` — identical to v1. v1 and v2 must never share a classpath.
- Toolchain: JDK 25, Kotlin 2.4.21, Gradle 9.9.1 (pinned wrapper), LWJGL 3.4.3, JOML 1.10.9, JUnit 6.1.3.
- OpenGL 3.3 core, GLSL `#version 330`.
- Automatic module name: `com.psiwray.organization.terminal_window`.
- License: MIT. v1 artifacts remain GPL-3.0. There is no `MIGRATION.md`; v2 replaces v1 in use as `2.0.0`. Maven Central versions are immutable: never claim `1.4.1` can be overwritten or redirected.
- Build profiles: Gradle property `terminalWindow.profile` = `debug` (local default) or `release` (publish-only). Debug forces `GLFW_PLATFORM_X11` on Linux; release lets GLFW choose.
- The Wrapper checksum is pinned. Do not downgrade Gradle or regenerate the wrapper without being asked.
- `gradlew.bat` is checked in as CRLF (`.gitattributes` `*.bat text eol=crlf`); do not normalize it to LF.

## Architecture invariants

- GLFW initialization, termination, window creation/destruction, event polling, and window state reads/writes run on the process main thread (the service thread). Never call them from an owner thread.
- Rendering and GL calls run on the per-window owner thread with that window's context current. Context transfer is explicit (`releaseContext` then acquire); never infer it.
- Input callbacks fire on the service thread, are routed per window by the GLFW user pointer, and are drained per window by the owner. Do not add a global event queue.
- One instance-buffer upload and one draw call per window per rendered frame. Zero per-frame heap allocation after warm-up.
- Instance data is 16 bytes per cell; `InstanceFormat` is the only place that encodes byte offsets.
- The library draw path is opaque: no blending, depth, or multisampling.
- Font files are untrusted input: validate every row, length, and code point, and report `file:line:column`.
- Never call `System.exit`; never install global AWT or GLFW hooks the caller did not request.
- The published POM must not depend on platform-specific native classifiers; consumers add natives.
- Central versions are immutable: a broken release is `2.0.1`, never a re-upload.

## Kotlin and API conventions

- `explicitApi()` is enabled; every public declaration needs explicit visibility and types.
- No `!!` in library code. Use `internal` for implementation. No coroutines; threading is explicit.
- Immutable value types are `data class`; stateless helpers are `object`; `Modifiers` is the only `@JvmInline value class`; colors are `Int` ARGB.
- Public declarations need KDoc. Add `@JvmStatic` (companions) or `@JvmOverloads` for Java callers when unambiguous.
- Hot paths (cell write, instance pack, upload) use indexed loops and primitives: no boxing, iterators, `Pair`, or capturing lambdas per cell/frame.
- Use `use {}` only for `Closeable`; GL handles are not `Closeable` and get an explicit `close()`.
- Style: UTF-8, LF, 4 spaces, max line length 120, `ktlint_official`.

## Build and verification

- Always use the wrapper: `./gradlew <task>`.
- `./gradlew build` is the baseline. Narrower checks first, then one final build if the change warrants it.
- `./gradlew minimumAcceptanceTest` is the default GL acceptance run. Long tests are tagged `long`; `./gradlew fullTest` (or `ci/full-test.sh`) runs them. Do not put long tests in the default suite.
- `./gradlew -q projects` must list `:`, `:library`, `:examples`.
- `./gradlew -q javaToolchains` must show JDK 25 in use.
- Verify behavior on the surface that changed. For UI/CLI, run it and report what was observed; if a surface cannot be run, say so explicitly.
- Do not rerun a check to confirm a failure the user already reported. Treat the report as ground truth.
- Delete throwaway scripts after use. Do not add tests for wiring, wording, or tautologies; tests must catch consumer-visible bugs.
- Configuration cache is enabled. Keep new build logic configuration-cache compatible.

## Documentation

- ADRs in `docs/adr/` are decision records. Do not rewrite an accepted decision unless the user changes it; if revised pre-release, note the revision in the ADR.
- Keep `docs/compatibility.md`, `README.md`, and `CHANGELOG.md` in sync with user-visible changes.
- Do not promise a `MIGRATION.md` or a v1 migration section.
- Every numeric threshold (cell size, queue depth, texture limit) is set once in the section that owns it and referenced elsewhere by name.
