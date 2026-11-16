# ADR-0009: Platform JVM launch flags

- **Status:** Accepted
- **Date:** 2026-11-15
- **Deciders:** project owner
- **Supersedes:** none

## Context

A JVM flag is launcher configuration, not library metadata. Maven Central cannot
deliver JVM flags to consumers: the POM records dependencies, not command
lines. Flags therefore belong to the project's own run, test, and example
launchers, plus a documented copy-paste block for consumers.

The platforms are not interchangeable:

- **macOS:** GLFW must create and drive the window on the process main thread.
  The JVM must be started with `-XstartOnFirstThread`, otherwise the AppKit main
  thread is not the JVM main thread and window creation fails or the process
  aborts. This is a correctness requirement, not a tuning knob.
- **Linux:** X11 versus Wayland selection is already decided by the build
  profile (ADR-0008). JVM flags must not duplicate or override that decision.
- **Windows:** no thread-start flag is required; GLFW creates the window on the
  service thread as usual.

Two JVM-wide concerns apply everywhere:

- JDK 25 restricts native access (JEP 472). LWJGL loads natives and makes
  downcalls; `--enable-native-access=ALL-UNNAMED` keeps classpath launches free
  of restricted-method warnings and is forward-compatible with enforcement.
- The library uses no AWT. `-Djava.awt.headless=true` prevents any AWT display
  initialization from an indirect dependency, which would otherwise open a
  second UI toolkit connection for no benefit.

Performance flags are not self-evidently useful here. A terminal frame allocates
nothing in steady state (invariant 3), so GC choice and heap sizing have little
effect on frame time. Adding `-XX:+UseZGC`, `-Xms`, or `-Xmx` by default would be
cargo cult without measurements from the performance section.

## Decision

We will define one source of truth in build logic:

```text
launchJvmArgs(profile, osName, createsWindow) -> List<String>
```

Every project-owned launcher (`run`, examples, windowed integration tests)
obtains its JVM arguments from this function. No task adds platform flags ad
hoc. Headless unit-test tasks request `createsWindow = false` and therefore omit
the macOS first-thread flag; they still get the baseline and profile flags.

### Baseline, all platforms

| Flag | Reason |
|---|---|
| `-Djava.awt.headless=true` | No AWT; avoid a second UI toolkit connection. |
| `--enable-native-access=ALL-UNNAMED` | LWJGL native calls under JDK 25 native-access policy. |

### Platform-specific

| Platform | Additional flag | Reason |
|---|---|---|
| macOS (`os.name` starts with `Mac`) | `-XstartOnFirstThread` when `createsWindow` | GLFW's main-thread rule; without it window creation fails. |
| Linux | none | The X11 hint is programmatic (ADR-0008), not a JVM flag. |
| Windows | none | No first-thread requirement. |

`-XstartOnFirstThread` is never passed on Linux or Windows; it is a macOS-only
JVM option and the launcher must not carry it elsewhere.

### Profile-specific

| Profile | Flags for project launchers |
|---|---|
| `debug` | `-Dorg.lwjgl.util.Debug=true` (checks stay enabled) |
| `release` | `-Dorg.lwjgl.util.NoChecks=true` |

These are additive to the baseline and platform rows. They are set on the
project's own JVMs only; the library never mutates `Configuration` or system
properties at runtime (ADR-0008).

### Performance flags

- No GC selection, heap pinning, JIT tier, or direct-memory limit is enabled by
  default.
- Examples may set a fixed heap for reproducibility, and that value is local to
  the example, not a library recommendation.
- A performance flag is added only when the performance section records a
  measured frame-time or allocation win on the affected platform. The flag and
  its evidence live together in the build logic comment and in the performance
  report.
- Flags must be portable: a flag that exists on one JVM/OS but is rejected on
  another is not added to the baseline.

### Consumer contract

- The published POM carries no JVM flags. The README and examples document the
  baseline and the macOS flag as copy-paste text.
- Consumers on the module path replace `ALL-UNNAMED` with the module name they
  deploy; the library does not assume a module name at launch.
- Forgetting `-XstartOnFirstThread` on macOS is the expected support case; the
  documentation places it next to the first example, not in a footnote.

## Consequences

### Positive

- macOS launches are correct by construction for every project-owned task.
- Native-access warnings do not mask real diagnostics on JDK 25.
- One function decides flags, so a new task cannot silently omit the macOS flag
  or invent a conflicting GC setting.
- No speculative tuning is baked into examples where users would copy it.

### Negative

- Consumers launching the library themselves must read and apply the flag set;
  the build system cannot enforce it for them.
- The macOS flag is fatal if omitted, which is a sharp edge for first-time
  users.
- Platform-conditional build logic needs its own tests; a typo can silently drop
  a required flag on one OS.
- `ALL-UNNAMED` is wrong for strict module-path deployments; those consumers
  must adjust it.

### Neutral

- `-Djava.awt.headless=true` does not hide GLFW's macOS menu bar or dock icon;
  that is controlled by the GLFW Cocoa init hint, not by AWT.
- Headless unit tests need none of the platform flags; only tests that create a
  window need the macOS one.
- JVM flags are invisible to binary compatibility; they are excluded from
  `docs/compatibility.md`.

## Alternatives

| Alternative | Why rejected |
|---|---|
| Put flags in `gradle.properties` for every task | Wrong on macOS when absent, wrong elsewhere when present; hidden from task definitions. |
| Let each example task set its own flags | Drift and duplicated macOS logic; one forgotten flag breaks the example. |
| Set flags from library code | The JVM is already running; `-XstartOnFirstThread` cannot be applied at runtime. |
| Enable ZGC and pin the heap everywhere | Unmeasured on a zero-allocation workload; hides regressions and misleads consumers. |
| Always pass `-XstartOnFirstThread` | macOS-only option; rejected by the launcher on other platforms. |
| Ship a launcher script with flags baked in | Only covers one OS and one invocation style; the build still needs the same logic. |
| Omit `--enable-native-access` | JDK 25 warnings pollute output and future JDKs may reject the calls. |

## References

- JDK 25, JEP 472: Prepare to Restrict the Use of JNI.
- GLFW documentation: main-thread requirement for window creation.
- ADR-0004 for the service thread; ADR-0008 for profile flags and the Linux X11
  hint; ADR-0003 for the JDK 25 baseline.
