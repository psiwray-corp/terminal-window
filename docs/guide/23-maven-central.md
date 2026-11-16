# 23 — Maven Central publishing

**Phase:** release · **Depends on:** 21, 22 · **Blocks:** 24

## Goal

`2.0.0` (and future releases) published through the Central Portal under the existing `com.psiwray.organization` namespace, with valid POM metadata, sources, Dokka javadoc, signatures, and checksums. The build is Gradle; `maven-publish` is only the Gradle plugin that speaks Maven's repository format. No `pom.xml` exists.

## Facts

- v1 shipped via OSSRH to `oss.sonatype.org`; those staging URLs are legacy. Do not copy the v1 `distributionManagement`.
- Maven Central now takes uploads through `central.sonatype.com` (Portal). Sonatype documents **no official Gradle plugin** for the Portal; use the community `com.gradleup.nmcp` (1.6.2, Java 17+, Gradle 8.8+).
- nmcp tasks: `publishAggregationToCentralPortal`, `publishAggregationToCentralSnapshots`.
- Versions are immutable; a bad release is fixed by publishing the next version.
- Kotlin projects need a Dokka-built `-javadoc.jar`; `withJavadocJar()` produces an empty jar.

## Deliverables

- `settings.gradle.kts`: `id("com.gradleup.nmcp.settings") version "1.6.2"` + `nmcpSettings { centralPortal { ... } }`.
- `library/build.gradle.kts`: `maven-publish` publication `mavenJava`, `javadocJar` from Dokka, POM metadata, `signing`, local file repository for dry runs.
- `docs/release/publishing.md`: token setup, namespace check, dry run, release steps, validation-error catalogue.
- `.github/workflows/release.yml`: tag-triggered publish (finalized in 24).

## Steps

1. **Namespace.** Sign in to `central.sonatype.com`; confirm `com.psiwray.organization` is listed (it was verified for v1). If not, verify ownership of `psiwray.com` per the Portal's DNS instructions.
2. **Token.** Generate a Portal user token; store as `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` in GitHub and Forgejo secrets (and locally in `~/.gradle/gradle.properties`, never in the repo).
3. **Publication (Kotlin DSL).**
   ```kotlin
   publishing {
       publications {
           create<MavenPublication>("mavenJava") {
               from(components["java"])
               artifactId = "terminal-window"
               artifact(tasks.named("javadocJar"))
               pom {
                   name.set("Terminal Window")
                   description.set("Per-thread OpenGL terminal matrix renderer with bitmap fonts.")
                   url.set("https://github.com/psiwray-corp/terminal-window")
                   licenses { license { name.set("..."); url.set("...") } }   // ADR-0002
                   developers { developer { id.set("psiwray"); name.set("Luca Cavallari"); email.set("...") } }
                   scm {
                       connection.set("scm:git:git@github.com:psiwray-corp/terminal-window.git")
                       url.set("https://github.com/psiwray-corp/terminal-window")
                   }
               }
           }
       }
       repositories { maven { name = "local"; url = uri(layout.buildDirectory.dir("repo")) } }
   }
   ```
4. **nmcp.** Configure username/password from env; `publishingType = "USER_MANAGED"`; `publicationName = "terminal-window:$version"`.
5. **Sign the publication.** After the `publication` exists in the same script, add `signing { sign(publishing.publications["mavenJava"]) }` (keys and `isRequired` come from 22). Keep the signing block after the publication block.
6. **Dry run.** `./gradlew publishAllPublicationsToLocalRepository -Prelease`; inspect `build/repo/com/psiwray/organization/terminal-window/2.0.0/`: jar, sources, javadoc, pom, module, `.asc`, checksums. Assert the POM has `joml` at compile scope, `kotlin-stdlib` at runtime scope, and no native classifiers.
7. **Portal validation.** `./gradlew publishAggregationToCentralPortal` with `USER_MANAGED`; let the Portal validate, fix errors, and **discard** the deployment. Repeat until clean.
8. **Snapshots (optional).** `publishAggregationToCentralSnapshots` to `central.sonatype.com/repository/maven-snapshots/`; snapshots are not on `repo1`.
9. **Release.** Publish `2.0.0`, release the deployment in the UI, wait for `repo1.maven.org`, then verify with a tiny Gradle consumer project on a clean cache (Maven `dependency:get` is an optional cross-check, not a build step).

## Acceptance

- [ ] A local dry-run publication contains exactly: `.jar`, `-sources.jar`, `-javadoc.jar`, `.pom`, `.module`, one `.asc` per file, checksums.
- [ ] The `-javadoc.jar` contains Dokka-generated Javadoc HTML for every exported package.
- [ ] The POM passes Central validation; it lists JOML (compile) and Kotlin stdlib (runtime) and no natives.
- [ ] `2.0.0` resolves from `repo1.maven.org` via a Gradle consumer (Maven `dependency:get` optional).
- [ ] The Central page lists `2.0.0` next to the v1 versions.
- [ ] `jar --describe-module` on the published JAR shows the automatic module name.

## Pitfalls

- Missing or empty Javadoc jar fails validation; verify the jar has HTML files, not just a manifest.
- Gradle Module Metadata (`.module`) references variants; keep `api`/`runtime` scopes meaningful (JOML vs Kotlin stdlib are the first test).
- Do not publish `2.0.0` from a dirty tree or a `-SNAPSHOT` version; Central rejects SNAPSHOT to the release repository.
- `mavenLocal()` is not a substitute for the dry-run repo; it hides missing artifacts.
- nmcp uploads in parallel; a partially failed upload leaves a deployment you must inspect and discard.
- Kotlin sources jar must contain `.kt` files; `withSourcesJar()` does that, `withJavadocJar()` does not produce docs.

## Plan seed

> Plan section 23 only: configure `maven-publish` + nmcp 1.6.2 for the Central Portal, build the javadoc jar from Dokka, complete POM metadata (JOML compile, Kotlin stdlib runtime), add a file-repository dry run, validate the bundle, and publish `2.0.0`. No release automation.