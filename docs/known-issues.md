# Known issues and future work

## Gutter icons no longer go through PSI

**Status:** fixed in 0.3.0 by dropping the `LineMarkerProvider` entirely.

The icons were a `codeInsight.lineMarkerProvider`, so they only ever appeared where the daemon ran
a PSI pass over the test file. Community has no JavaScript plugin, TextMate owns `.js` there, and
no marker was produced — reported again on 2026.1.5.

`NodeTestGutterMarkup` is an `editorFactoryListener` instead: on an editor for a test file it parses
the text with `TestOutline` (which never needed PSI) and adds gutter icons to the editor's own
markup model, refreshing on every document change and after a run finishes. Nothing in that path
depends on which languages the IDE has plugins for.

**Still PSI-based, so still broken in Community:** the `describe`/`it` **Structure view**
(`lang.psiStructureViewFactory`) and the ESLint / Prettier annotators. The structure view has the
same fix available — a tool window fed from `TestOutline` rather than from PSI — and is worth doing
next.

## ESLint / Prettier annotators assume a local file

`BasePlatformTestCase.doHighlighting()` on a fixture file fails with:

```
Failed to map temp:///src (filesystem com.intellij.openapi.vfs.ex.temp.TempFileSystem) into nio Path
```

An annotator calls `toNioPath()` (or something that does) on a `VirtualFile` that has no nio path.
Harmless for ordinary local projects, but a scratch file, a file in a remote or in-memory
filesystem, or a fixture will hit it. Worth a guard that skips the annotator when the file has no
real path, which would also let daemon-level tests run.

## Coverage is manual, not IDE-native

**Status:** future enhancement.

`NodeCoverageActions`/`NodeCoverageService` (`src/main/kotlin/com/nodespark/coverage/`) run the
suite via a plain `Tools → Node Coverage` menu action, parse the `lcov.info` it writes, and paint
covered/uncovered stripes into the editor gutter by hand. That's the whole feature today: no
`com.intellij.coverageEngine`/`CoverageRunner` registration, so there is no Run-with-Coverage button
next to Run/Debug on a test or a file, and no Coverage tool window with per-file or per-function
percentages — the IDE has no idea coverage data exists.

Doing this natively means implementing `CoverageEngine`, `CoverageRunner` and
`CoverageAnnotator` for `LCOV_RELATIVE`-shaped data (`LcovParser` already does the hard part —
parsing — and could be reused as the `CoverageRunner`'s loader) and registering the run configs'
executor so `ExecutionRegistry` offers a coverage executor alongside Run and Debug.

## Screenshots

`docs/screenshots/` is specified in [screenshots/README.md](screenshots/README.md); `coverage.png`
is still missing since the feature it would show is only the basic version above.
