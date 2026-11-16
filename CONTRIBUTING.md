# Contributing

`terminal-window` is a Kotlin/JVM library. Changes must preserve the public API,
the threading contract, and the OpenGL 3.3 core floor.

## Environment

- JDK 25, with `java` on `PATH`.
- The checked-in Gradle 9.9.1 wrapper: `./gradlew`.
- Linux integration tests need Xvfb, Mesa, and GLFW's X11 libraries. On
  Debian/Ubuntu, run `bash ci/install-gl-deps.sh`.
- macOS manual runs need `-XstartOnFirstThread`; the example tasks add it.
  JDK 25 also needs `--enable-native-access=ALL-UNNAMED` for clean native-access
  behavior.

## Local gates

Apply formatting, then run the merge gate:

```sh
./gradlew spotlessApply
./gradlew check
```

`check` compiles with `explicitApi()`, warnings-as-errors, and runs the unit
tests. It does not run GL integration tests or generate Dokka.

Run the integration suite under Xvfb:

```sh
xvfb-run -a -s "-screen 0 1280x1024x24" ./gradlew integrationTest
```

Run the complete unit and GL suites, including tests tagged `long`:

```sh
xvfb-run -a -s "-screen 0 1280x1024x24" ./gradlew fullTest
```

Run the same build steps as CI, including examples, benchmarks, Dokka, and the
local publication:

```sh
bash ci/build.sh
```

Check documentation links and the README line budget:

```sh
bash ci/check-docs.sh
```

`dokkaGenerate` fails on undocumented public declarations and broken KDoc. The
Javadoc jar is built from that output.

## Documentation

- Public declarations need KDoc with `@since 2.0.0`, and `@param`, `@return`, or
  `@throws` where applicable.
- Update the owning page under `docs/` rather than duplicating a contract in
  several files. Cross-link related pages.
- Add every user-visible change to `CHANGELOG.md` under `[Unreleased]`.
- Do not describe the library as Java-first, promise TTF/OTF support, or claim
  an OpenGL version below 3.3.
- Code blocks in the README must compile or be marked `text`. The quickstart is
  checked as `examples/.../ReadmeQuickstart.kt`.

## Tests and examples

- Add a regression test for a bug fix when the existing suite does not cover
  the path. Keep it deterministic and isolated; GL tests must be tagged `gl`.
- The examples must build without a display: `./gradlew :examples:build`.
- The benchmark must build without a display: `./gradlew :benchmarks:build`.
- Do not make CI depend on absolute performance numbers or a hardware vendor.

## Dependencies and compatibility

- Do not add a new dependency without an ADR under `docs/adr/`.
- Public API changes follow [`docs/compatibility.md`](docs/compatibility.md):
  additions are minor, removals are major, and data-class shapes are frozen.
- Never expose an LWJGL type, native handle, or GL name in a public signature.
- The library never calls `System.exit` and never installs global AWT or GLFW
  hooks for the caller.
- v1 and v2 share a package root; do not mix them on one classpath or module
  path.

## Commit style

Use a short imperative subject, for example `Add resize cursor fallback`. Keep
one logical change per commit. For a guide section, use the repository's existing
`Step NN ...` subject and leave the work uncommitted until the section is
reviewed. Explain non-obvious tradeoffs in the body; do not add generated
build output or native jars.

## Pull request checklist

- [ ] JDK 25 and the wrapper are used.
- [ ] `./gradlew spotlessApply check` passes.
- [ ] `xvfb-run ... ./gradlew integrationTest` or `fullTest` passes when GL,
      lifecycle, input, resize, or rendering changed.
- [ ] `bash ci/check-docs.sh` and `./gradlew :library:dokkaGenerate` pass when
      public API or documentation changed.
- [ ] New user-visible behavior is in `CHANGELOG.md` and the owning `docs/` page.
- [ ] New dependencies have an ADR; no native jars were committed.
- [ ] CI stays thin: shared logic is in `ci/*.sh`; the GitHub workflow only
      selects a runner and calls those scripts.
- [ ] The change has no hidden `System.exit`, global hook, or API leak.

## Continuous integration

GitHub Actions is the contributor gate. `.github/workflows/build.yml` runs
`ci/build.sh` on Ubuntu, Windows, and macOS, plus `ci/integration-test.sh` under
Xvfb on Ubuntu.

Keep build logic in `ci/*.sh`; the workflow only selects runners and calls those
scripts. Do not duplicate gates in YAML.

## License

Contributions are accepted under the MIT license used by v2. Do not copy
GPL-3.0 v1 code into the v2 tree unless the artifact is intentionally relicensed
as a whole; v1 published artifacts remain available under GPL-3.0.