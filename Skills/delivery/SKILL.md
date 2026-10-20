---
name: delivery
description: >-
  Handles ssYouTube Gradle dependencies, Android compatibility, APK packaging,
  Git-derived versions, release workflows, and in-app update discovery,
  download validation, progress, and installation. Use for build, release,
  or updater changes.
---

# Build, release, and application updates

- Choose [build/release](references/build-release.md) or
  [updates](references/updates.md); do not load both automatically.
- Preserve the version/signing/release contract and validated installation.
- Consult [playback](../playback/SKILL.md) for native dependency changes,
  [shell](../android-shell/SKILL.md) for update presentation, or
  [diagnostics](../diagnostics/SKILL.md) when changing logging.
- Normally ignore browsing scripts, music credentials, ad rules, and native
  playback internals unless the changed dependency affects them.
- Use [validation commands](../repo-router/references/validation.md) and mapped
  updater tests. Broaden build/lint/test coverage for build/dependency changes.
- Publishing releases or deleting assets/tags requires explicit task authorization;
  neither is a validation step.
