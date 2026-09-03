# Known issues and future work

## Gutter icons are not verified working in Community

**Status:** partially fixed, not confirmed on a real Community IDE.

`NodeTestLineMarkerProvider` used to anchor each marker to the line its PSI leaf started on. That
works where a JavaScript plugin supplies one leaf per token, and it is what every Ultimate install
has. Community has no JavaScript plugin, and the marker was never produced there.

The provider now scans by line rather than by leaf (`collectSlowLineMarkers`), and
`NodeTestLineMarkerProviderTest` proves that path over a plain-text `.js` PSI: 3 markers where the
old code produced 0.

**What is still unproven.** With the bundled JavaScript plugin disabled in IntelliJ IDEA Ultimate
2026.2 — the nearest stand-in for Community — the icons still do not appear, and the
`describe`/`it` **Structure view is empty as well**. So on that setup no PSI-based extension of
this plugin reaches a `.js` file, not just the line marker provider. The test fixture and that IDE
disagree about what a `.js` file is:

| | `.js` file type | PSI shape | Markers |
|---|---|---|---|
| Test fixture (no TextMate) | plain text | one leaf for the whole file | 3 ✅ |
| Ultimate, JS plugin disabled | TextMate | not established | 0 ❌ |

Community bundles TextMate too, so the TextMate path is the one that matters.

**Next step.** Disable `org.jetbrains.plugins.textmate` alongside the JavaScript plugins and open
a test file:

- icons appear → TextMate's PSI is what suppresses them, and since Community bundles TextMate the
  feature is still broken there. The fix would have to stop depending on PSI shape — mark up the
  editor directly (an `EditorFactoryListener` adding gutter icons off the document text) rather
  than going through a `LineMarkerProvider`.
- icons still missing → the daemon is not running PSI passes on `.js` at all in that
  configuration, and the same alternative applies.

Confirm in the sandbox as well: `./gradlew runIde` launches Community 2024.1.7 with `demo/` open,
which is the real target and settles the question without any Ultimate variables.

Everything the README says about gutter icons should be treated as unverified for Community until
this is closed.

## ESLint / Prettier annotators assume a local file

`BasePlatformTestCase.doHighlighting()` on a fixture file fails with:

```
Failed to map temp:///src (filesystem com.intellij.openapi.vfs.ex.temp.TempFileSystem) into nio Path
```

An annotator calls `toNioPath()` (or something that does) on a `VirtualFile` that has no nio path.
Harmless for ordinary local projects, but a scratch file, a file in a remote or in-memory
filesystem, or a fixture will hit it. Worth a guard that skips the annotator when the file has no
real path, which would also let daemon-level tests run.

## Screenshots

`docs/screenshots/` is specified in [screenshots/README.md](screenshots/README.md) but empty — the
README references eight images that do not exist yet. Capturing them needs an IDE where the
gutter feature is actually working, so it is blocked behind the issue above.
