# Targeted validation

All features share `:app`; there are no independently buildable domain modules.
Choose test classes from the affected domain reference, not a repository-wide
test inventory.

## Prerequisites

- JDK 17 and Android SDK platform 34; Gradle wrapper and dependency repository
  network access for an uncached build. SDK location comes from the environment
  or `/home/runner/work/ssYoutube/ssYoutube/local.properties`, not committed
  machine-specific settings.
- Full Git history: `/home/runner/work/ssYoutube/ssYoutube/app/build.gradle`
  rejects shallow clones because versions use first-parent commit counts.
  Inspect with `git -C /home/runner/work/ssYoutube/ssYoutube rev-parse --is-shallow-repository`;
  if shallow, obtain history with
  `git -C /home/runner/work/ssYoutube/ssYoutube fetch --unshallow` before Gradle.
- Node 18+ for executable video-ad JavaScript coverage; no npm dependencies.
  `YouTubeVideoAdBlockingTest` skips its executable test when Node is absent.
  Report that skip explicitly; it is not successful JavaScript validation.

## Select and run

JUnit files are in
`/home/runner/work/ssYoutube/ssYoutube/app/src/test/java/com/skystream/ssyoutube`.
Tests use package `com.skystream.ssyoutube`. Start with the mapped class or method:

```sh
/home/runner/work/ssYoutube/ssYoutube/gradlew -p /home/runner/work/ssYoutube/ssYoutube :app:testDebugUnitTest --tests 'com.skystream.ssyoutube.PreferencesTest'
```

Replace the class with the affected test. Repeat `--tests` for multiple classes;
use a fully qualified `ClassName.methodName` for a focused regression during
iteration. Expand to the class and affected consumers before completing the change.
Resource/manifest tests read paths relative to the app module, so use Gradle
rather than an ad hoc Java invocation.

For the bundled video-ad script:

```sh
node --test /home/runner/work/ssYoutube/ssYoutube/app/src/test/js/youtube_video_adblock.test.cjs
```

For Android source/resources/manifest integration:

```sh
/home/runner/work/ssYoutube/ssYoutube/gradlew -p /home/runner/work/ssYoutube/ssYoutube :app:assembleDebug :app:lintDebug
```

For broad shared-contract or build/dependency changes, or final broad regression:

```sh
/home/runner/work/ssYoutube/ssYoutube/gradlew -p /home/runner/work/ssYoutube/ssYoutube test
```

These are existing Gradle/Node facilities, not new test infrastructure. Do not
start with full test execution or `clean` merely to discover the responsible
component. Build configuration and release checks are in
[build/release](../../delivery/references/build-release.md).

## Coverage limits

Pure decision/state classes are JVM-tested. Some Java tests assert generated
script text rather than execute a browser. Layout/manifest tests parse XML;
they do not prove rendering, accessibility, or Android intent behavior.
The Node fixtures execute the actual ad script but simulate the page environment.
No checked-in instrumented Android test suite substitutes for device checks.

Use the affected domain's device scenarios for native decoding/ABI/audio focus,
WebView SPA behavior, lifecycle recreation, browser/Keystore authentication,
or installer permissions. Do not report live-service or device checks as passed
when only JVM fixtures ran.

## Skill-documentation changes

No documentation-specific test/lint task is defined. Review YAML frontmatter
(`name` matches the directory, actionable `description`), reference links,
source symbols, and named tests against the current checkout. Replay the five
[routing examples](architecture.md#routing-acceptance-examples), checking both
selected and deliberately excluded context. Verify host discovery separately;
reading Markdown successfully does not prove automatic invocation.

Use `git -C /home/runner/work/ssYoutube/ssYoutube diff --check` for whitespace.
Documentation-only edits do not require APK compilation or application tests.
Keep reference maintenance scoped; do not add a new framework to validate Skills.

Source guidance:
[/home/runner/work/ssYoutube/ssYoutube/README.md](/home/runner/work/ssYoutube/ssYoutube/README.md#build-and-test).
