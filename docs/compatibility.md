# Compatibility policy

This document defines what "2.x compatible" means for
`com.psiwray.organization:terminal-window`. It is normative: a change that
violates it requires a major version.

## Versioning

The artifact follows Semantic Versioning 2.0.0 with versions
`MAJOR.MINOR.PATCH`:

| Change | Version |
|---|---|
| Breaking public API or contract change | Major (`3.0.0`) |
| Additive public API, no breaking change | Minor (`2.1.0`) |
| Bug fix, documentation, performance, no API change | Patch (`2.0.1`) |

The 2.x line starts at `2.0.0`. Development builds are `2.0.0-SNAPSHOT` and are
never published as releases.

## Covered surface

The following are covered by the compatibility guarantee:

| Surface | Guarantee |
|---|---|
| Maven coordinates | `com.psiwray.organization:terminal-window` remains the artifact for the entire 2.x line. |
| Package names | `com.psiwray.organization.terminal_window` and its public subpackages `.font`, `.input` keep their names and package placement. |
| Public Kotlin declarations | Classes, interfaces, functions, properties, and their signatures, including nullability, default-argument presence, type parameters, and receiver types. |
| Kotlin metadata | The compiled `@Metadata` is part of the ABI. A change that is JVM-compatible but metadata-incompatible is treated as a breaking change. |
| JVM signatures | Descriptors, checked exceptions, and static/instance shape for Java callers. |
| Manifest | `Automatic-Module-Name: com.psiwray.organization.terminal_window` is stable. |
| Threading contract | Which thread may call which API, as defined by ADR-0004. |
| GL requirements | OpenGL 3.3 core minimum, GLSL 330, one context per window, no blending or depth in the library draw path. |
| Events | Event types, their fields, and their delivery ordering within a frame, per window. Cross-window ordering is not defined. |
| Configuration | Meaning and validation rules of `TerminalWindowConfig` fields. |

`internal` declarations are not covered. They may be renamed, moved, removed,
or changed in any release without notice.

## Excluded surface

These are not compatibility guarantees:

- **Rendered pixels.** Driver, GPU, font, and atlas differences may change
  output. Glyph shapes may be corrected in a patch release.
- **Timing and frame rate.** Frame pacing, vsync behaviour, and latency may
  change between releases.
- **Diagnostic text.** Exception messages and log text may be reworded.
- **Performance.** Throughput and allocation counts may change, provided the
  documented frame contract (one upload, one draw, no steady-state allocation)
  still holds.
- **Native dependencies.** LWJGL native classifiers are supplied by the
  consumer; the published POM stays platform-neutral for 2.x.
- **Build profile.** Debug builds are development artifacts: they are never
  published, add fail-fast validation, and force X11 on Linux. Their behaviour
  is not part of the 2.x guarantee; published artifacts are always release
  builds.
- **JVM launcher flags.** The POM carries none; project tasks and documentation
  supply the per-OS set. Changing a flag is not a library API change.
- **Behaviour of unsupported configurations.** OpenGL 3.0, GLES, compatibility
  profiles, Android, and JVM versions below 25 are outside the contract.
- **Java source compatibility beyond the JVM surface.** Java interop is best
  effort (ADR-0003); Kotlin metadata is authoritative.

## Kotlin-specific rules

Kotlin's ABI is not just JVM descriptors. Within 2.x:

1. **Public data classes are frozen.** Adding a property changes the
   constructor, `copy`, and `componentN` descriptors. New state is expressed by
   a new type or in a major release.
2. **Default arguments are part of the ABI.** Adding a parameter, even with a
   default value, changes the synthetic `$default` method. Additions use a new
   overload or wait for a major.
3. **Nullability changes are breaking.** Changing `T` to `T?` or back breaks
   compiled Kotlin callers even when the JVM descriptor is unchanged.
