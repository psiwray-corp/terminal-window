# ADR-0002: License for version 2

- **Status:** Accepted
- **Date:** 2026-11-15
- **Revised:** 2026-11-15 (Apache-2.0 to MIT, before any 2.x release)
- **Deciders:** project owner
- **Supersedes:** none

## Context

Version 1 was published under GPL-3.0. Every published version 1 artifact stays
under GPL-3.0 forever; a license cannot be revoked for copies already
distributed.

Version 2 is a new implementation written for this project. Relicensing is
legally available only for code whose copyright the project owner holds, so the
decision depends on provenance: no version 1 source file may be copied into the
version 2 tree if version 2 is to carry a permissive license. Copying a single
GPL-3.0 file would make the combined work GPL-3.0.

The library is a rendering toolkit intended to be embedded in applications.
Copyleft licensing of a library is a known adoption barrier for proprietary
consumers, while the dependencies chosen for version 2 (LWJGL under BSD-3-Clause,
JOML under MIT) are permissive and impose no copyleft obligation.

An earlier revision of this ADR selected Apache-2.0. The project owner replaced
that choice with MIT before any 2.x artifact was published; no release depends
on the earlier text.

## Decision

We will license version 2, including the build scripts and documentation in this
repository, under the **MIT License**.

Rules attached to this decision:

1. Version 2 code must be authored for this project. No version 1 source is
   copied into this repository.
2. If a version 1 file is ever imported, the import is rejected unless the
   artifact is relicensed to GPL-3.0 as a whole, or the imported file is
   independently rewritten from scratch.
3. Published `1.x` artifacts remain GPL-3.0. Their license text, notices, and
   source availability obligations are unchanged.
4. Inbound contributions are accepted under MIT (inbound = outbound). No
   separate CLA is required.
5. The repository ships `LICENSE` with the MIT text and the copyright notice.
   MIT has no `NOTICE`-file requirement.
6. Dependency licenses are recorded in the repository so the permissive
   baseline is auditable.
7. Copyright notices are preserved in copies and substantial portions of the
   software, as the MIT text requires.

This decision applies to `2.0.0` and all later 2.x releases unless a future ADR
supersedes it.

## Consequences

### Positive

- Proprietary and open-source applications can embed the library without
  copyleft obligations.
- MIT is the shortest permissive license in common use and needs no
  `NOTICE` file or per-file change statements.
- MIT is compatible with the BSD-3-Clause and MIT dependencies.
- MIT code can be combined into a GPL-3.0 work, which then carries GPL-3.0, so
  GPL-licensed consumers are not excluded.
- Consumers can vendor, repackage, or ship modified builds under clear terms.

### Negative

- MIT grants no express patent license.
- MIT grants no trademark rights.
- The version 1 codebase cannot be reused verbatim; fixes must be ported by
  rewriting, not by copying.
- Contributors cannot submit version 1 patches as version 2 patches.
- Redistributors must preserve the copyright and permission notice; downstream
  modifications may remain closed.

### Neutral

- The license change does not affect binary or source compatibility; see
  `docs/compatibility.md`.
- Version 1 users who need GPL-3.0 can stay on `1.4.1`.

## Alternatives

| Alternative | Why rejected |
|---|---|
| Stay GPL-3.0 | Continuity, but keeps the copyleft barrier that limits library adoption; version 2 is a new codebase, so continuity has no legal benefit. |
| Apache-2.0 | Earlier selection; adds an express patent grant plus `NOTICE` and change-statement obligations that this project does not need. |
| LGPL-3.0 | Still imposes relinking and reverse-engineering rights on combined works; weaker barrier than GPL, but not what library consumers expect. |
| Dual GPL-3.0 / commercial | Requires sales, tracking, and per-consumer terms; no demand established. |
| MIT for new code, GPL-3.0 for imported v1 code | Produces a mixed-license artifact whose effective terms are GPL-3.0; defeats the purpose. |

## References

- MIT License: <https://opensource.org/license/mit>
- SPDX identifier `MIT`.
- Version 1 artifacts on Maven Central, published under GPL-3.0.
