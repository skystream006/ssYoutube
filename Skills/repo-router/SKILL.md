---
name: repo-router
description: >-
  Routes ssYouTube changes to the smallest relevant engineering domain. Use for
  unclear ownership, repository orientation, or changes spanning multiple
  features. Skip when a domain Skill already clearly matches the request.
---

# Route before exploring

1. Match the request against domain Skill metadata; select one primary owner.
2. If ownership is unclear, read the relevant row in
   [architecture](references/architecture.md), not every domain reference.
3. Return the primary Skill, seed symbols, conditional neighbors, and smallest
   relevant test set. Then load that Skill and only its relevant reference.
4. Consult [shared contracts](references/shared-contracts.md) only when crossing
   an ownership boundary; use [validation](references/validation.md) for commands.
5. Expand only for a changed contract, an observed dependency, or failing evidence.
   Calling a stable helper does not require loading its owner's entire domain.

Do not implement feature changes here, scan the whole repository, or read all of
`MainActivity` by default. If a request targets an absent backend, database, or
external server, identify that boundary instead of inventing local components.

The root Skills directory requires a loader that discovers or explicitly registers
it. Metadata enables selection only after discovery; these files do not configure
host-specific automatic invocation. See the architecture reference for activation
and routing acceptance examples.
