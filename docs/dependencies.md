# Dependencies and native runtime

`terminal-window` exposes LWJGL (`lwjgl`, `lwjgl-glfw`, `lwjgl-opengl`) and JOML
as API dependencies. LWJGL's `lwjgl-stb` is a runtime dependency used for
image decoding by `CursorImage.fromPath` and `WindowIcon.fromPath`. The Kotlin
stdlib is a runtime dependency added by the Kotlin Gradle plugin. Consumers do
**not** declare JOML, LWJGL, `lwjgl-stb`, or the Kotlin stdlib separately.

Native LWJGL jars are host-specific. They are runtime-only and are never part
of the published POM: add exactly one classifier per LWJGL module for the host
that will run the program. Runs on a different host need a different set.

## Coordinates

| Item | Value |
|---|---|
| Library | `com.psiwray.organization:terminal-window:2.0.0` |
| LWJGL | `3.4.3` |
| stb_image | `lwjgl-stb:3.4.3` (runtime, transitive) |
| JOML | `1.10.9` (transitive) |
| JVM | 25 |

## Gradle

Kotlin DSL; Groovy uses the same strings.

```kotlin
val lwjglVersion = "3.4.3"
val lwjglNatives = "natives-linux" // see the classifier table

dependencies {
    implementation("com.psiwray.organization:terminal-window:2.0.0")

    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjglVersion:$lwjglNatives")
}
```

Do not use the LWJGL BOM: the four modules have no transitive dependencies,
and a platform BOM adds nothing to the build.

## Maven

```xml
<properties>
  <lwjgl.version>3.4.3</lwjgl.version>
  <lwjgl.natives>natives-linux</lwjgl.natives>
</properties>

<dependencies>
  <dependency>
    <groupId>com.psiwray.organization</groupId>
    <artifactId>terminal-window</artifactId>
    <version>2.0.0</version>
  </dependency>

  <dependency>
    <groupId>org.lwjgl</groupId>
    <artifactId>lwjgl</artifactId>
    <version>${lwjgl.version}</version>
    <classifier>${lwjgl.natives}</classifier>
    <scope>runtime</scope>
  </dependency>
  <dependency>
    <groupId>org.lwjgl</groupId>
    <artifactId>lwjgl-glfw</artifactId>
    <version>${lwjgl.version}</version>
    <classifier>${lwjgl.natives}</classifier>
    <scope>runtime</scope>
  </dependency>
  <dependency>
    <groupId>org.lwjgl</groupId>
    <artifactId>lwjgl-opengl</artifactId>
    <version>${lwjgl.version}</version>
    <classifier>${lwjgl.natives}</classifier>
    <scope>runtime</scope>
  </dependency>
  <dependency>
    <groupId>org.lwjgl</groupId>
    <artifactId>lwjgl-stb</artifactId>
    <version>${lwjgl.version}</version>
    <classifier>${lwjgl.natives}</classifier>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

JOML, `lwjgl-stb`, and the Kotlin stdlib arrive transitively from
`terminal-window`; do not add them explicitly. The `lwjgl-stb` native jar is
still host-specific and must be added as shown when using image decoding.

## Native classifiers

| Host | Classifier |
|---|---|
| Linux x86_64 | `natives-linux` |
| Linux aarch64 | `natives-linux-arm64` |
| Windows x86_64 | `natives-windows` |
| Windows aarch64 | `natives-windows-arm64` |
| macOS x86_64 | `natives-macos` |
| macOS aarch64 | `natives-macos-arm64` |

The x86_64 names have no architecture suffix. Copy the names above; do not
synthesize `natives-linux-x86_64` or `natives-macos-x86_64`.

## Runtime notes

- macOS requires `-XstartOnFirstThread` on the JVM command line.
- Add `--enable-native-access=ALL-UNNAMED` on JDK 25 to avoid native-access
  warnings; the library's launch helper adds it for managed runs.
- LWJGL extracts native jars to `java.io.tmpdir` at runtime. On a read-only or
  restricted container filesystem, point `-Djava.io.tmpdir` at a writable
  directory.
- The library never bundles natives; a fat jar must include the host's
  classifier jars explicitly.

## Related

[Public API](public-api.md) · [Threading](threading.md) ·
[Performance](performance.md) · [Architecture](architecture.md)
