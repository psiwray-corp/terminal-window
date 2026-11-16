# 04 — Quality gates

**Phase:** foundations · **Depends on:** 03 · **Blocks:** 05

## Goal

One command enforces formatting, static analysis, compiler warnings, and API documentation. CI cannot merge unformatted or warning-producing Kotlin.

## Deliverables

- `library/build.gradle.kts`: Spotless + ktlint, Dokka (HTML + Javadoc), strict Kotlin compiler settings.
- `gradle/libs.versions.toml`: plugin versions.
- `docs/quality.md`: local commands, suppression policy, CI parity, detekt fallback decision.

## Steps

1. **Spotless 8.11.0 + ktlint 1.8.0.** Apply to `src/**/*.kt`, plus `kotlinGradle` for `*.gradle.kts`; `trimTrailingWhitespace()`, `endWithNewline()`. Pin ktlint explicitly (Spotless's default drifts). Spotless wires `spotlessCheck` into `check`; verify.
2. **detekt 1.23.8 (verify before depending on it).** `buildUponDefaultConfig = true`, `parallel = true`, `autoCorrect = false`; config in `config/detekt/detekt.yml`. Wire `detekt` (and `detektTest`) into `check`. No baseline file — fix findings instead of freezing them. detekt 1.23.8 predates Kotlin 2.4; run it once and confirm it parses the sources and stdlib metadata. If it fails on Kotlin 2.4 metadata, drop detekt and rely on ktlint + `allWarningsAsErrors` (do not pin a detekt alpha).

   **Outcome:** dropped. detekt 1.23.8 rejects JVM target 25; with its `jvmTarget` reduced to 22, its embedded IntelliJ `JavaVersion` parser fails on JDK 25 (`IllegalArgumentException: 25.0.4`) before analysis. No detekt plugin, config, or baseline ships. See `docs/quality.md`.
3. **Kotlin compiler.** `explicitApi()` + `allWarningsAsErrors = true` + `jvmTarget = JVM_25`, already in 02; add `extraWarnings = true` if the first run is clean (it surfaces extra checks). Do not enable `-progressive` unless the whole build is clean.
4. **Dokka 2.3.0.** Apply both `org.jetbrains.dokka` and `org.jetbrains.dokka-javadoc`; `dokkaGenerate` builds HTML and Javadoc output. Add a `javadocJar` `Jar` task from `dokkaGeneratePublicationJavadoc` (attached to the publication in 23).
5. **KDoc rules.** Every public declaration documented; `@param`, `@return`, `@throws`, `@since 2.0.0`; no broken `[links]`. Dokka fails on unresolved references in strict mode if configured (`failOnWarning`).
6. **Suppression policy.** `@Suppress("...")` requires the rule id, a one-line reason, and an issue link in the commit message, and must be at the narrowest declaration. detekt is not present; its suppression rules do not apply.
7. **Baseline run.** Run `spotlessApply` before the first production file exists, so later diffs are formatting-neutral.
8. **Hygiene.** `./gradlew check` runs spotlessCheck + tests. `dokkaGenerate` stays a separate CI step (it is documentation, not correctness).

## Acceptance

- [x] `./gradlew spotlessCheck` fails on a deliberately misformatted `.kt` and passes after `spotlessApply`.
- [x] detekt 1.23.8 was evaluated and dropped per step 2 (JDK 25 incompatibility); no detekt task ships.
- [x] `./gradlew compileKotlin` fails on an unused variable and on a missing explicit visibility/return type in public API (`explicitApi`). Unused private class members are not a Kotlin compiler diagnostic; detekt was dropped.
- [x] `./gradlew dokkaGenerate` produces HTML and Javadoc; `javadocJar` contains the Javadoc HTML.
- [x] `./gradlew build` green on a clean clone; second run is configuration-cache up-to-date.
- [x] `.twf` fixtures are untouched by Spotless.

## Pitfalls

- Ktlint owns formatting and its standard lint rules. Do not re-add detekt until a JDK 25-compatible release exists; 1.23.8 fails before analysis.
- `allWarningsAsErrors` turns deprecation warnings into build failures; fix or suppress with justification.
- Dokka 2.x task names are `dokkaGeneratePublicationHtml` / `dokkaGeneratePublicationJavadoc`; `dokkaHtml` is the old 1.x name.
- `withJavadocJar()` does not work for a Kotlin-only project (it jars an empty `javadoc` task); build the jar from Dokka.
- Do not let Spotless reformat `.editorconfig`-sensitive fixtures or `.twf` pixel rows.

## Plan seed

> Plan section 04 only: add Spotless+ktlint 1.8.0, strict Kotlin compiler settings, and Dokka 2.3.0 (HTML + Javadoc + `javadocJar`) to `:library`; evaluate detekt 1.23.8 and drop it if incompatible; prove each shipped gate fails on a deliberate violation. No production code.