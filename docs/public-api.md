# Public API and compatibility

This document is the consumer-facing contract for
`com.psiwray.organization:terminal-window`. The normative compatibility policy is
[compatibility.md](compatibility.md); the threading model is in
[threading.md](threading.md).

## Scope

The public surface is limited to three packages:

| Package | Public API |
|---|---|
| `com.psiwray.organization.terminal_window` | `TerminalWindow`, `TerminalWindowConfig`, `TerminalWindowService`, `GlfwOwnership`, `TerminalMatrix`, `RenderStats`, `ContextInfo`, `Colors`, `ResizePolicy`, `CursorShape`, `CursorMode`, `CursorImage`, `WindowIcon`, `GlException`, `GlfwException` |
| `...terminal_window.font` | `BitmapFont`, `FontFormatException` |
| `...terminal_window.input` | `InputEvent` and its subtypes, `InputListener`, `Key`, `MouseButton`, `Modifiers`, `KeyAction`, `CursorPosition` |

Everything in `...terminal_window.internal` is implementation. Font atlas
types are internal even though they share the public `.font` package.

`internal` is a Kotlin source boundary, not a security boundary. Internal
declarations are public bytecode and are exported by the automatic JPMS module;
their names and signatures are not compatibility promises. A mixed classpath or
module path containing v1 and v2 is unsupported: both use the same package root.

## Compatibility rules

The artifact follows Semantic Versioning 2.0.0:

- A breaking public API or contract change requires a major version.
- Additions are minor versions.
- Documentation, performance, and internal changes are patch versions.

The public Kotlin declarations, Kotlin metadata, JVM descriptors, manifest
module name, threading contract, event ordering within a window, configuration
validation, and GL requirements are covered. Rendered pixels, timing, exception
message text, native classifier selection, build-profile behavior, and JVM
launcher flags are excluded.

1. Public data classes are frozen: adding a property changes the constructor,
   `copy`, and `componentN` descriptors. Add a new type or wait for a major.
2. Default arguments are ABI. Adding a parameter changes the synthetic
   `$default` method.
3. Nullability is ABI. Do not change `T` to `T?` or back in a minor release.
4. Public data classes and sealed hierarchies are closed shapes. New event kinds
   require a new non-sealed interface or a major release.
5. `Modifiers` is a value class over an `Int` bit mask; changing its underlying
   type is breaking.
6. There are no public inline functions. An inline body would be copied into
   consumer bytecode and could not be fixed in a patch.
7. Deprecation precedes removal; removal happens only in a major release.
8. Version 2.0 has no opt-in API. If one is added, it is marked
   `@RequiresOptIn` and excluded from the 2.x guarantee.

## Thread affinity

"Any thread" means no synchronization is required by the library; it does not
mean the operation is synchronous with the service thread. Queued state setters
take effect at the next service-thread pump.

