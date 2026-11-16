# Release and hotfix runbook

This project uses a release branch per major/minor line. `master` always holds
the newest development snapshot. A release branch holds the current snapshot
for its patch line. A published version exists in exactly one commit, and that
commit is tagged.

Signing and publishing are local-only. The key and passphrase never enter CI;
the exact upload mechanics are in [publishing.md](publishing.md).

## Branch and version model

| Ref | Example | Meaning |
|---|---|---|
| `master` | `2.1.0-SNAPSHOT` | Newest development snapshot |
| `release/2.0` | `2.0.1-SNAPSHOT` | Current snapshot of the 2.0 patch line |
| Tag `v2.0.0` | — | The one commit containing `2.0.0` |
| Tag `v2.0.1` | — | The one commit containing hotfix `2.0.1` |

`gradle.properties` is the version source of truth. The tag must match it:
`v${version}`. Never publish a `-SNAPSHOT` to the Central release repository.

The sequence for one release is:

```text
master:       2.0.0-SNAPSHOT
release/2.0:  2.0.0-SNAPSHOT
              |
              +-- commit 2.0.0 + tag v2.0.0
              |
release/2.0:  2.0.1-SNAPSHOT
master:       2.1.0-SNAPSHOT
```

Only the tagged commit contains the non-snapshot version. The next commit
restores the snapshot. The next minor version is `2.1.0-SNAPSHOT`, not
`3.0.0-SNAPSHOT`; `3.0.0` is reserved for a deliberate breaking release.

## Before starting

Use JDK 25, a clean release branch, and a working GnuPG keyring. The public key
must be available for verification:

```bash
export PUBLIC_KEY_FILE=/secure/path/terminal-window-signing.asc
export VERSION=2.0.0
export MAJOR="${VERSION%%.*}"
export MINOR="${VERSION#*.}"; export MINOR="${MINOR%%.*}"
export RELEASE_BRANCH="release/${MAJOR}.${MINOR}"
export NEXT_MINOR="${MAJOR}.$((MINOR + 1)).0-SNAPSHOT"
export TAG="v$VERSION"
export DATE="$(date -u +%F)"
```

For `2.0.0`, these expand to `release/2.0`, `2.1.0-SNAPSHOT`, `v2.0.0`, and
today’s UTC date. `release-prepare.sh` derives the next patch snapshot
(`2.0.1-SNAPSHOT`) itself.

## Git helper scripts

Three scripts handle the branch, commits, and tag:

```bash
scripts/release-branch.sh "$VERSION"             # create or switch release/<major.minor>
scripts/release-branch.sh "$VERSION" --push      # also push the branch
scripts/release-branch.sh "$VERSION" --from HEAD # non-default base for a new branch
scripts/release-prepare.sh "$VERSION"            # release commit + signed tag + next snapshot commit
scripts/release-tag.sh "$VERSION"                # tag only; used by release-prepare.sh
scripts/release-tag.sh "$VERSION" --push         # also push the tag
```

All accept `--dry-run`; use it to inspect the Git commands without changing the
repository. `release-branch.sh` may be used with uncommitted release-candidate
changes; `release-prepare.sh` and `release-tag.sh` require a clean worktree.
`release-prepare.sh` commits with `git commit -S`; the GPG passphrase is entered
manually.

## Normal release

### 1. Create the release branch

For a new major/minor line, branch from `master` while it is still the
`X.Y.0-SNAPSHOT` version:

```bash
git switch master
git pull --ff-only
scripts/release-branch.sh "$VERSION"
```

The release branch must start from `${VERSION}-SNAPSHOT`. A release branch
never starts at the non-snapshot version. `scripts/release-branch.sh` enforces
this check when it creates a branch.

### 2. Prepare the changelog and release commits

Move the `[Unreleased]` changelog entries under a heading for this release and
commit that changelog change on the release branch. Leave a new, empty
`## [Unreleased]` section for the next cycle.

Then run:

```bash
scripts/release-prepare.sh "$VERSION"
```

The script performs, in order:

1. `2.0.0-SNAPSHOT` -> signed commit `Release 2.0.0`
2. signed tag `v2.0.0` at that commit
3. `2.0.0` -> signed commit `Prepare next development iteration 2.0.1-SNAPSHOT`

The release branch is left on `2.0.1-SNAPSHOT`; the tag points at the only
commit containing `2.0.0`. Preview the sequence with:

```bash
scripts/release-prepare.sh "$VERSION" --dry-run
```

### 3. Build from the tag

```bash
git switch --detach "$TAG"

./gradlew --no-daemon clean check
xvfb-run -a ./gradlew --no-daemon integrationTest
./gradlew --no-daemon dokkaGenerate
./gradlew --no-daemon clean \
  :library:publishAllPublicationsToLocalRepository -Prelease
SIGNING_PUBLIC_KEY_FILE="$PUBLIC_KEY_FILE" \
  scripts/verify-signatures.sh \
  "library/build/repo/com/psiwray/organization/terminal-window/$VERSION"
```

