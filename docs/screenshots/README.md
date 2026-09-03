# README screenshots

Images referenced by the top-level [README](../../README.md). Reproduce them from the bundled
`demo/` project so they stay consistent.

## Setup

```bash
cd demo && npm install
./gradlew runIde          # from the repo root; opens demo/ in a sandbox IDE
```

In the sandbox IDE, before shooting:

- **Dark theme**, default font size, no distraction-free mode
- Hide the tool windows the shot does not need (`Ctrl+Shift+F12` toggles all)
- Editor window ~1200px wide; crop tight to the region named below
- No personal paths in the title bar or breadcrumbs

Save as PNG, at the filename below, in this directory.

## Shot list

| File | Shows | How |
|---|---|---|
| `hero-test-tree.png` | Editor with gutter ▶ icons on the left, Run window with a green test tree on the right | Open `demo/src/math.test.js`, run the whole file, screenshot editor + Run window together |
| `gutter-run-icons.png` | Two or three ▶ arrows beside `describe` / `it` | Same file, editor only, crop to ~15 lines including the gutter |
| `test-tree-failure.png` | A failed assertion in the tree with the *Click to see difference* link | Temporarily break one expectation in `math.test.js`, run the file, shoot the Run window (revert the edit afterwards) |
| `debugger.png` | Stopped on a breakpoint: frames, variables, the highlighted line | Breakpoint inside a test in `math.test.js`, debug from the gutter |
| `npm-scripts.png` | ▶ beside each entry of `"scripts"` | Open `demo/package.json`, crop to the scripts block with the gutter |
| `coverage.png` | Green/red coverage stripes in the gutter | **Tools → Node Coverage → Run Tests with Coverage**, then open `demo/src/math.js` |
| `env-editor.png` | The `.env` key/value grid tab | Open `demo/.env`, switch to the table tab |
| `settings.png` | The NodeSpark settings page | `Settings → Tools → NodeSpark`, crop to the panel |

`.env` values in `demo/` are fixtures — no real credentials — but check the shot before committing
it anyway.
