---
name: delivery
description: >-
  Handles Gradle dependencies, Android compatibility, APK packaging,
  Git-derived versions, release workflows, and in-app update downloads
  and installation. Use for build, release, or updater changes.
---

# Choose the delivery stage

- For builds, start with `/home/runner/work/ssYoutube/ssYoutube/app/build.gradle`
  and [build/release](references/build-release.md). For updates, start with
  `/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/AppUpdater.java`
  and [updates](references/updates.md). Do not load both references automatically.
- Preserve version/signing compatibility, bounded downloads, and validated installation.
- Consult [playback](../playback/SKILL.md) for native dependencies,
  [android-shell](../android-shell/SKILL.md) for update UI, or
  [diagnostics](../diagnostics/SKILL.md) for logging changes.
- Ignore browsing scripts, music credentials, ad rules, and native playback
  internals unless directly affected. Never read keystore contents as context.
- Run mapped tests using [validation](../repo-router/references/validation.md);
  broaden build/lint/tests for build or shared-contract changes.
- Publishing releases, deleting assets/tags, or changing signing identity needs
  explicit task authorization; validation does not authorize those operations.
