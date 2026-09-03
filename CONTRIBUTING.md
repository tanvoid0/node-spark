# Contributing to NodeSpark

Contributions are welcome. This file covers building the plugin, the two IDE variants, and the
rules for PRs and issues.

## Ground rules

1. **Be respectful.** Disagreement on technical decisions is fine; personal attacks are not.
   Follow the [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/).
2. **One concern per PR.** Bug fixes should not carry refactors; features should not fix unrelated
   bugs.
3. **Tests for bugs.** Include a regression test, or a note on why one isn't possible.
4. **No breaking changes without discussion.** Open an issue first if the change affects existing
   run configurations or SDK registrations.
5. **English only** in code, comments, commit messages and PR descriptions.

## Development setup

Requirements: **JDK 21** (Gradle wrapper included). Open the project in IntelliJ IDEA; the Gradle
project imports itself.

```bash
git clone https://github.com/tanvoid0/node-spark
cd node-spark

./gradlew compileKotlin   # compile
./gradlew test            # unit tests
./gradlew runIde          # sandbox IDE, opens demo/ automatically
./gradlew buildPlugin     # distributable zip -> build/distributions/
```

If your default JDK is too old to run Gradle itself, set `org.gradle.java.home` in
`~/.gradle/gradle.properties` — not in the repo's, which every clone shares.
`kotlin { jvmToolchain(21) }` already pins the compile JDK.

## Two IDE variants

The SM test-tree classes (`com.intellij.execution.testframework.sm.*`) live in the platform core
classloader up to build 261, but 262 moved them into a separate Test Runner plugin module, which a
plugin has to declare to see. A `<dependencies><module/>` block naming a module the IDE does not
know makes the plugin fail to load — so one jar cannot serve both, and the build runs twice:

```bash
./gradlew buildPlugin                    # 241-261  -> node-spark-241.x.zip
./gradlew buildPlugin -PideVariant=262   # 262+     -> node-spark-262.x.zip
```

Both variants build from the same source; only the descriptor and the compile classpath differ.
The branch number goes in *front* of the version (`241.0.2.0`, `262.0.2.0`) because a `-262`
suffix would be read as a semver pre-release and sort below the plain version.

The 262 build compiles against an installed 262+ IDE (`localIde262` in `gradle.properties`),
because 2026.2 is not published to the IntelliJ repository yet, and it skips searchable-options
indexing, which cannot drive a 262 IDE headless.

A release builds both, after bumping `pluginVersion` in `gradle.properties`.

Two other things moved in 262 and are handled per variant: JSON PSI became a separate bundled
plugin, and `isOpenProjectSettingsAfter` left the import-builder hierarchy.

## Screenshots

`docs/screenshots/` holds the images used by the README. See
[docs/screenshots/README.md](docs/screenshots/README.md) for what each one is supposed to show and
how to reproduce it.

## Submitting a PR

1. Fork and branch: `git checkout -b fix/describe-block-detection`
2. Make the change
3. `./gradlew test && ./gradlew buildPlugin` — must pass
4. Open a PR against `master` describing what changed and why

## Reporting issues

Include:

- IntelliJ IDEA version and edition
- NodeSpark version
- Node.js version and how it's installed (nvm, fnm, system)
- Test runner and version
- Steps to reproduce, expected vs. actual

## Feature requests

Open an issue tagged `enhancement` before writing code. Some things are deliberately out of scope
because they need platform APIs Community does not expose, or would duplicate what the project's
own tooling already does.
