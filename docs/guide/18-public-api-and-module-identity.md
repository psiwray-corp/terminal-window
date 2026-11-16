# 18 — Public API and module identity

**Phase:** api · **Depends on:** 16 · **Blocks:** 19, 21, 22

## Goal

Freeze a small, documented, LWJGL-free Kotlin API and give the JAR a stable module name. Java interop is best-effort; the source of truth is Kotlin.

## Deliverables

- `library/build.gradle.kts`: `explicitApi()`, `Automatic-Module-Name` manifest attribute.
- `package-info` is not used in Kotlin; write package-level KDoc in each package's primary file or a `package.md` for Dokka.
- KDoc pass over every public type (`@since 2.0.0`, `@throws`, thread notes).
- `docs/public-api.md`: compatibility rules, thread affinity table, exception policy, Java interop notes.
- `library/src/test/kotlin/.../ApiSurfaceTest.kt` (reflection/metadata audit).

## Package map

| Package | Contents | Public |
|---|---|---|
| `com.psiwray.organization.terminal_window` | `TerminalWindow`, `TerminalWindowConfig`, `TerminalWindowService`, `TerminalMatrix`, `RenderStats`, `ContextInfo`, `CursorShape`, `CursorMode`, `CursorImage`, `WindowIcon`, `Colors`, `ResizePolicy`, `GlException`, `GlfwOwnership` | yes |
| `com.psiwray.organization.terminal_window.font` | `BitmapFont`, `FontFormatException` | yes |
| `com.psiwray.organization.terminal_window.input` | `InputListener`, `InputEvent` + subtypes, `Key`, `MouseButton`, `Modifiers`, `KeyAction`, `CursorPosition` | yes |
| `com.psiwray.organization.terminal_window.internal` | renderer, pipeline, queues, GL wrappers | `internal` |

## Steps

1. **Visibility.** `explicitApi()` (strict) plus Kotlin `internal` for implementation. Do not rely on `internal` for security or for bytecode hiding: internal classes are still visible to reflection and to Java callers.
2. **Module name.** Set the manifest attribute:
   ```kotlin
   tasks.jar {
       manifest { attributes("Automatic-Module-Name" to "com.psiwray.organization.terminal_window") }
   }
   ```
   No `module-info.java` in 2.0: Kotlin does not emit it, and mixed Java/Kotlin module compilation would split the source set. An automatic module reads and exports everything, including `.internal`; document that caveat. Revisit full JPMS when the toolchain makes it painless.
3. **No LWJGL leakage.** `ApiSurfaceTest`: enumerate public classes (Kotlin reflection or `kotlinx-metadata-jvm`), reject public members whose parameter/return types start with `org.lwjgl`; also reject `internal` declarations in the public list. `testImplementation(kotlin("reflect"))` is enough for visibility.
4. **Nullability.** Kotlin non-null by default; `requireNotNull`/`require` for Java-supplied values that can be null at the boundary. Public nullable returns are limited to `BitmapFont.fallbackCodePoint: Int?`. No `!!`.
5. **Exceptions.** Unchecked only: `IllegalArgumentException` (bad values), `IllegalStateException` (wrong thread, closed window), `GlException`, `FontFormatException`. Add `@Throws(IOException::class)` to parsing methods for Java callers. Document that listener exceptions propagate.
6. **Java interop.** `@JvmStatic` on companion/object entry points; `@JvmOverloads` only where it does not create overload ambiguity; `@JvmName` when a Kotlin name collides. Data classes expose `copy`/`componentN`; that is public API and part of the compatibility contract.
7. **Thread affinity table.** For each public method: service thread, owner thread, or any thread. Verify against 11/12 and repeat the short version in `TerminalWindow` KDoc.
8. **KDoc.** `@since 2.0.0` everywhere; no `@author`; code samples use fenced blocks in KDoc. Dokka's `documentedVisibilities` set to `PUBLIC`; the Javadoc-format output is the Central `-javadoc.jar`.
9. **Audits.** `jar --describe-module --file build/libs/terminal-window-*.jar` prints `com.psiwray.organization.terminal_window` (automatic). `jdeps --multi-release 25 --check` succeeds. A tiny Kotlin consumer resolves via `includeBuild` or the local repo.

## Acceptance

- [x] `jar --describe-module` shows the automatic module name `com.psiwray.organization.terminal_window`.
- [x] The API audit finds zero `org.lwjgl*` types in public signatures and zero `internal` declarations in the public surface.
- [x] `dokkaGenerate` succeeds; the javadoc output documents every public type.
- [x] A Kotlin consumer resolves the local publication (POM + JOML + LWJGL) and runs with only the LWJGL natives added at runtime.
- [x] Every public method appears in the thread-affinity table.
- [x] `explicitApi()` is enabled and the build fails on a missing visibility/return type.

## Pitfalls

- Automatic modules export all packages; `.internal` is not hidden from JPMS consumers. Say so in the KDoc.
- `data class` `copy()` and `componentN()` are public API; adding/removing a component is a breaking change.
- Kotlin default arguments generate synthetic `$default` methods; Java callers need `@JvmOverloads` or the full constructor.
- `internal` member names are mangled (`foo$module_name`), which is not a compatibility promise.
- Do not add `kotlin-reflect` to the runtime classpath; it is test-only.
- Do not add JPMS `requires` lines "for later"; a half-configured module system is worse than none.

## Plan seed

> Plan section 18 only: finalize the Kotlin public API (packages above), enable `explicitApi`, set `Automatic-Module-Name`, write KDoc, add the API surface and module audits, and document the thread-affinity table and Java interop limits. No new features.