# 02 — Repository bootstrap

**Phase:** foundations · **Depends on:** 01 · **Blocks:** 03

## Goal

A clone-and-build skeleton: Gradle 9.9.1 wrapper, Kotlin JVM modules, version catalog, repo hygiene files. `./gradlew build` must pass with zero production classes.

## Deliverables

- `.git/` history (create or reuse the GitHub repo; tag v1 before rewriting).
- `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (pinned + `distributionSha256Sum`).
- `settings.gradle.kts`, `build.gradle.kts` (root), `library/build.gradle.kts`, `examples/build.gradle.kts`.
- `gradle/libs.versions.toml`.
- `gradle.properties` (`group`, `version=2.0.0-SNAPSHOT`, caching/parallel/configuration-cache).
- `.gitignore`, `.gitattributes`, `.editorconfig`, `LICENSE`, `README.md` (stub with v1 notice), `CHANGELOG.md`.

## Steps

1. **History.** If the GitHub repo exists, clone it, confirm v1.4.1 is reachable, and create branch `legacy` + signed tag `v1.4.1-legacy` at the last v1 commit. Keep `master` for v2. If no repo exists, `git init` and plan the first commit as the skeleton.
2. **Wrapper.** Use a Gradle 9.9.1 distribution once (`gradle wrapper --gradle-version 9.9.1 --distribution-type bin`); verify the sha256 checksum in `gradle-wrapper.properties`. Commit executable bits.
3. **Settings.** `rootProject.name = "terminal-window"`; `include(":library", ":examples")`; `dependencyResolutionManagement { repositories { mavenCentral() } }`; `pluginManagement` includes `gradlePluginPortal()`; optionally `id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"` so contributors on older JDKs provision 25.
4. **Kotlin plugin.** `library/build.gradle.kts`:
   ```kotlin
   plugins {
       kotlin("jvm") version "2.4.21"
       `java-library`
       `maven-publish`          // publications configured in 23; the plugin makes publishToMavenLocal exist
   }
   kotlin {
     jvmToolchain(25)
     explicitApi()
     compilerOptions {
       jvmTarget.set(JvmTarget.JVM_25)
       allWarningsAsErrors.set(true)
     }
   }
   ```
   `:examples` applies the same Kotlin plugin plus `application`. Source roots become `src/main/kotlin` and `src/test/kotlin`.
5. **Catalog.** Every version and coordinate in `libs.versions.toml`: Kotlin, LWJGL, JOML, JUnit, Dokka, detekt, ktlint. No inline version strings in build scripts.
6. **Modules.** `:library` → `base { archivesName = "terminal-window" }`, `withSourcesJar()` (Javadoc jar comes from Dokka in 04/23). `:examples` applies `application` and depends on `project(":library")`; stays empty until 19.
7. **Hygiene.** `.gitattributes`: `* text=auto eol=lf`, `*.twf text eol=lf`, `*.png binary`, `*.jar binary`. `.editorconfig`: UTF-8, LF, 4 spaces, `ktlint_code_style = ktlint_official`, `max_line_length = 120`. `.gitignore`: `.gradle/`, `build/`, `out/`, `.idea/`, `*.iml`, `.DS_Store`.
8. **Docs stubs.** README states v1 is superseded by v2 and that there is no migration guide; CHANGELOG follows Keep a Changelog with an `Unreleased` heading.

## Acceptance

- [x] `./gradlew projects` lists `:`, `:library`, `:examples`.
- [x] `./gradlew build` succeeds on a clean clone; toolchain 25 is used (`./gradlew -q javaToolchains`).
- [x] `./gradlew compileKotlin` compiles a one-line `internal const val` placeholder (delete it afterward) with `explicitApi()`.
- [x] `git status` is clean after a build; no build output is tracked.
- [x] Wrapper checksum pinned; `./gradlew --version` reports 9.9.1 and runs on JDK 25.
- [x] `./gradlew :library:jar` produces `terminal-window-2.0.0-SNAPSHOT.jar`.

## Pitfalls

- Kotlin 2.4.x requires a recent Gradle; 9.9.1 is in the supported range. Do not drop to Gradle 8.x.
- `explicitApi()` fails on the placeholder until it has an explicit visibility; that is the point.
- `jvmToolchain(25)` and `jvmTarget = 25` must agree, otherwise KGP fails validation (`ERROR` by default).
- `JvmTarget` needs `import org.jetbrains.kotlin.gradle.dsl.JvmTarget` in `build.gradle.kts`; without it the compiler option does not resolve.
- Do not commit `.kotlin/` session caches or IDE metadata.

## Plan seed

> Plan section 02 only: bootstrap a Gradle 9.9.1 multi-project repo (`:library`, `:examples`) with Kotlin 2.4.21, JDK 25 toolchain, explicit API, version catalog, wrapper checksum, and hygiene files. Preserve v1 history on a `legacy` branch. No production code.