# Targeted validation

Domain references map behavior to existing test classes and device scenarios.
Use those maps first; do not load the entire test suite to choose a test.

## Prerequisites and source of truth

- `/home/runner/work/ssYoutube/ssYoutube/app/build.gradle`: Android API levels,
  dependencies, desugaring, test dependency, and full-Git-history requirement.
- `/home/runner/work/ssYoutube/ssYoutube/gradle/wrapper/gradle-wrapper.properties`:
  Gradle distribution.
- `/home/runner/work/ssYoutube/ssYoutube/README.md`, “Build and test”: JDK 17,
  Android SDK compile API 34, minimum API 21, Node 18+ for JavaScript fixtures.
- `/home/runner/work/ssYoutube/ssYoutube/settings.gradle`: Google Maven, Maven
  Central, and restricted JitPack resolution; fresh builds need network access.

All features compile in `:app`. There is no independently buildable frontend,
backend, or JVM-only domain module. Even a filtered Gradle test invocation needs
the Android toolchain and full Git history because project configuration checks it.
Report missing prerequisites instead of presenting a blocked check as passed.

## Commands

Use the actual absolute checkout root if different. Replace `<TestClass>` with a
class named by the selected reference; repeat `--tests` for additional classes.

```sh
/home/runner/work/ssYoutube/ssYoutube/gradlew -p /home/runner/work/ssYoutube/ssYoutube :app:testDebugUnitTest --tests 'com.skystream.ssyoutube.<TestClass>'
```

A method filter can narrow an existing class further, for example
`com.skystream.ssyoutube.PreferencesLayoutTest.updateProgressIsInlineBelowHeaderAndHiddenWhenIdle`.
Expand to the whole relevant class when shared behavior changes.

For Android source/resource integration:

```sh
/home/runner/work/ssYoutube/ssYoutube/gradlew -p /home/runner/work/ssYoutube/ssYoutube :app:assembleDebug :app:lintDebug
```

For shared build/contract changes or broad final regression:

```sh
/home/runner/work/ssYoutube/ssYoutube/gradlew -p /home/runner/work/ssYoutube/ssYoutube test
```

For the actual bundled video-ad JavaScript:

```sh
node --test /home/runner/work/ssYoutube/ssYoutube/app/src/test/js/youtube_video_adblock.test.cjs
```

`YouTubeVideoAdBlockingTest` invokes the same Node suite but skips if Node cannot
be started. A skipped wrapper is not JavaScript coverage. No npm packages are
required. Avoid running both forms redundantly unless diagnosing the wrapper.

## Selection and coverage limits

1. Start with the changed class's named tests; inspect only relevant test methods.
2. Add consumer tests for changed shared predicates, data shapes, resources, or
   lifecycle contracts. Deduplicate when multiple Skills name the same test.
3. Keep tests in their existing domain and preserve unrelated assertions.
4. Run build/lint for Android changes; broaden JVM coverage for build/dependency
   changes or evidence of cross-domain regressions.
5. Report executed, skipped, blocked, and manual checks separately.

JVM tests cover pure decisions, fake HTTP connections, script strings, and parsed
XML. They do not prove WebView DOM behavior, native rendering/decoding, Android
Keystore, browser/passkey interaction, or installation permissions. There is no
checked-in instrumentation suite; follow the relevant domain's device checklist.

## Skill-documentation changes

No application build is needed for Markdown-only edits. There is no dedicated
Skill linter/test framework in this repository; do not add one just for these
documents.

- Check front matter for a unique `name` matching the folder and a task-specific
  `description`; verify that each control plane stays compact.
- Follow every local Markdown link and confirm source paths, symbols, resource
  IDs, and test classes against the checkout.
- Replay the five routing examples in the architecture reference; unrelated
  domains should remain unloaded.
- Check that shared knowledge has one owner, references are loaded conditionally,
  and discovery requirements are not mistaken for installed host configuration.
- Review the diff for whitespace, unintended application changes, and secrets.