| Type / member | Thread | Notes |
|---|---|---|
| `Colors` object and `rgb` / `rgba` | Any | Pure ARGB helpers. |
| `ContextInfo` constructor, properties, `copy`, `componentN` | Any | Immutable value. |
| `GlException` / `GlfwException` constructors and properties | Any | Immutable exception payload. |
| `GlfwOwnership` values | Any | Initialization mode; the mode is validated at initialize time. |
| `RenderStats` constructor, properties, `copy`, `componentN` | Any | Immutable value. |
| `ResizePolicy` values | Any | Enum. |
| `TerminalMatrix` constructor | Any single thread | Pure data; a matrix attached to a window is owner-thread confined. |
| `TerminalMatrix.defaultForeground`, `defaultBackground`, `columns`, `rows`, `isDirty` | Owner | Read/mutate on the thread that owns the window. |
| `TerminalMatrix.codePointAt`, `foregroundAt`, `backgroundAt`, `set`, `fill`, `clear`, `write`, `resize` | Owner | Owner-thread matrix operations; no GL calls. |
| `TerminalWindow.config`, `font`, `contextInfo`, `isClosed`, `droppedEvents`, `shouldClose` | Any | Immutable or cached snapshots. |
| `TerminalWindow.matrix` (property access) | Any | Mutating the returned matrix is owner-thread confined. |
| `TerminalWindow.forceRepaint` get/set | Owner | Continuous render control read by the frame path. |
| `TerminalWindow.setTitle` | Any | Queued to the service thread. |
| `TerminalWindow.setCursor(CursorShape)` / `setCursor(CursorImage)` | Any | Queued to the service thread; native cursors are created or selected there. |
| `TerminalWindow.setCursorVisible` / `setCursorMode` | Any | Queued to the service thread; the mode controls GLFW cursor input state. |
| `TerminalWindow.setIcon` / `clearIcon` | Any | Queued to the service thread; the icon is copied to native storage there. |
| `CursorImage.of`, `CursorImage.fromPath`, `copyPixels` | Any | Immutable image construction and copy; `fromPath` uses stb_image. |
| `WindowIcon.of`, `WindowIcon.fromPath`, `copyPixels` | Any | Immutable icon construction and copy; `fromPath` uses stb_image. |
| `CursorShape`, `CursorMode` values | Any | Enums; GLFW codes are internal. |
| `TerminalWindow.addInputListener` / `removeInputListener` | Any | Listener callbacks themselves run on the owner thread. |
| `TerminalWindow.isKeyPressed`, `isMouseButtonPressed`, `cursorPosition` | Any | Read the latest service-thread snapshot; no GLFW calls. |
| `TerminalWindow.frame` | Service or owner | Calls GL and requires the window context current on the caller. In multi-threaded mode the owner thread calls it. |
| `TerminalWindow.renderStats` | Any | Returns the last immutable snapshot. |
| `TerminalWindow.requestClose` / `setCloseRequested` | Any | Queued to the service thread. |
| `TerminalWindow.setVisible` / `setResizable` | Any | Queued to the service thread. |
| `TerminalWindow.requestRepaint` | Any | Atomic one-shot request; never calls GL or GLFW. |
| `TerminalWindow.acquireContext` | New owner | Requires a released context; makes the calling thread owner. |
| `TerminalWindow.releaseContext` | Current owner | Releases the context on the caller. |
| `TerminalWindow.close` | Any | If the caller owns the context it is released; a context owned by another thread is an error. |
| `TerminalWindow.awaitClosed` | Any non-service | Blocks until native destruction; returns immediately on the service thread. |
| `TerminalWindowConfig` constructor, properties, `copy`, `componentN` | Any | Immutable value; validation is constructor-side. |
| `TerminalWindowService.initialize()` | Process main thread only | Captures the service thread and initializes GLFW for `OWNED`. |
| `TerminalWindowService.openWindow` | Service thread | Creates the window/context; the context starts current there. |
| `TerminalWindowService.pump()` / `pump(Duration)` | Service thread | Polls events and applies queued work. |
| `TerminalWindowService.runEventLoop` | Service thread | Pumps until the last registered window is destroyed. |
| `TerminalWindowService.close` | Service thread | Releases one reference; terminates owned GLFW at zero. |
| `BitmapFont` constructor, `cellWidth`, `cellHeight`, `fallbackCodePoint`, `glyphCount`, `codePoints`, `hasGlyph` | Any | Immutable font value. |
| `BitmapFont.parse(Path)`, `parse(InputStream)`, `parse(Reader)` | Any | Pure parsing; no GLFW or GL. |
| `FontFormatException` constructor and properties | Any | Parse diagnostics. |
| `InputEvent` and all event constructors, properties, `copy`, `componentN` | Any | Immutable values; delivery is owner-thread. |
| `InputListener.onKey`, `onChar`, `onCursorEnter`, `onMouseMove`, `onMouseButton`, `onScroll`, `onResize`, `onContentScale`, `onFocus`, `onCloseRequested` | Owner | Called by the library during owner-thread input drain. |
| `Key`, `KeyAction`, `MouseButton` values | Any | Enums; GLFW codes are internal. |
| `Modifiers` constructor, `bits`, `has`, companion constants | Any | Value class over an `Int` mask. |
| `CursorPosition` constructor, properties, `copy`, `componentN` | Any | Immutable value. |

