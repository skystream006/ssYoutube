# Build, dependencies, and releases

Use the actual absolute checkout root if different from the paths below.

## Configuration map

| Absolute path | Purpose |
| --- | --- |
| `/home/runner/work/ssYoutube/ssYoutube/build.gradle` | Android application plugin |
| `/home/runner/work/ssYoutube/ssYoutube/settings.gradle` | Single `:app` module and dependency repository restrictions |
| `/home/runner/work/ssYoutube/ssYoutube/gradle.properties` | JVM, AndroidX, resource settings |
| `/home/runner/work/ssYoutube/ssYoutube/gradle/wrapper/gradle-wrapper.properties` | Gradle distribution |
| `/home/runner/work/ssYoutube/ssYoutube/gradlew` | Existing build entry point |
| `/home/runner/work/ssYoutube/ssYoutube/app/build.gradle` | SDK/Java settings, versions, signing configuration, dependencies, packaging, `printVersionName` |
| `/home/runner/work/ssYoutube/ssYoutube/app/proguard-rules.pro` | mpv wrapper keep rule |
| `/home/runner/work/ssYoutube/ssYoutube/.github/workflows/manual-release.yml` | Explicit main-branch APK build/test/publication |
| `/home/runner/work/ssYoutube/ssYoutube/.github/workflows/cleanup-releases.yml` | Scheduled/manual release and tag retention |
| `/home/runner/work/ssYoutube/ssYoutube/README.md` | Build prerequisites, native packaging caveats, distribution licensing |

## Toolchain and dependency boundaries

Current configuration uses Gradle 8.7 / AGP 8.5.2 / JDK 17, Java 8 source/target
with desugaring, compile/target API 34, and minimum API 21. Read configuration
before changing these; do not use this reference as a version lockfile.

- Shell: AppCompat and AndroidX WebKit.
- Playback: Media3 common/UI controls, mpv Android library, NewPipeExtractor;
  Kotlin coroutines support the mpv wrapper. There is no ExoPlayer dependency.
- Core-library desugaring preserves use of newer Java APIs on old Android.
- Tests: JUnit 4 and built-in Node test runner; no npm package tree.
- Dependencies resolve through Google Maven, Maven Central, and restricted JitPack
  for NewPipe/nanojson. Preserve repository scoping and license notices.

The mpv dependency packages JNI/native libraries and CA material for supported
ABIs. Consult playback before upgrading it: `MpvPlayer.prepareCertificates` uses
a versioned cache filename that must stay aligned, and native behavior needs
device verification. Do not copy upstream library implementations into the app.

## Version and release contract

- Let N be the first-parent commit count at HEAD: versionCode is N + 1 and
  versionName is `1.0.%02d` formatted with N. Full Git history is required even
  for Gradle configuration. Versions are not manually bumped.
- `printVersionName` exposes the name to automation. Manual release checks out
  full main history, uses JDK 17, runs `test assembleDebug`, names the APK from that
  task, and publishes a missing stable release tagged with `v` plus the version.
- The workflow distributes the debug APK using the configured debug signing key.
  Do not silently change identity/signing: installed applications validate signers.
  Do not load, copy, or document keystore contents.
- Existing releases/assets are left unchanged by manual publication. Workflow
  artifacts alone are not visible to the in-app GitHub Releases updater.
- Cleanup retains the five newest releases, deletes older releases by ID, and
  removes tags only when not shared by a retained release and the exact ref exists.
- The checked-in workflow set has manual release and cleanup. README references
  to automatic main/PR builds do not establish a corresponding workflow here.

## Validation and scope

Use [validation commands](../../repo-router/references/validation.md).
Build/dependency changes warrant full JVM tests, debug build, and lint; also check
`printVersionName` when version calculation changes. Do not execute destructive
release cleanup or publish a release just to validate a workflow edit.

There is no dedicated workflow test suite. Review event/branch guards, permissions,
full-history checkout, version/artifact naming, and release-retention semantics.
If investigating a reported CI failure, inspect actual workflow runs and job logs.
Native upgrades need playback device/ABI checks; update contract changes need the
tests in [updates](updates.md). Keep unrelated runtime implementations unloaded.

## Documentation

[Build and test](/home/runner/work/ssYoutube/ssYoutube/README.md#build-and-test)
documents toolchain, native packaging, Git versioning, and publication.
[Third-party licensing](/home/runner/work/ssYoutube/ssYoutube/README.md#third-party-licensing)
explains why the wrapper license alone does not cover native redistribution.
