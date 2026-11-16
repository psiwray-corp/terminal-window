# ADR-0003: Runtime, language, and dependencies

- **Status:** Accepted
- **Date:** 2026-11-15
- **Deciders:** project owner
- **Supersedes:** none

## Context

Version 1 targeted Java 11, LWJGL 3.2.0, and JOML 1.10.2. Version 2 is a new
implementation and can choose its runtime without carrying version 1's
constraints. The library is a graphics library: it owns a GLFW window, an
OpenGL context, and a render loop, so JVM startup cost and threading are part of
the design rather than incidental.

The project requires a current stable toolchain: Gradle 9.9.1 embeds Kotlin
2.4.21, and Kotlin 2.5 is still a release candidate. JDK 25 is the current LTS.
The owner explicitly requested JOML as a dependency, while the renderer design
emits clip-space quads and performs no matrix math.

## Decision

We will build version 2 with:

| Item | Value | Role |
|---|---|---|
| Language | Kotlin 2.4.21 | Library source and tests |
| JVM target | 25 | Class file version 69 |
| JDK toolchain | 25 | Compile and test runtime |
| Build | Gradle 9.9.1, Kotlin DSL, version catalog | Build and publication |
| Bindings | LWJGL 3.4.3 (`lwjgl`, `lwjgl-glfw`, `lwjgl-opengl`, `lwjgl-stb` runtime) | Window, input, GL, image decoding |
| Math | JOML 1.10.9 (`org.joml:joml`) | Public `api` dependency |
| GL baseline | OpenGL 3.3 core, GLSL `#version 330` | Rendering |

Kotlin is compiled with `explicitApi()` in strict mode. Library code uses
`internal` for implementation, never `!!`, and no coroutines; threading is
explicit and documented. Public declarations carry KDoc; Dokka produces the
Javadoc jar.

Java interoperability is best effort, not a goal. `@JvmStatic` and
`@JvmOverloads` are added only where they do not create ambiguous overloads.
Kotlin-specific types (default arguments, data classes, value classes) are part
of the public API.

### Why JDK 25 and JVM target 25

JDK 25 is the current long-term-support release. Gradle 9.9.1 supports running
on JDK 25 (supported since Gradle 9.1), and the toolchain block pins the
compiler and test JVM so a developer's default JDK cannot change the output.
Targeting 25 rather than 21 lets the library use the current language level and
avoids a second toolchain in CI. The cost is that consumers must run on JVM 25
or newer; Android and JVM 21 deployments are out of scope for 2.x.

### Why JOML is included and why the renderer uses none

JOML is a public `api` dependency because public helpers and examples may expose
JOML vector types; making it `implementation` would hide types that appear in
signatures. No wrapper types are introduced around JOML.

The core renderer does not use JOML. In version 2 the vertex shader derives the
unit quad from `gl_VertexID`, and cell positions are integer instance
attributes. The only matrix-like work is the viewport transform, which is a
two-term scale-and-bias computed from the framebuffer size. Adding a JOML
`Matrix4f` there would allocate and perform math with no benefit. JOML is
therefore a consumer-facing convenience, not a render-path dependency.

### Why Java interop is secondary

Kotlin's nullability, default arguments, and value classes do not map cleanly to
Java. Designing for Java first would either remove those features or require a
parallel Java API. The library is published as a Kotlin library; Java callers
can use it, but the source of truth is the Kotlin surface and its metadata.

### Not included

No coroutines, no dependency-injection framework, no logging facade, no
reflection-based configuration, no shading. LWJGL native classifiers are not
published in the POM (the published POM must stay platform-neutral; consumers
add natives for their platform).

## Consequences

### Positive

- One current toolchain across local builds and CI; the Gradle wrapper and
  version catalog pin every version.
- Kotlin's null safety and data/sealed classes make invalid states harder to
  represent in events and configuration.
- JOML is available to consumers without transitive version conflicts, because
  the version is fixed and published.
- The renderer has no matrix allocation and no dependency on JOML internals.

### Negative

- Consumers must run JDK 25+; this excludes Android and older JVM deployments
  for the entire 2.x line.
- Kotlin stdlib is a runtime dependency; the artifact is not usable from a
  pure-Java, zero-dependency environment without it.
- Kotlin metadata is part of the API contract, so binary compatibility checking
  must understand Kotlin, not only JVM descriptors.
- JOML is an unused dependency for consumers who never call vector helpers.

### Neutral

- Kotlin 2.5 is expected to be source-compatible; upgrading the compiler within
  2.x is allowed if the API dump and tests remain unchanged.
- Native libraries are the consumer's responsibility; the library fails with a
  diagnostic if LWJGL natives are missing.

## Alternatives

| Alternative | Why rejected |
|---|---|
| Java 25 | Verbose event and configuration types, no null safety, checked exceptions in IO paths, no data classes. |
| Kotlin 2.5 release candidate | Pre-release compiler; the build must be reproducible on stable tooling. |
| Kotlin 2.2 or older | Not embedded in Gradle 9.9.1; would add a second compiler version to maintain. |
| JVM target 21 | Would require a second toolchain and gives up nothing that 25 costs; the owner chose the current LTS. |
| JOML as `implementation` | Public helpers expose JOML types; consumers would not see them on their compile classpath. |
| No JOML | Explicitly requested; would push consumers to add their own vector library. |
| GLM | There is no GLM artifact for LWJGL 3; this option does not exist. |
| LWJGL 3.2.x | Version 1's binding; lacks current GLFW fixes and is out of maintenance for this project. |

## References

- Kotlin 2.4.21 release notes.
- Gradle 9.9.1 compatibility matrix (running on JDK 25 supported since 9.1).
- LWJGL 3.4.3 on Maven Central.
- JOML 1.10.9 on Maven Central.
- `docs/compatibility.md` for the JVM baseline commitment.
