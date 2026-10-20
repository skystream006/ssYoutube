---
name: diagnostics
description: >-
  Handles ssYouTube opt-in logs, privacy redaction, crash formatting, bounded
  rotation, sharing/clearing, and memory/network/storage statistics. Use for
  diagnostic instrumentation, exports, or stats-overlay behavior.
---

# Diagnostics

- Start with `Logger`/`LogFormat`/`LogStore` or `StatsMonitor`/`StatsValues` as
  mapped in [diagnostics](references/diagnostics.md).
- Preserve privacy, bounded asynchronous work, narrow exports, and lifecycle
  cancellation; detailed invariants and test maps live in that reference.
- Consult [shell](../android-shell/SKILL.md) for controls/overlay/export wiring and
  the caller's Skill for changes to instrumentation at a feature boundary.
- Normally ignore stream selection, authentication internals, ad filtering,
  update transport, build workflows, and unrelated UI.
- Run mapped formatting/storage/statistics tests using
  [validation commands](../repo-router/references/validation.md), then affected
  device checks. Never load real credentials, browsing history, or private log
  contents as routine repository context.
