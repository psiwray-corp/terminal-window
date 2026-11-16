# Maven Central publishing

`terminal-window` publishes to the Central Portal through `com.gradleup.nmcp`
1.6.2. The Portal has no official Gradle plugin; nmcp is the only bridge
between Gradle's Maven publication format and the Portal upload API.

The procedures here are local-only. **PsiWray, the OSSRH key owner, is the only
person who may sign or publish a release.** Signing and passphrase entry are
manual; CI has neither the key nor the passphrase. The branch, tag, and hotfix
sequence is in [runbook.md](runbook.md).

## Prerequisites

- `com.psiwray.organization` is verified in the Central Portal.
- A Portal user token exists. The token username and password are separate from
  the account password.
- The release tree is clean, has no `-SNAPSHOT` version, and includes the
  `CHANGELOG.md` entry.
- The signing key from [signing.md](signing.md) is available in the local
  GnuPG keyring.

Do not use the legacy `oss.sonatype.org` staging URLs. Do not put the Portal
password, GPG passphrase, or `SIGNING_*` values in this repository or in either
forge's CI configuration.

## Namespace check

Sign in at <https://central.sonatype.com/> and open the namespace list. Confirm
that `com.psiwray.organization` is present and verified. If it is absent,
follow the Portal's DNS TXT verification for `psiwray.com`; do not publish until
the namespace is verified.

## Portal token

Create the token in the Portal account settings. Load it into the current shell
only:

```bash
export MAVEN_CENTRAL_USERNAME='<portal-token-username>'
read -rsp 'Central Portal token password: ' MAVEN_CENTRAL_PASSWORD; echo
export MAVEN_CENTRAL_PASSWORD
```

Use `--no-configuration-cache` for Portal commands so the token is not retained
in the Gradle configuration cache:

```bash
./gradlew --no-daemon publishAggregationToCentralPortal \
  -Prelease -Pversion=2.0.0 --no-configuration-cache
```

## Local dry run, including signatures

The release tree sets `version=2.0.0` in `gradle.properties`. The explicit
override below also works from a `-SNAPSHOT` development checkout:

```bash
./gradlew --no-daemon clean \
  :library:publishAllPublicationsToLocalRepository \
  -Prelease -Pversion=2.0.0
```

Gradle invokes `gpg-agent`/pinentry for **each** artifact: main JAR, sources JAR,
Dokka Javadoc JAR, POM, and Gradle module metadata. Enter the passphrase
manually. The local repository is:

```text
library/build/repo/com/psiwray/organization/terminal-window/2.0.0/
```

It must contain:

```text
terminal-window-2.0.0.jar
terminal-window-2.0.0-sources.jar
terminal-window-2.0.0-javadoc.jar
terminal-window-2.0.0.pom
terminal-window-2.0.0.module
*.asc                       # one signature for each file above
*.md5, *.sha1, *.sha256, *.sha512
```

Verify the signatures with only the public key:

```bash
gpg --armor --export FAA5A60467F543371A28988501E34446CF304A61 \
  > /secure/path/terminal-window-signing.asc
SIGNING_PUBLIC_KEY_FILE=/secure/path/terminal-window-signing.asc \
  scripts/verify-signatures.sh \
  library/build/repo/com/psiwray/organization/terminal-window/2.0.0
```

Check the publication contents:

```bash
jar tf library/build/repo/com/psiwray/organization/terminal-window/2.0.0/terminal-window-2.0.0-javadoc.jar | head
jar --describe-module --file \
  library/build/repo/com/psiwray/organization/terminal-window/2.0.0/terminal-window-2.0.0.jar
grep -A4 '<artifactId>joml</artifactId>' \
  library/build/repo/com/psiwray/organization/terminal-window/2.0.0/terminal-window-2.0.0.pom
grep -A4 '<artifactId>kotlin-stdlib</artifactId>' \
  library/build/repo/com/psiwray/organization/terminal-window/2.0.0/terminal-window-2.0.0.pom
```

The Javadoc JAR must contain HTML for every exported package. The POM must show
JOML at compile scope and Kotlin stdlib at runtime scope. No LWJGL native
classifier may appear.

## Portal validation

Upload a user-managed deployment and let the Portal validate it:

```bash
./gradlew --no-daemon publishAggregationToCentralPortal \
  -Prelease -Pversion=2.0.0 --no-configuration-cache
```

Open <https://central.sonatype.com/> and inspect the deployment. For a
pre-release dry run, **discard** it after validation and repeat until clean. For
the final release candidate, validation is the last gate: leave the deployment
in place and continue to **Publish**. nmcp uploads in parallel; a failed upload
can leave a partial deployment that must be discarded rather than released.

## Release

Only after the clean validation:

1. Set `version=2.0.0` in `gradle.properties`; remove `-SNAPSHOT`.
2. Confirm `git status` is clean and the tree is the tagged release commit.
3. Run the local dry run and public-key verification again.
4. Upload with `USER_MANAGED` using the command above.
5. In the Portal, review coordinates, files, signatures, and checksums, then
   click **Publish**.
6. Wait for the deployment to report `PUBLISHED`.

Versions are immutable. A bad `2.0.0` cannot be replaced; fix it by publishing
`2.0.1`. Past releases stay signed by their original key.

## Snapshots

Snapshots are optional and are not on `repo1`:

```bash
./gradlew --no-daemon publishAggregationToCentralSnapshots \
  -Pversion=2.0.0-SNAPSHOT --no-configuration-cache
```

Snapshot coordinates are served from
`https://central.sonatype.com/repository/maven-snapshots/`.

## Validation-error catalogue

| Error | Cause | Fix |
|---|---|---|
| `401`/`403` | Wrong token or namespace | Regenerate the Portal token; confirm namespace ownership. |
| Missing `-sources.jar` | Template publication lacks sources | Keep `withSourcesJar()` and `from(components["java"])`. |
| Empty `-javadoc.jar` | `withJavadocJar()` used instead of Dokka | Use the `javadocJar` task backed by `dokkaGeneratePublicationJavadoc`. |
| Missing `.asc` | Signing did not run | Rerun with `-Prelease`; check GnuPG key selection. |
| Invalid POM | Missing name, description, URL, license, developers, or SCM | Keep the `pom { ... }` block in `library/build.gradle.kts`. |
| Native classifier in POM | `natives` configuration leaked into a published component | Keep natives as a separate resolvable-only configuration. |
| `SNAPSHOT` rejected | Release repository given a snapshot version | Publish `2.0.0`, not `2.0.0-SNAPSHOT`. |
| Duplicate version | Version already on Central | Publish the next patch version. |
| Checksum mismatch | Upload interrupted or files changed after signing | Discard the deployment; rebuild and re-sign from a clean tree. |

## Related

[Artifact signing](signing.md) · [Release runbook](../guide/24-release-runbook.md) ·
[Compatibility](../compatibility.md)