4. **No public inline functions.** Inline bodies are copied into consumer
   bytecode, so a bug fix would not reach already-compiled consumers. If an
   inline function is ever published, its body is treated as a behavioural
   detail, not as a fixable implementation.
5. **Sealed hierarchies are closed.** Adding a subtype to a public sealed
   hierarchy breaks exhaustive `when` expressions in consumer source. New event
   kinds therefore require a new non-sealed interface or a major release.
6. **Value classes keep their underlying type.** `Modifiers` is a value class
   over a bit mask; changing the mask width or underlying type is breaking.
7. **Deprecation is not removal.** A deprecated declaration stays binary
   compatible until the major release that removes it.

## Threading and GL contract

The following are API, not implementation details:

- GLFW initialization, window creation/destruction, and event polling occur on
  the process main thread (the service thread).
- GL calls occur on the owner thread with the window context current.
- Context ownership changes only through the explicit release/acquire calls.
- Window state setters from other threads are queued; they are not synchronous
  with the service thread.
- Input callbacks are delivered on the service thread and exposed as events on
  the owning window thread. Each window has its own bounded queue; an owner
  thread drains only its own queue, and a slow window does not delay another.
- One window per owner thread; multiple windows share the service thread's
  polling and registry.

A release may add runtime checks for violations. It may not move a call to a
different thread.

## JVM and native requirements

- **JVM:** target 25, class file version 69. Dropping the minimum JVM is a major
  change; raising it within 2.x is also a major change because it excludes
  consumers.
- **Dependencies:** Kotlin stdlib, LWJGL (`lwjgl`, `lwjgl-glfw`, `lwjgl-opengl` as
  `api`; `lwjgl-stb` at runtime), and JOML as `api`. Removing or replacing a
  public dependency is a major change.
- **Natives:** the consumer adds LWJGL natives for the target platform. A
  missing native produces a diagnostic at window open, not a linkage error at
  class load.
- **Module path:** the artifact is an automatic module. Adding a real
  `module-info.class` changes the module surface and is at least a minor change;
  restricting exports is a major change.

## Deprecation and removal

1. A declaration is deprecated with `@Deprecated` at `WARNING` level and a
   `ReplaceWith` expression for at least one minor release.
2. It may be raised to `ERROR` in a later minor, but remains present.
3. Removal happens only in the next major release.
4. Deprecation does not change the binary signature.

## Experimental APIs

Version 2.0 publishes no opt-in API. If a future release adds one, it is marked
with `@RequiresOptIn`, and its declarations are explicitly excluded from the
compatibility guarantee: they may change or disappear in a minor release.
Opt-in APIs are the only exception to the rules above.

## Relationship to version 1

- Version 2 is **not** source- or binary-compatible with `1.x`.
- Both versions use the same package root. A classpath containing both is
  unsupported; class loading order decides which implementation wins, silently.
- Version 1 artifacts remain available and remain GPL-3.0 (ADR-0002). Version 2
  is MIT.
- There is no migration guide, bridge artifact, or automatic conversion of
  version 1 code; consumers move directly to `2.0.0`.

## License consistency

Version 2, including this repository's build and documentation, is
MIT (ADR-0002). Published `1.x` artifacts are GPL-3.0. License terms are
not part of the compatibility guarantee, but the 2.x line does not change
license within the major version.

## Verification

The guarantee is enforced mechanically:

- `ApiSurfaceTest` inventories the compiled public packages and checks the
  intended public/internal boundary, Kotlin visibility, and absence of LWJGL
  types in public signatures.
- The check reads Kotlin reflection metadata, not only JVM descriptors, so
  nullability, default arguments, and data-class shape remain visible to
  reviewers; future API-dump tooling can compare a committed baseline.
- Thread-affinity tests exercise the service/owner boundary.
- The GL floor is verified in the headless harness before a release.

The API dump and its tooling are owned by the quality-gate section; this
document defines the policy they enforce.

## Related

[Public API](public-api.md) · [Threading](threading.md) ·
[Dependencies](dependencies.md)