The last two commands prompt for the GPG passphrase. Verify that the local
repository contains the JAR, sources, Dokka Javadoc, POM, module metadata, one
`.asc` per file, and checksums. If a gate fails, delete the local tag and
repeat from step 2.

### 4. Publish to the Central Portal

Still on the tagged commit, load the Portal token into the current shell only,
then upload:

```bash
export MAVEN_CENTRAL_USERNAME='<portal-token-username>'
read -rsp 'Central Portal token password: ' MAVEN_CENTRAL_PASSWORD; echo
export MAVEN_CENTRAL_PASSWORD

./gradlew --no-daemon publishAggregationToCentralPortal \
  -Prelease --no-configuration-cache
```

The deployment is `USER_MANAGED`. Review it at
<https://central.sonatype.com/>. If validation fails, discard the deployment
and fix the source; if it passes, click **Publish** and wait for `PUBLISHED`.

Do not discard the final validated deployment; that is the release. Do not
move the tag or re-upload after Central accepts the version.

### 5. Verify and push release refs

```bash
curl -fsSL \
  "https://repo1.maven.org/maven2/com/psiwray/organization/terminal-window/$VERSION/terminal-window-$VERSION.pom" \
  >/dev/null

git switch "$RELEASE_BRANCH"
git push origin "$RELEASE_BRANCH" "$TAG"
```

Also resolve the version from a clean consumer project and check the Central
artifact page. A failed resolution is not fixed by re-uploading the same
version; publish the next patch version.

### 6. Move master to the next snapshot

Merge the release line back so `master` has the release commit and snapshot
commit, then set its next development version:

```bash
git switch master
git merge --no-ff "$RELEASE_BRANCH" \
  -m "Merge $RELEASE_BRANCH after $VERSION"
sed -i -E "s/^version=.*/version=${NEXT_MINOR}/" gradle.properties
git add gradle.properties
git commit -S -m "Prepare next development iteration $NEXT_MINOR"
git push origin master
```

After 2.0.0, `master` is `2.1.0-SNAPSHOT` and `release/2.0` is
`2.0.1-SNAPSHOT`.

## Hotfix

A hotfix bumps only the third version element and runs on the existing release
branch.

```bash
export VERSION=2.0.1
export RELEASE_BRANCH=release/2.0
export TAG="v$VERSION"
export DATE="$(date -u +%F)"

git switch "$RELEASE_BRANCH"
git pull --ff-only
```

### 1. Commit the fix without changing the version

```bash
# Make and test the fix.
git add <fixed-files>
git commit -S -m "Fix <issue>"
export FIX_COMMIT="$(git rev-parse HEAD)"
```

### 2. Prepare and build the patch release

Add the hotfix entry under a new `## [$VERSION] - $DATE` heading and commit it.
Then let the preparation script create the release commit, tag, and next patch
snapshot commit:

```bash
git add CHANGELOG.md
git commit -S -m "Add $VERSION changelog"

scripts/release-prepare.sh "$VERSION"
git switch --detach "$TAG"

./gradlew --no-daemon clean check
xvfb-run -a ./gradlew --no-daemon integrationTest
./gradlew --no-daemon dokkaGenerate
./gradlew --no-daemon clean \
  :library:publishAllPublicationsToLocalRepository -Prelease
SIGNING_PUBLIC_KEY_FILE="$PUBLIC_KEY_FILE" \
  scripts/verify-signatures.sh \
  "library/build/repo/com/psiwray/organization/terminal-window/$VERSION"
```

Upload and publish exactly as in the normal release. Then return to the release
branch, which the preparation script left at the next patch snapshot, and push:

```bash
git switch "$RELEASE_BRANCH"
git push origin "$RELEASE_BRANCH" "$TAG"
```

### 3. Bring the fix to master

Do not merge the release branch wholesale into `master`; that would overwrite
master’s newer snapshot version. Cherry-pick only the fix commit:

```bash
git switch master
git pull --ff-only
git cherry-pick "$FIX_COMMIT"
git push origin master
```

## Failure and recovery rules

- Before Central accepts the version: delete the local tag if it was not
  pushed, fix the release commit, and repeat. If a deployment is already
  validated but not released, discard it and re-upload the corrected artifacts.
- After Central accepts the version: never re-upload or re-tag it. Publish the
  next patch (`2.0.1`) or minor (`2.1.0`) version instead.
- A tag may be deleted locally or on the forge only if the Central version was
  never released. Central versions are immutable.
- Never put the GPG passphrase, Portal token, or `SIGNING_*` values in the
  repository or in a workflow.