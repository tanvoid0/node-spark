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

**Status:** fixed in 0.4.0 by registering a `coverageEngine` and a `coverageRunner`.

Coverage used to be a `Tools → Node Coverage` action that parsed `lcov.info` and painted the gutter
by hand, with no Run-with-Coverage button and no Coverage tool window. `NodeCoverageEngine` (plus
`NodeCoverageRunner`, `NodeCoverageSuite` and `NodeCoverageAnnotator` in
`src/main/kotlin/com/nodespark/coverage/`) now plugs `LcovParser` into the platform's own coverage
machinery, so all three of those come from the IDE.

Two things are worth knowing about the shape of it:

* The engine's view-extension hook and its deprecated suite factories differ between build
  branches, so `NodeCoverageEngineBase` lives in `src/variant241/` and `src/variant262/` rather than
  `src/main/` — see the comment in either copy. The 241 variant spans 2024.1 to 2026.1, and the
  hook changed inside that range (2024.3), so it carries the newer form as a plain method the JVM
  still dispatches to.
* The editor annotator asks `getQualifiedName(outputFile, psiFile)` BEFORE `getQualifiedNames`,
  because a source file's default "output file" is itself. Leave that at its null default and the
  tool window fills in correctly while every gutter stays blank — and `coverageProjectViewStatisticsApplicableTo`
  defaults to false, which is the same trap the other way round. Both are overridden.
* A file the report never mentions is shown at 0% rather than left out of the tree
  (`fillInfoForUncoveredFile` is overridden; the default returns null, which hides exactly the files
  worth seeing). Its denominator is physical lines, since only the report knows which lines are
  executable — so an untested file reads a little pessimistically until the runner is told to report
  all files (`--coverage.all`, jest's `collectCoverageFrom`).

## Screenshots

`docs/screenshots/` is specified in [screenshots/README.md](screenshots/README.md), and every shot
in that list now exists.
