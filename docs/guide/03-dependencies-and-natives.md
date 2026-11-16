# 03 — Dependencies, natives, Kotlin baseline

**Phase:** foundations · **Depends on:** 02 · **Blocks:** 04, 06, 11

## Goal

Minimal, publishable dependency graph: LWJGL + JOML as `api`, host natives only for local dev/tests, nothing platform-specific in the published POM.

## Deliverables

- `gradle/libs.versions.toml`: `kotlin = "2.4.21"`, `lwjgl = "3.4.3"`, `joml = "1.10.9"`, `junit = "6.1.3"`, `dokka = "2.3.0"`, `detekt = "1.23.8"`, `ktlint = "1.8.0"`.
- `library/build.gradle.kts`: `api` deps, isolated `natives` configuration, test wiring.
- `examples/build.gradle.kts`: runtime natives.
- `docs/dependencies.md`: consumer snippets (Gradle + Maven) for Linux/Windows/macOS x64/arm64.

## Steps

1. **LWJGL.** `api("org.lwjgl:lwjgl:3.4.3")`, `api("org.lwjgl:lwjgl-glfw:3.4.3")`, `api("org.lwjgl:lwjgl-opengl:3.4.3")`; `lwjgl-stb` is an `implementation`/runtime dependency for image decoding. Explicit versions; the BOM is unnecessary (LWJGL modules have no transitive deps) and publishing a platform POM dependency is noise.
2. **JOML.** `api("org.joml:joml:1.10.9")`. It may be unused by the core frame path in 2.0; that is accepted. Use it for 2-D vector work (mouse/cell coordinates, geometry helpers) and in examples; never wrap it in a parallel type hierarchy.
3. **Kotlin stdlib.** KGP adds it automatically to `implementation`; the published POM gets it at runtime scope. Do not hand-declare it (avoids duplicate-version conflicts); document that consumers on Kotlin already have it.
4. **No GLM.** Verified: no `lwjgl-glm` artifact and no GLM package in `lwjgl`/`lwjgl-opengl` (3.1.0–3.4.3). JOML is the math library.
5. **Natives at build time only.** Create an isolated resolvable configuration (e.g. `natives`) with classifiers from host detection:
   - linux x86_64 → `natives-linux`; linux aarch64 → `natives-linux-arm64`
   - windows x86_64 → `natives-windows`; windows aarch64 → `natives-windows-arm64`
   - macos aarch64 → `natives-macos-arm64`; macos x86_64 → `natives-macos`
   Add `lwjgl`, `lwjgl-glfw`, `lwjgl-opengl`, and `lwjgl-stb` natives (all four jars exist on Central).
6. **Keep natives out of publication.** Only `api`/`implementation` declarations are published. The `natives` configuration must not extend a published configuration; wire it to `testRuntimeOnly`, the `integrationTest` suite, `:examples` runtime, and `:benchmarks` runtime.
7. **JVM baseline.** `jvmToolchain(25)`, `jvmTarget = JVM_25`; tests compile with the same target. No preview features.
8. **Smoke proof.** `./gradlew :library:dependencies --configuration runtimeClasspath` shows LWJGL (`lwjgl-stb` included) + JOML + Kotlin stdlib + host natives.

## Acceptance

- [x] `:library:runtimeClasspath` contains `lwjgl`, `lwjgl-glfw`, `lwjgl-opengl`, `lwjgl-stb`, `joml`, `kotlin-stdlib` and exactly one native classifier per LWJGL module for the host.
- [x] `:library:apiElements` / `:library:runtimeElements` contain no native classifier artifacts.
- [x] `./gradlew dependencies` shows no GLM, no logging framework, no coroutines.
- [x] `docs/dependencies.md` has working Gradle and Maven consumer snippets for linux/windows/macos (x64 + arm64), including JOML transitively.
- [x] `./gradlew build` still green.

## Pitfalls

- Classifier naming is not symmetric: 64-bit Windows/Linux/macOS x64 jars drop the `x86_64` suffix; arm64 and arm32 keep it. Copy names from Central, do not synthesize.
- `runtimeOnly("org.lwjgl:lwjgl::natives-linux")` in `:library` would leak into the POM as a published runtime dependency. Use the isolated configuration.
- JOML is `api`: a future public helper returning `Vector2f` must not force a republish just to change scope.
- Native jars are extracted to a temp dir by LWJGL; on read-only/container filesystems set `-Djava.io.tmpdir` accordingly (document, do not code around it).

## Plan seed

> Plan section 03 only: wire LWJGL 3.4.3 (core/glfw/opengl, plus the stb_image runtime), JOML 1.10.9, and the Kotlin stdlib; add a non-published host-native configuration for tests/examples; document consumer setup. No rendering code.
