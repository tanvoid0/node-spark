# NodeSpark

Node.js test runner for IntelliJ IDEA Community Edition. Brings WebStorm-style test execution to the free IDE — gutter run icons, right-click test running, and Node.js SDK management, without requiring a WebStorm license.

---

## Features

### Test Execution
- **Gutter run icons** — click ▶ next to any `describe`, `it`, or `test` block to run it in isolation
- **Right-click → Run Node Tests** — run all tests in a file from the editor or project tree
- **Single test filter** — when cursor is inside a test block, the run config auto-populates a name filter so only that test runs
- **Run tool window** — live output streamed directly into IntelliJ's Run panel

- **Pass/fail test tree** — bundled TeamCity reporters feed IntelliJ's native test runner UI: nested suites, per-test timings, "Click to see difference" on assertion failures, and green/red gutter icons after a run

### Runner Support
| Runner | Auto-detected | Single test | File run | Test tree |
|--------|--------------|-------------|----------|-----------|
| Jest   | ✅ | `--testNamePattern` | ✅ | ✅ |
| Vitest | ✅ | `-t` | ✅ | ✅ |
| Mocha  | ✅ | `--grep` | ✅ | ✅ |
| `node --test` | ✅ | `--test-name-pattern` | ✅ | ✅ |

Auto-detection reads the `test` script in `package.json` first, then the declared dependency, then a
config file, then `node_modules/.bin`, falling back to the built-in `node --test`. Declared intent wins
over what merely happens to be installed. Override via **Settings → NodeSpark**.

Runners are launched as `node <entry.js>` rather than through `node_modules/.bin/<name>.cmd`, because
the Windows shim expands `%VAR%` and eats `^` inside test-name filters.

### Beyond tests
- **package.json scripts** — gutter ▶ on every script, with a generated run configuration (npm/yarn/pnpm/bun detected)
- **Plain Node run/debug** — right-click any `.js`/`.ts` file → Run, or attach to a running `node --inspect` process
- **ESLint + Prettier** — inline ESLint annotations, `Reformat with Prettier` (Ctrl+Alt+Shift+P), optional format-on-save
- **Coverage** — **Tools → Node Coverage**: run tests with coverage or load an existing `coverage/lcov.info`, painted into the editor gutter
- **Structure view** — `describe`/`it` outline for test files
- **npm install banner** — offered when a project has dependencies but no `node_modules`

### Node.js SDK Management
- Registers a **Node.js SDK type** in **File → Project Structure → SDKs**
- Auto-detects Node.js from PATH, nvm, fnm, and common install directories
- Registers a **Node.js module type** in **File → Project Structure → Modules → Add → New Module**
- Imports an existing `package.json` via **Modules → Add → Import Module → Import module from external model → Node.js** (content root set, `node_modules` excluded from indexing; needs the bundled Java plugin, which owns that extension point)
- Per-module SDK selection in the module's **Dependencies** tab (Node.js SDKs only)
- Per-project SDK selection under **Settings → NodeSpark → Node.js SDK**
- Run configs resolve the SDK module-first, then project, then the first registered Node.js SDK — no manual path configuration needed

### File Detection
Recognises test files by:
- `*.test.js` / `*.test.ts` / `*.test.mjs` / `*.test.mts` / `*.test.cjs`
- `*.spec.js` / `*.spec.ts` and equivalents
- Files inside `__tests__/` directories

---

## Installation

### From JetBrains Marketplace _(recommended)_
`Settings → Plugins → Marketplace` → search **NodeSpark** → Install

### From Disk
1. Download the latest `.zip` from [Releases](../../releases)
2. `Settings → Plugins → ⚙ → Install Plugin from Disk`
3. Select the zip → restart IntelliJ

### Build from Source
Requirements: JDK 21, Gradle 9 (wrapper included). Building the 262 variant also needs a 262+ IDE installed.

```bash
git clone https://github.com/your-username/node-spark
cd node-spark

# Windows
build-plugin.bat

# macOS / Linux
chmod +x build-plugin.sh && ./build-plugin.sh
```

Plugin zip is output to `build/distributions/`.

#### Two IDE variants
The SM test-tree classes (`com.intellij.execution.testframework.sm.*`) sit in the platform core
classloader up to build 261, but build 262 moved them into a separate Test Runner plugin module,
which a plugin has to declare to see. A single jar cannot satisfy both — a `<dependencies><module/>`
block naming a module the IDE does not know makes the plugin fail to load — so build twice:

```bash
./gradlew buildPlugin                    # 241-261
./gradlew buildPlugin -PideVariant=262   # 262+
```

Both variants are built and tested from the same source; only the descriptor and the compile
classpath differ. The 262 build compiles against an installed 262+ IDE (`localIde262` in
`gradle.properties`) because 2026.2 is not published to the IntelliJ repository yet, and it skips
searchable-options indexing, which cannot drive a 262 IDE headless.

Two other things moved in 262 and are handled per variant: JSON PSI became a separate bundled
plugin, and `isOpenProjectSettingsAfter` left the import-builder hierarchy.

---

## Setup

### 1. Add a Node.js SDK
`File → Project Structure → Platform Settings → SDKs → +` → **Node.js**

Point to your Node.js installation directory:
- **Windows**: `C:\Program Files\nodejs`
- **macOS/Linux**: `/usr/local/bin` or the nvm version path

