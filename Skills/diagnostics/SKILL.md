---
name: diagnostics
description: >-
  Handles opt-in logs, privacy redaction, crash formatting, rotation,
  sharing/clearing, and memory/network/storage statistics. Use for
  diagnostic instrumentation, exports, or stats-overlay behavior.
---

# Start at collection or presentation

- Use [diagnostics](references/diagnostics.md) to select Logger/LogFormat/LogStore
  or StatsMonitor/StatsValues in
  `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube`.
  Inspect only the relevant source methods and mapped tests.
- Preserve bounded asynchronous work, privacy rules, and lifecycle cancellation.
- Consult [android-shell](../android-shell/SKILL.md) for controls/export wiring
  and the caller's Skill when instrumentation changes feature behavior.
- Normally ignore extraction, authentication, filtering, update transport,
  and build workflows. Never load actual credentials, browsing history, or
  private log contents as repository context.
- Run mapped formatting/storage/statistics tests with
  [validation](../repo-router/references/validation.md); include affected Android
  resource checks and device scenarios for overlay/export changes.
