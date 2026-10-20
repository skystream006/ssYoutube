# Build, dependencies, and release

## Locate the change

| Source | Responsibility |
| --- | --- |
| `/home/runner/work/ssYoutube/ssYoutube/build.gradle` | Android application plugin version |
| `/home/runner/work/ssYoutube/ssYoutube/settings.gradle` | Single `:app` module; Google, Maven Central, restricted JitPack resolution |
| `/home/runner/work/ssYoutube/ssYoutube/gradle.properties` | JVM/AndroidX/resource configuration |
| `/home/runner/work/ssYoutube/ssYoutube/gradle/wrapper/gradle-wrapper.properties` | Wrapper distribution |
| `/home/runner/work/ssYoutube/ssYoutube/gradlew` | Existing build entry point |
| `/home/runner/work/ssYoutube/ssYoutube/app/build.gradle` | SDK levels, Git version, signing, Java/desugaring, dependencies, packaging, `printVersionName` |
| `/home/runner/work/ssYoutube/ssYoutube/app/proguard-rules.pro` | mpv wrapper preservation |
| `/home/runner/work/ssYoutube/ssYoutube/.github/workflows/manual-release.yml` | Manual main-branch APK build/publication |
| `/home/runner/work/ssYoutube/ssYoutube/.github/workflows/cleanup-releases.yml` | Scheduled/manual retention of five newest releases and associated tag cleanup |

## Build contracts

The checked-in toolchain is Gradle 8.7, AGP 8.5.2, JDK 17, Java 8 source/target
with core-library desugaring, compile/target SDK 34, and minimum API 21.
Re-read build declarations before an upgrade rather than treating these pins as
permanent. AppCompat/WebKit support shell behavior; Media3 common/UI provide
controls; the mpv library supplies JNI/libmpv/FFmpeg and CA data; NewPipeExtractor
supplies extraction. Coroutines support the native wrapper. JUnit 4 is the JVM
test dependency. Do not widen JitPack beyond the required extractor dependencies.

`revision` counts first-parent commits on HEAD. `versionCode` is revision + 1;
`versionName` formats revision as `1.0.%02d`. Full history is mandatory; do not
work around the shallow-clone guard or manually bump a version file.
`printVersionName` is the workflow's source of truth.

The manual workflow checks out full main history, uses JDK 17, runs `test
assembleDebug`, uploads the renamed APK artifact, and creates a missing stable
release. Existing releases/assets remain unchanged. The distributed APK uses
the configured debug signing identity; do not substitute `assembleRelease` or a
new key without considering installed-app update compatibility. Never copy
keystore contents or signing material into Skills.

Cleanup includes drafts/prereleases, deletes releases by ID, and preserves tags
referenced by retained releases. Reviewing it is not permission to run it.
The README mentions automatic main/PR builds, but the inspected workflow
directory contains only these two definitions; verify actual workflows before
claiming automatic publication or checks.

## Consumers and validation

Version, package, signing, or release-asset changes require
[updater contract](updates.md) review and `UpdateChecksTest` /
`GitHubUpdateClientTest` as relevant. Native dependency changes require
[playback](../../playback/SKILL.md), ABI/device checks, and the affected playback
tests. An mpv library upgrade must also review `prepareCertificates` and its
versioned CA-cache filename in
`/home/runner/work/ssYoutube/ssYoutube/app/src/main/java/com/skystream/ssyoutube/MpvPlayer.java`.
Packaged ABIs currently include ARMv7, ARM64, x86, and x86_64.

Use [common commands/prerequisites](../../repo-router/references/validation.md)
for build, lint, and full tests after build/dependency changes. Confirm version
output with:

```sh
/home/runner/work/ssYoutube/ssYoutube/gradlew -p /home/runner/work/ssYoutube/ssYoutube -q :app:printVersionName
```

Workflow-only changes need review of triggers, permissions, branch guards, and
asset/tag handling, not an unauthorized publication run. No dedicated workflow
test harness is checked in.

Documentation: [Build and test](/home/runner/work/ssYoutube/ssYoutube/README.md#build-and-test)
and [Third-party licensing](/home/runner/work/ssYoutube/ssYoutube/README.md#third-party-licensing).
Native redistribution obligations are not covered by the wrapper license alone.
