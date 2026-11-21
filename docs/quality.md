# Quality gates

`./gradlew check` is the local merge gate. It runs:

- Spotless with pinned ktlint 1.8.0 on the Kotlin sources and Gradle scripts
  under `library/`, `examples/`, and `benchmarks/`; formatting only.
- Kotlin compilation with `explicitApi()`, JVM target 25,
  `allWarningsAsErrors`, and `extraWarnings`.
- Tests, including the reflection-based public API surface audit.

Dokka is a separate documentation check, not part of `check`.

## Commands

| Command | Purpose |
|---|---|
| `./gradlew spotlessCheck` | Fail on formatting or ktlint lint violations. |
| `./gradlew spotlessApply` | Apply formatting before committing. |
| `bash ci/check-docs.sh` | Fail on a broken relative Markdown link or a README over 160 lines. |
| `./gradlew build` | Compile, run tests, and run `check`. |
| `./gradlew :library:dokkaGenerate` | Generate HTML and Javadoc under `library/build/dokka/`. |
| `./gradlew :library:javadocJar` | Build the Javadoc jar from Dokka output. |
| `LIBGL_ALWAYS_SOFTWARE=1 ./gradlew :benchmarks:run` | Manual offscreen frame benchmark; not a CI gate. |

`dokkaGenerate` writes `library/build/dokka/html` and
`library/build/dokka/javadoc`. `javadocJar` is intentionally not attached to
the publication yet; section 23 attaches it. `withJavadocJar()` is not used:
it jars an empty Java `javadoc` task for a Kotlin-only project.

## Compiler gate

The compiler is the strict gate: public declarations need explicit visibility
and return types, and any warning is an error. `extraWarnings` is enabled.
Unused local variables therefore fail the build.

Detekt 1.23.8 was evaluated and dropped. It rejects JVM target 25
(`Invalid value (25) ... --jvm-target`); with detekt's `jvmTarget` reduced to
22, its embedded IntelliJ `JavaVersion` parser fails on JDK 25 with
`IllegalArgumentException: 25.0.4` before analysis. This is the fallback called
out in section 04 step 2. No detekt plugin, config, or baseline ships. No
detekt alpha is pinned.

Consequence: unused private class members, complexity, and other detekt-only
rules are not machine-enforced today. Fixing unused locals and all compiler
warnings is still mandatory, and review covers the gap.

### Alternatives checked

- detekt 2.x has no stable release as of 2026-11; only alpha builds exist, so
  it is excluded.
- Diktat 2.0.0 is stable and runs on JDK 25. It reports empty blocks and
  excessive parameters, but its default ruleset is a full style/KDoc engine
  that overlaps ktlint; enabling only the detekt-equivalent rules requires a
  rule-by-rule configuration of the whole ruleset. It also has no
  unused-private-member rule. Not adopted.
- Ktlint 1.8.0 is already the JDK 25-compatible formatter/linter. There is no
  clean drop-in replacement for detekt's semantic rules, so no second analyzer
  is added.

## KDoc gate

Every public declaration gets KDoc with `@param`, `@return`, `@throws` where
applicable, and `@since 2.0.0`. Links must resolve. Dokka runs with
`reportUndocumented = true` and `failOnWarning = true`, so an undocumented
public declaration or broken reference fails `dokkaGenerate`.

## Suppression policy

- Fix the finding; do not suppress it to make a build green.
- `@Suppress("<rule-id>")` is placed at the narrowest declaration that needs
  it and includes a one-line reason plus an issue link in the commit message.
- `@file:Suppress` is reserved for generated files or rules that cannot be
  expressed at declaration scope, with the same justification.
- There is no lint baseline file. Findings are fixed, not frozen.
- Spotless overrides `ktlint_standard_package-name = disabled` because the
  fixed v2 package root `com.psiwray.organization.terminal_window` contains an
  underscore that the rule rejects.

## Formatting boundaries

Spotless targets only Kotlin and Gradle Kotlin scripts. `.twf` fixtures and
other data files are outside every Spotless target and must not be reformatted;
pixel rows and trailing spaces are data. A deliberate trailing-space `.twf`
probe kept an identical SHA-256 before and after `spotlessApply`.

## CI parity

CI logic lives in `ci/*.sh`; the GitHub workflow only selects a runner and calls
those scripts. `ci/build.sh` runs `ci/check-docs.sh` first, then
`spotlessCheck`, `test`, `:examples:build`, `:benchmarks:build`,
`dokkaGenerate`, and `publishToMavenLocal` without a display. The docs check
enforces the README line budget and resolves every relative Markdown link. The
benchmark is compiled but never run in CI; its absolute numbers are not a gate.
`ci/integration-test.sh` runs the
`integrationTest` task under Xvfb with software Mesa. There is no CI-only
formatting or analysis command.

`ci/install-gl-deps.sh` installs the apt packages needed by GLFW's X11 backend
and Mesa's software renderer; it is idempotent and uses `sudo` only when not
already root.

## CI required checks

GitHub Actions is the contributor gate:

- `build (ubuntu-latest)`
- `build (windows-latest)`
- `build (macos-latest)`
- `gl`

The three OS jobs run `ci/build.sh`. The `gl` job runs on `ubuntu-latest`,
installs the Xvfb/Mesa packages, and runs `ci/integration-test.sh`. GL tests
never run on the Windows or macOS hosted runners. Failed jobs upload
`**/build/reports/**` and `**/build/test-results/**` with 7-day retention.

The workflow uses `actions/checkout@v4` and `actions/upload-artifact@v4`.
Caching is provided by `gradle/actions/setup-gradle@v4`; never cache `build/`.

Branch protection on GitHub should require the four checks above.

## Related

[Testing](testing.md) · [Performance](performance.md) ·
[Dependencies](dependencies.md) · [Public API](public-api.md)
