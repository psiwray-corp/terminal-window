# 05 — CI pipeline (GitHub Actions + Forgejo Actions)

**Phase:** foundations · **Depends on:** 04 · **Blocks:** 06, 24

## Goal

Every push proves the same gates on both forges: Kotlin compiles on JDK 25, tests pass, formatting/lint clean, Dokka builds, and a real GL context works under Xvfb. GitHub runs a 3-OS matrix; the self-hosted Forgejo instance runs on its `debian` runner.

## Deliverables

- `ci/build.sh` — format/lint/test/Dokka/local-publish gates (no display required).
- `ci/integration-test.sh` — Xvfb + Mesa GL tests.
- `ci/install-gl-deps.sh` — apt packages for GLFW/Xvfb (idempotent, `sudo` only if needed).
- `ci/check-jdk.sh` — asserts the active JDK is 25.
- `.github/workflows/build.yml`
- `.forgejo/workflows/build.yml`
- `docs/quality.md` — required checks, runner labels, local parity.
- Optional `.github/workflows/codeql.yml` (GitHub only; Forgejo gets no CodeQL).

## Steps

1. **Shared scripts first.** Bash, `set -euo pipefail`, invoked from both forges. `ci/build.sh` runs `./gradlew --no-daemon --stacktrace spotlessCheck test dokkaGenerate publishToMavenLocal` (append `detekt` only if 04 confirmed it on Kotlin 2.4). `ci/integration-test.sh` runs `xvfb-run -a -s "-screen 0 1280x1024x24" ./gradlew --no-daemon --stacktrace integrationTest` with `LIBGL_ALWAYS_SOFTWARE=1`; it fails with a clear message if `xvfb-run` is missing.
2. **JDK.** GitHub: `actions/setup-java@v4` with temurin 25. Forgejo: the `debian` runner must already have JDK 25; `ci/check-jdk.sh` reads `java -version` and fails unless the major is 25. Do not rely on `actions/setup-java` on Forgejo unless the instance has GitHub-action proxying enabled.
3. **GitHub workflow.** Matrix `ubuntu-latest`, `windows-latest`, `macos-latest`: checkout, setup-java 25, `gradle/actions/setup-gradle@v4`, `bash ci/build.sh` (Git Bash on Windows; scripts are LF-only). Separate Linux `gl` job: `bash ci/install-gl-deps.sh` then `bash ci/integration-test.sh`. Upload `**/build/reports/**` and `**/build/test-results/**` on failure with `actions/upload-artifact@v4`; 7-day retention. `permissions: contents: read`; concurrency cancels superseded runs.
4. **Forgejo workflow.** `runs-on: debian`; `on: [push, pull_request]`; steps: checkout, `bash ci/check-jdk.sh`, `bash ci/install-gl-deps.sh` (guarded by `if command -v apt-get`), `bash ci/build.sh`, `bash ci/integration-test.sh`. Set `shell: bash` on steps that use bashisms.
5. **Action availability.** Forgejo Actions is GitHub-compatible for `uses:`, but a self-hosted instance may have no GitHub proxy. Use `actions/checkout@v4` in both; if the Forgejo instance cannot fetch it, replace only that step with `git clone --depth 1 "${{ github.server_url }}/${{ github.repository }}.git" .` (Forgejo provides GitHub-compatible contexts). Document the mirror option.
6. **Caching.** GitHub: `setup-gradle`. Forgejo: optional `actions/cache@v4` on `~/.gradle` and `~/.kotlin`, or rely on a persistent runner workspace; do not cache `build/`.
7. **Artifacts.** GitHub uploads on failure; Forgejo either uses `actions/upload-artifact@v4` (if available) or leaves `build/reports/` for the runner operator. Document which one applies.
8. **Required checks.** GitHub: three OS jobs + GL job. Forgejo: the single `build` job. Record both in `docs/quality.md`.
9. **No auto-fix in CI.** Never run `spotlessApply` or `detekt --auto-correct` in either workflow.

## Acceptance

- [x] `bash ci/build.sh` and `bash ci/integration-test.sh` pass locally on Linux; the first also passes without a display.
- [x] `.github/workflows/build.yml` runs the 3-OS matrix and the Linux GL job.
- [x] `.forgejo/workflows/build.yml` uses `runs-on: debian`, checks JDK 25, and runs both scripts.
- [x] A PR with an unformatted `.kt` fails on both forges.
- [x] A failing test uploads/reports artifacts on both forges.
- [x] `docs/quality.md` lists both forges' required checks and the `debian` label requirement.
- [x] `./gradlew build` locally still does not require a display.

## Pitfalls

- macOS/Windows hosted runners have no reliable GL context; never put GL integration tests there. The Forgejo `debian` runner is Linux and can run them.
- Xvfb alone is not enough: Mesa DRI must be installed or GLFW reports "GLX: Failed to create context".
- `xvfb-run -a` avoids display-number collisions; hardcoding `:99` is flaky.
- Forgejo runners often run jobs as an unprivileged user; `install-gl-deps.sh` must use `sudo` only when present and must be idempotent.
- Forgejo label names are configured on the runner; `debian` must exist before the workflow is enabled.
- Do not copy the GitHub matrix into Forgejo: there is one runner label, so one job; duplicate work is wasted.
- Secrets are per-forge; the same names must be configured on both if both can release (section 24).

## Plan seed

> Plan section 05 only: create shared `ci/*.sh` gates and two thin workflows — GitHub Actions (3-OS matrix + Linux GL job) and Forgejo Actions (`runs-on: debian`, JDK 25, GL tests) — with caching/artifact notes and required-check documentation. No publishing.