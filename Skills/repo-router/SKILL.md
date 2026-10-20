---
name: repo-router
description: >-
  Routes ssYouTube changes to the smallest relevant engineering domain.
  Use for unclear ownership, repository orientation, or changes spanning
  multiple features. Skip when a domain Skill clearly matches.
---

# Route before exploring

- Match the request to domain metadata first: [android-shell](../android-shell/SKILL.md),
  [playback](../playback/SKILL.md), [adblocking](../adblocking/SKILL.md),
  [music-server](../music-server/SKILL.md), [delivery](../delivery/SKILL.md),
  or [diagnostics](../diagnostics/SKILL.md). Do not open all their bodies.
- If ownership is unclear, read the matching section of
  [architecture](references/architecture.md). It also explains loader discovery.
- Select one primary Skill, seed symbols, the smallest test set, and conditional
  neighbors. Load a neighbor only when its contract must change or be investigated.
- Consult [shared contracts](references/shared-contracts.md) at shared boundaries
  and [validation](references/validation.md) when choosing commands.
- Do not implement here, scan the whole repository, or load every reference.
  If a request targets an absent backend/service, identify that external boundary.
- Validate routing decisions against the examples in the architecture reference;
  domain Skills remain authoritative for implementation.