The SDK panel shows the detected version. Multiple SDKs (Node 18, 20, 22, etc.) can coexist.

### 2. Select SDK for your project
`Settings → NodeSpark → Node.js SDK` → pick from the dropdown

Set to **auto-detect** to always use the first available Node.js SDK.

### 3. Open a Node.js project with tests
Open any project containing Jest, Vitest, Mocha or `node --test` tests. No extra configuration needed.

---

## Usage

### Running a single test
Open a test file. A ▶ icon appears in the gutter next to each `describe`, `it`, and `test` declaration. Click it to run that test in isolation.

### Running all tests in a file
Right-click anywhere in a test file (editor or project tree) → **Run Node Tests**

### Editing a run configuration
After running once, the configuration appears in the run config dropdown. Click **Edit Configurations** to adjust:
- Test file path
- Test name filter (regex)
- Working directory
- Extra environment variables (`KEY=VAL,KEY2=VAL2`)

### Supported test syntax
```js
describe('suite name', () => {        // ▶ gutter icon
  test('test name', () => { ... });   // ▶ gutter icon
  it('also works', () => { ... });    // ▶ gutter icon

  test.only('focused', () => { ... }); // ▶ gutter icon
  it.skip('skipped', () => { ... });   // ▶ gutter icon
});
```

---

## Configuration

**Settings → NodeSpark** (global, all projects):

| Setting | Default | Description |
|---------|---------|-------------|
| Node.js path | `node` | Path to node binary, used when no SDK is configured |
| npm path | `npm` | Path to npm binary |
| Default env vars | `NODE_ENV=test` | Environment variables injected into every test run |
| Auto-detect runner | ✅ | Detect the runner from the test script, dependencies, config files, then `node_modules/.bin` |
| Runner override | — | Force a specific runner when auto-detect is off |

**Settings → NodeSpark → Node.js SDK** (per project):

| Setting | Description |
|---------|-------------|
| Node.js SDK | Which registered SDK this project uses. "auto-detect" picks the first available. |

---

## Demo Project

A working demo project is included under `demo/` with 50 tests across four files:

```
demo/
├── package.json          # Jest 29 + Vitest 1
├── jest.config.js
└── src/
    ├── math.js           # arithmetic + factorial + fibonacci
    ├── math.test.js      # 18 tests
    ├── strings.js        # string utilities
    ├── strings.test.js   # 14 tests
    ├── async.test.js     # 6 async/promise tests
    ├── array.spec.js     # 12 tests (.spec.js extension)
    └── ...
```

To run the demo:
```bash
cd demo
npm install
npm test
```

To test the plugin against it: open `demo/` as a project in IntelliJ with NodeSpark installed.

---

## Compatibility

| IntelliJ IDEA | Supported |
|---------------|-----------|
| 2024.1.x | ✅ |
| 2024.2.x | ✅ |
| 2025.1.x | ✅ |

**Requires**: IntelliJ IDEA Community or Ultimate (not Android Studio alone). Node.js installed separately.

**Does not require**: WebStorm, Node.js plugin, or any other paid IDE.

---

## Contributing

Contributions are welcome. Please read these guidelines before opening a PR.

### Ground Rules

1. **Be respectful.** Disagreement on technical decisions is fine; personal attacks are not. Follow the [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/).
2. **One concern per PR.** Bug fix PRs should not include refactors. Feature PRs should not fix unrelated bugs. Keep scope narrow.
3. **Tests for bugs.** If you fix a bug, include a regression test or a note explaining why one isn't possible.
4. **No breaking changes without discussion.** Open an issue first if your change affects existing run configurations or SDK registrations.
5. **English only** in code, comments, commit messages, and PR descriptions.

### Development Setup

```bash
git clone https://github.com/your-username/node-spark
cd node-spark
```

Open in IntelliJ IDEA. The Gradle project will import automatically.

**JDK requirement**: JDK 21 (the Android Studio bundled JBR works). Set `JAVA_HOME` accordingly or configure it in IntelliJ's Gradle settings.

```bash
# Compile
./gradlew compileKotlin

# Run plugin in a sandboxed IDE instance
./gradlew runIde

# Build distributable zip
./gradlew buildPlugin

# Run tests
./gradlew test
```

### Submitting a PR

1. Fork the repository
2. Create a branch: `git checkout -b fix/describe-block-detection`
3. Make your changes
4. Run `./gradlew buildPlugin` — must pass with no errors
5. Open a PR against `main` with a clear description of what changed and why

### Reporting Issues

Open an issue with:
- IntelliJ IDEA version
- NodeSpark version
- Node.js version and how it's installed (nvm, fnm, system)
- Test runner (Jest / Vitest / Mocha) and version
- Steps to reproduce
- What you expected vs. what happened

### Feature Requests

Open an issue tagged `enhancement` before writing code. Some things are intentionally out of scope (e.g. full TypeScript language support, debugging, npm script runner) because they overlap with WebStorm or require platform APIs not available in Community.

---

## Roadmap

- [ ] TeamCity protocol output parser (rich pass/fail tree in Run window)
- [ ] `npm test` / `package.json` script fallback when no runner binary found
- [ ] TypeScript file detection (`.test.ts`, `.spec.ts`) with `ts-jest` / `tsx` support
- [ ] Watch mode run configuration option
- [ ] Node.js debugger attach for test debugging
- [ ] Vitest workspace config detection

---

## License

[MIT](LICENSE)
