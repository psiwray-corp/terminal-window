# 22 — GPG signing

**Phase:** release · **Depends on:** 18 · **Blocks:** 23

## Goal

Every published artifact (JAR, sources, Dokka javadoc, POM, module metadata) is signed with a key Maven Central can verify, locally and in CI, without ever printing the key.

## Deliverables

- `library/build.gradle.kts`: `signing` plugin + in-memory key support.
- `docs/release/signing.md`: key generation/rotation, keyserver publication, CI secrets, verification commands.
- `.github/workflows/release.yml` skeleton (completed in 24).
- `scripts/verify-signatures.sh` (or a Gradle task) that verifies a directory of artifacts.

## Steps

1. **Key audit.** `gpg --list-secret-keys --keyid-format long`; confirm a key with a valid email you control, an expiry, and no weak algorithm. If none: create an RSA-4096 or Ed25519 key dedicated to Maven Central.
2. **Publish the public key.** Send to `keys.openpgp.org` (email verification required) and `keyserver.ubuntu.com`; Central needs a discoverable key. Verify with `gpg --keyserver ... --recv-keys <id>` on a clean keyring.
3. **Gradle signing (Kotlin DSL).** Configure the keys now; sign the three JAR tasks for standalone verification. Publication-level signing is added in 23, when the `mavenJava` publication exists.
   ```kotlin
   signing {
       useInMemoryPgpKeys(
           providers.environmentVariable("SIGNING_KEY").orNull,
           providers.environmentVariable("SIGNING_PASSWORD").orNull,
       )
       sign(tasks["jar"], tasks["sourcesJar"], tasks["javadocJar"])
       isRequired = providers.gradleProperty("release").isPresent
   }
   ```
4. **Artifacts covered.** After 23, confirm `.asc` is generated for `*.jar`, `*-sources.jar`, `*-javadoc.jar`, `*.pom`, and `*.module` (if published).
5. **CI secrets.** `SIGNING_KEY` (ASCII-armored secret key), `SIGNING_PASSWORD`; mask them; never `echo`; prefer protected environments with required reviewers for releases. Configure the same names on GitHub and Forgejo if both forges can release.
6. **Verification.** Script: for each artifact, `gpg --verify artifact.asc artifact`; fail on missing or bad signature; also assert the signing key ID matches the expected fingerprint.
7. **Hygiene.** Store a revocation certificate offline; rotate before expiry; a compromised key means revoke + publish a new key and sign future releases (past releases stay).
8. **Test.** Sign a `2.0.0-local` build into `build/repo` (`maven-publish` to a file repository) and verify every `.asc` locally.

## Acceptance

- [ ] `./gradlew sign -Prelease` produces `.asc` for `jar`, `sourcesJar`, and `javadocJar`; `gpg --verify` passes with the public key only.
- [ ] After 23, the publication dry run signs the POM and module metadata too.
- [ ] A build without `-Prelease` and without keys still succeeds (no signing attempted).
- [ ] CI release job signs without printing key material (check logs).
- [ ] `docs/release/signing.md` states key expiry and the exact fingerprint to trust.
- [ ] The Dokka-generated `-javadoc.jar` is signed like any other artifact.

## Pitfalls

- `useInMemoryPgpKeys` expects the armored secret key, not a key ID; passing an ID silently uses the local keyring and fails on CI.
- Signing the publication is not enough if you publish a separate `distZip`; sign whatever is uploaded.
- `.asc` files must not be checksummed into the publication in a way Central rejects; let Gradle/nmcp handle checksums.
- An expired key breaks Central validation even if signatures are cryptographically valid.
- Kotlin DSL `isRequired = ...` must not force signing for local snapshot builds.

## Plan seed

> Plan section 22 only: configure the Gradle `signing` plugin with in-memory keys, sign all publication artifacts including the Dokka javadoc jar, add a verification script, and document key publication/rotation/CI secrets. No publishing to Central yet.