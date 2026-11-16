# ADR-0001: Artifact identity and coordinates

- **Status:** Accepted
- **Date:** 2026-11-15
- **Deciders:** project owner
- **Supersedes:** none

## Context

Version 1 of this library was published to Maven Central as
`com.psiwray.organization:terminal-window`. Its last release is `1.4.1`
(2021-10-24); nine versions exist in total. Central versions are immutable:
neither `1.4.1` nor any earlier artifact can be deleted, edited, or redirected,
and a broken release can only be followed by a new version.

Version 1 targets Java 11, LWJGL 3.2.0, JOML 1.10.2, and the package root
`com.psiwray.organization.terminal_window`. Its API and threading assumptions
are not compatible with the version 2 design.

The version 2 work is a new implementation. It is not a source-compatible
continuation of the version 1 codebase, but it is a continuation of the same
artifact so that consumers have one coordinate to depend on.

## Decision

We will publish version 2 under the existing coordinates:

| Item | Value |
|---|---|
| Group | `com.psiwray.organization` |
| Artifact | `terminal-window` |
| Release version | `2.0.0` |
| Development version | `2.0.0-SNAPSHOT` |
| Repository | `psiwray-corp/terminal-window` on GitHub |
| Package root | `com.psiwray.organization.terminal_window` |
| Subpackages | `.font`, `.input`, `.internal` |
| Automatic module name | `com.psiwray.organization.terminal_window` (ADR-0007) |
| Language / target | Kotlin 2.4.21, JVM target 25 (ADR-0003) |

The package root is deliberately identical to version 1. A classpath that
contains both `terminal-window:1.x` and `terminal-window:2.x` is unsupported:
the JVM will load classes from whichever artifact appears first, with no error.
This is stated in the README and in `docs/compatibility.md`.

The GitHub repository is the source of truth for the build, CI, and release
workflows. Maven Central remains the only distribution repository for
consumers.

### Recorded version 1 facts

These facts are fixed history and are not re-derived in later sections:

| Fact | Value |
|---|---|
| Last version | `1.4.1`, published 2021-10-24 |
| Published versions | 9 |
| Java baseline | Java 11 |
| LWJGL | 3.2.0 |
| JOML | 1.10.2 |
| License | GPL-3.0 (published artifacts remain GPL-3.0; see ADR-0002) |
| Package root | `com.psiwray.organization.terminal_window` |

## Consequences

### Positive

- Existing consumers keep one coordinate; a plain version bump to `2.0.0`
  is the only dependency change.
- Maven Central metadata, group ownership, and search results continue to
  point at the same artifact.
- Version 1 remains downloadable and its documentation remains accurate for
  its own version range.

### Negative

- Both major versions occupy the same package root, so mixing them is a silent
  classpath hazard rather than a compile-time conflict.
- A consumer using an open version range such as `[1.0,3.0)` will resolve
  `2.0.0` and must then satisfy the JVM 25 and OpenGL 3.3 requirements.
- The artifact name gives no hint of the major-version break; consumers move
  directly to `2.0.0`, with no migration guide.

### Neutral

- Version 1 cannot be removed from Central, deprecated at the repository level,
  or made to resolve to version 2. Version 2 is published as an additional
  version.
- GitHub repository creation is part of section 02; this record only fixes the
  intended name and owner.

## Alternatives

| Alternative | Why rejected |
|---|---|
| New artifact ID (`terminal-window2`) | Splits the dependency, abandons existing Central metadata, and forces every consumer to edit both group and artifact. |
| New group ID | Same split, plus a second group ownership claim and signing setup. |
| New package root (`...terminalwindow.v2`) | Would allow both versions on one classpath, but breaks the explicit requirement that version 2 keep the version 1 root, and doubles porting work for no functional gain. |
| Keep version 1 and evolve it | Version 1's API, threading model, and GL baseline are exactly what version 2 replaces. |

## References

- Maven Central: `com.psiwray.organization:terminal-window` (versions immutable).
- `docs/compatibility.md` in this repository.
