# 21 — Documentation set

**Phase:** docs · **Depends on:** 18, 19, 25 · **Blocks:** 23

## Goal

A fresh visitor can install, run, and understand the threading and font contracts without reading source. The README is the product page.

## Deliverables

- `README.md` (≤ 160 lines)
- `CHANGELOG.md` (Keep a Changelog, SemVer)
- `CONTRIBUTING.md`
- `docs/threading.md`, `docs/font-format.md`, `docs/frame.md`, `docs/resize.md`, `docs/performance.md`, `docs/public-api.md`, `docs/dependencies.md`, `docs/cursor.md` (cross-linked)
- Optional: GitHub Pages with Dokka HTML (`docs.yml` workflow)

## README outline

1. Name, one-sentence pitch, badges (GitHub CI, Maven Central, license, JDK 25, Kotlin 2.4).
2. **v1 notice:** `1.x` is abandoned; use `2.x`; coordinates unchanged; package root unchanged, so do not mix 1.x and 2.x on one classpath.
3. Requirements: JDK 25 runtime, OpenGL 3.3 core, desktop OS with GLFW support.
4. Install: Gradle + Maven, **including natives** (copy from `docs/dependencies.md`); note JOML arrives transitively.
5. Kotlin quickstart (≤ 15 lines): open window, write text, add listener, loop `frame()` with `use`.
6. Threading in three bullets (service thread, owner thread, one upload per frame) linking `docs/threading.md`.
7. Font format: link + 5-line sample of a glyph block.
8. Examples + screenshot.
9. Performance summary (table from 20).
10. License and contributing.

## Steps

1. Write the README from the examples, not from aspiration: every snippet must compile. Extract snippets into `examples` or a docs test.
2. Ensure each `docs/*.md` has a single owner section and no duplicated content; cross-link instead of copying.
3. `CHANGELOG.md`: `[Unreleased]` → `2.0.0` with Added/Changed/Removed/Fixed; explicitly note v1 removal of tileset/texture windows and the Java→Kotlin rewrite.
4. **CONTRIBUTING.md**: JDK 25, `./gradlew spotlessApply check`, how to run integration tests (`xvfb-run`), commit style, PR checklist, no new dependencies without an ADR, and the fact that CI runs on GitHub Actions via `ci/*.sh`.
5. Add a link checker to CI (optional): fail on broken relative links.
6. Optional Javadoc/Dokka publishing: GitHub Actions `pages` job that runs `./gradlew dokkaGenerate` and uploads `library/build/dokka/html`.

## Acceptance

- [x] A fresh clone can run `:examples:hello` using only README instructions.
- [x] Every code block in README compiles or is marked `text`.
- [x] No document contradicts the thread-affinity table or the GL 3.3 floor.
- [x] `CHANGELOG.md` has an entry for every section's user-visible change.
- [x] All internal links resolve.
- [x] Requirements state JDK 25 and Kotlin 2.4.21 explicitly.
- [x] CONTRIBUTING documents the shared `ci/*.sh` gates and both forge workflows.

## Pitfalls

- Do not claim "OpenGL 3.0" anywhere; the floor is 3.3 core.
- Do not show `implementation("...")` without the natives step; users will hit `UnsatisfiedLinkError`.
- Avoid duplicating the font grammar in the README; link the spec.
- Do not promise TTF/OTF support; it is v2.1 scope.
- Do not describe the library as Java-first; Kotlin is the API, Java interop is best-effort.

## Plan seed

> Plan section 21 only: write the README, CHANGELOG, CONTRIBUTING, and finalize the docs set with cross-links and compile-checked Kotlin snippets. No code changes beyond doc examples.