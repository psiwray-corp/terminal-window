# 24 — Release runbook

**Phase:** release · **Depends on:** 23 · **Blocks:** —

## Goal

A repeatable, auditable release with no manual steps that can be forgotten: tag → CI → signed artifacts → Central Portal → verification → announcement. Both GitHub Actions and Forgejo Actions must be able to run the release.

## Deliverables

- `ci/release.sh` — shared release gates and publish command.
- `.github/workflows/release.yml` (tag-triggered, `v*.*.*`).
- `.forgejo/workflows/release.yml` (tag-triggered, `runs-on: debian`).
- `docs/release/runbook.md` (this checklist, with exact commands).
- `docs/release/rollback.md`.
- A GitHub Release per version with jars, sources, Dokka javadoc, and the `CHANGELOG` section; a Forgejo release is optional (same artifacts).

## Steps

1. **Pre-flight (local, clean tree).**
   - `git status` clean; on `main`; up to date.
   - Version in `gradle.properties` is `2.0.0` (no `-SNAPSHOT`).
   - `CHANGELOG.md` has a `2.0.0` section with the date.
   - `./gradlew clean check` green; `xvfb-run -a ./gradlew integrationTest` green.
   - `./gradlew dokkaGenerate` green; the javadoc jar contains HTML.
   - `./gradlew publishAllPublicationsToLocalRepository -Prelease` and `gpg --verify` every `.asc`.
   - `README.md` install snippet matches the release version.
2. **Tag.** `git tag -s v2.0.0 -m "terminal-window 2.0.0"`; push the tag.
3. **Shared release script.** `ci/release.sh` verifies `git describe --tags --exact-match` equals `v$version` from `gradle.properties`, runs the gates, then `./gradlew --no-daemon publishAggregationToCentralPortal` with `USER_MANAGED`.
4. **GitHub workflow.** Tag trigger; checkout at the tag; JDK 25; `ci/release.sh`; create a draft GitHub Release with the jars from `build/repo` (or the publication outputs).
5. **Forgejo workflow.** Tag trigger; `runs-on: debian`; JDK 25 check; `ci/release.sh`; optional release via the Forgejo API or leave the Portal deployment for manual release. Same secret names (`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, `SIGNING_PASSWORD`).
6. **Portal release.** Review the validated deployment at `central.sonatype.com`; confirm coordinates and files; release it. Wait for `PUBLISHED`.
7. **Verify.** From a clean environment: a tiny Gradle consumer project resolves `com.psiwray.organization:terminal-window:2.0.0`; `repo1.maven.org` paths; the Central page shows `2.0.0`. `mvn dependency:get` is an optional cross-check, not a build step.
8. **Publish the GitHub Release** with the JARs, `CHANGELOG` excerpt, and a link to the Central page. Undraft only after step 7 passes.
9. **Post-release.** Update badges/version in README if pinned; move `[Unreleased]` in the changelog; announce.
10. **Rollback policy.** If validated but not released: discard the deployment. If already published: never re-upload; revert on `main`, bump to `2.0.1`, release. A GitHub/Forgejo tag or release may be deleted; a Central version cannot.

## Acceptance

- [ ] The runbook is executed once end to end for `2.0.0` with no undocumented step.
- [ ] Both workflows call the same `ci/release.sh`; neither duplicates the publish logic.
- [ ] CI fails if the tag version and `gradle.properties` version disagree.
- [ ] The GitHub Release contains the same bytes as Central (checksums match).
- [ ] `docs/release/rollback.md` names the exact commands for discard vs patch release.
- [ ] A second dry run for `2.0.1` on a throwaway tag is discarded cleanly on both forges.
- [ ] The released `-javadoc.jar` opens as HTML and matches the current API.

## Pitfalls

- Do not tag before the changelog/version commit is pushed; the workflow must build exactly the tagged tree.
- `USER_MANAGED` deployments expire; release before the Portal timeout or re-upload.
- Deleting a tag after publish does not delete the Central version.
- GitHub Actions secrets are not available to fork PRs; Forgejo secrets are per-repository and per-instance. Configure both before relying on either.
- A Forgejo runner without JDK 25 must fail fast in `ci/check-jdk.sh`, not publish with a 21 build.
- Do not let the GitHub workflow create a release before the Portal deployment is validated; order matters.

## Plan seed

> Plan section 24 only: write the shared `ci/release.sh` and tag-triggered release workflows for GitHub Actions and Forgejo Actions (`debian`), including version/tag verification, Dokka javadoc, signing, Portal release, post-release verification, and immutability rules. No library changes.