The service thread is the process main thread. In single-threaded mode it is
also the window owner. In multi-threaded mode, context transfer is explicit:
release on the old owner, then acquire on the new owner. The library never makes
a context current on two threads.

## Nullability

Kotlin non-null is the default. `require` / `requireNotNull` validate values that
Java callers can supply as null; library code contains no `!!`. The only public
nullable return in 2.0 is `BitmapFont.fallbackCodePoint: Int?`.

## Exceptions

Only unchecked exceptions cross the public boundary, plus checked `IOException`
where declared:

| Exception | Meaning |
|---|---|
| `IllegalArgumentException` | Invalid constructor/configuration value, matrix bounds, resize dimensions, event values, or an invalid GLFW modifier/action input. |
| `FontFormatException` | `IllegalArgumentException` subtype carrying `TW-001`...`TW-011`, path, line, and Unicode-scalar column. |
| `IllegalStateException` | Wrong thread, closed service/window, context ownership conflict, or invalid lifecycle transition. |
| `GlException` | GL environment/context failure after GLFW succeeded, such as a missing 3.3 context, shader failure, or GL resource failure. |
| `GlfwException` | Checked GLFW error copied immediately with `glfwGetError`; `description` is safe to retain. |
| `IOException` | `BitmapFont.parse` stream/path read failure or image decode failure from `CursorImage.fromPath` / `WindowIcon.fromPath`, declared with `@Throws` for Java callers. |

Listener exceptions propagate out of `TerminalWindow.frame()` and abort that
frame before renderer calls. The library never swallows them. A one-shot repaint
request consumed before a failed sink is restored for the next tick.

## Java interop

Java support is best-effort; Kotlin metadata is authoritative.

- `@JvmStatic` is present on companion entry points (`Colors`, `BitmapFont.parse`,
  `TerminalWindowService.initialize`). `@JvmOverloads` is used only on
  `TerminalWindowService.initialize`; other APIs avoid overload ambiguity.
- Default arguments generate a synthetic `$default` method. Java callers should
  pass all arguments (or use a Kotlin call site) rather than call `$default`.
- Data classes expose `copy` and `componentN`; these are public ABI, not
  implementation details. Adding or removing a component is breaking.
- Enums expose their enum entries and JVM value shape. Internal GLFW code fields
  are not public API.
- Kotlin `internal` names are mangled with the module suffix in bytecode. Do not
  call them from Java; they are not stable.
- The public signatures do not expose `org.lwjgl.*` types, GLFW handles, or GL
  names. `ApiSurfaceTest` enforces this on the compiled test classpath.

## Module identity

The JAR manifest carries:

```text
Automatic-Module-Name: com.psiwray.organization.terminal_window
```

No `module-info.class` is shipped in 2.0. On the module path this is an
automatic module: it exports every package, including `.internal`, and reads
every module. Consumers must declare their LWJGL/JOML modules or use the
classpath. A future real `module-info` that restricts exports is a compatibility
change.

For source and binary compatibility, keep using the same package root; v1 and
v2 must not coexist on one classpath or module path.

## Documentation and verification

`dokkaGenerate` builds HTML and Javadoc output for public declarations. The
Javadoc output is the Central `-javadoc.jar`. `ApiSurfaceTest` checks the
public type inventory, Kotlin visibility, and absence of `org.lwjgl` types in
public signatures. The manifest module name is checked with
`jar --describe-module` on the built JAR.

## Related

[Threading](threading.md) · [Frame contract](frame.md) ·
[Resize and DPI](resize.md) · [Performance](performance.md) ·
[Dependencies](dependencies.md) · [Font format](font-format.md) ·
[Input](input.md) · [Cursor customization](cursor.md) ·
[Window icons](window-icon.md)