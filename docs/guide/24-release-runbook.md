# 24 — Release runbook

**Status:** Concluded 2026-11-16 · **Phase:** release · **Depends on:** 23 · **Blocks:** —

## Goal

A repeatable, auditable release driven from the maintainer's machine. Git
preparation is scripted; artifact signing and Central Portal publication stay
local because the signing key must never enter CI.

Both GitHub and Forgejo run ordinary build/test pipelines. There is deliberately
no tag-triggered release workflow and no release-signing secret.

## Directions

- `master` carries the newest development snapshot.
- `release/<major.minor>` carries the current snapshot for its patch line.
- A release has exactly one non-snapshot commit; the signed `v<version>` tag
  points at it.
- Release branches are never merged back into `master`.
- Hotfixes land on `master` first and are cherry-picked to the release branch.
- Maven Central versions are immutable; a mistake is fixed by the next patch or
  minor version.

## Deliverables

- `scripts/release.sh` — one-shot normal release preparation.
- `scripts/release-branch.sh` — release branch creation/selection.
- `scripts/release-prepare.sh` — release commit, signed tag, next-patch commit.
- `scripts/release-tag.sh` — signed tag helper.
- `scripts/verify-signatures.sh` — public-key-only artifact verification.
- `docs/release/runbook.md` — branch, tag, normal-release, and hotfix procedure.
- `docs/release/publishing.md` — Portal upload and validation instructions.
- `docs/release/signing.md` — key, local-signing, and release-authority rules.
- `docs/release/rollback.md` — discard-versus-next-version recovery.
- No `.github/workflows/release.yml`; no `.forgejo/workflows/release.yml`.

## Procedure

1. **Pre-flight.** On `master`: clean tree, `version=X.Y.Z-SNAPSHOT`, and a
   committed `## [X.Y.Z] - DATE` changelog heading.
2. **Prepare.** Run `scripts/release.sh "$VERSION"`. It creates the release
   branch, signed release commit, signed tag, next-patch snapshot commit, and
   the next-minor snapshot commit on `master`, then checks out the tag.
3. **Build from the tag.** Run the Gradle unit/integration/Dokka gates, then
   the signed local publication and public-key verification.
4. **Publish.** With local Portal token environment variables, run
   `publishAggregationToCentralPortal -Prelease --no-configuration-cache`.
   Validate, then release the deployment in the Portal.
5. **Verify.** Confirm the POM, module, JARs, signatures, and checksums on
   `repo1.maven.org`; resolve `2.0.0` from a clean consumer project; confirm the
   Central page.
6. **Push refs.** Push the release branch, signed tag, and updated `master` to
   GitHub and Forgejo.
7. **Continue development.** `master` is already at the next minor snapshot;
   `release/<major.minor>` remains at the next patch snapshot.

The exact commands are in [runbook.md](../../docs/release/runbook.md).

## Acceptance

- [x] The `2.0.0` release was prepared, signed, uploaded, and published.
- [x] Exactly one commit contains `2.0.0`, and `v2.0.0` points at it.
- [x] The release branch is at `2.0.1-SNAPSHOT`; `master` is at
  `2.1.0-SNAPSHOT`; the release branch was not merged into `master`.
- [x] `repo1.maven.org` serves the POM, module metadata, JAR, sources, Dokka
  Javadoc, and all signatures.
- [x] The POM has JOML at compile scope and Kotlin stdlib at runtime scope, with
  no native classifiers.
- [x] A clean Gradle consumer resolves `terminal-window:2.0.0` and its runtime
  dependencies.
- [x] `docs/release/rollback.md` exists.
- [x] No release-signing key, passphrase, or Portal token is present in either
  forge's workflow configuration.

## Pitfalls

- Do not create the release commit before the release branch starts from the
  snapshot version.
- Do not tag before the changelog and release tree are committed.
- Do not add a release workflow that receives the signing key or passphrase.
- Do not merge the release branch into `master`.
- Do not reuse or re-upload a Central version after publication.
- `USER_MANAGED` deployments expire; validate and release promptly.

## Plan seed

> Section 24 is concluded: release Git operations are scripted under
> `scripts/`; signing and publication remain local-only; branch, tag, hotfix,
> verification, and rollback procedures are documented. No CI release workflow.