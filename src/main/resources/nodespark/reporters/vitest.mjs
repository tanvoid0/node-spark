// Custom Vitest reporter: walks the finished task tree and emits TeamCity service messages
// for IntelliJ's SMTRunner. Loaded via `--reporter=<absolute path to this file>`.
//
// Streaming via onTaskUpdate was tried and dropped: task/result mutate in place as vitest
// runs, so an update can fire before a parent's testSuiteStarted has gone out, and ordering
// across worker threads isn't guaranteed. Building the whole tree once in onFinished is the
// only ordering vitest actually gives us.
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { createTree, fileLocation, stringify } = require('./tc.js');

// vitest 1.6 quirk (verified empirically, not documented): @vitest/utils' processError()
// unconditionally overwrites err.expected/err.actual with stringify(undefined) === 'undefined'
// for every error, diff or not — so their mere presence no longer means "has a real diff".
// err.showDiff is the only reliable signal chai/vitest give us that a comparison actually ran.
function failureOf(err) {
  if (!err) return { message: '', isError: true };
  const hasDiff = err.showDiff === true;
  return {
    message: err.message == null ? String(err) : err.message,
    details: err.stack,
    expected: hasDiff ? stringify(err.expected) : undefined,
    actual: hasDiff ? stringify(err.actual) : undefined,
    isError: !hasDiff,
  };
}

function walkTask(task, tree, parentId, filepath) {
  const line = task.location && task.location.line;

  if (task.type === 'suite') {
    const id = tree.newId();
    tree.suiteStarted(id, parentId, task.name, fileLocation(filepath, line));
    for (const child of task.tasks || []) walkTask(child, tree, id, filepath);
    tree.suiteFinished(id, task.name, task.result && task.result.duration);
    return;
  }

  // type 'test' or 'custom' (benchmarks) — both are leaves.
  const state = task.result && task.result.state;
  if (state === 'skip' || state === 'todo' || task.mode === 'skip' || task.mode === 'todo') {
    // tc.js's testIgnored carries no parentNodeId — it's a standalone terminal message, so it
    // needs no matching testStarted/testFinished pair.
    tree.testIgnored(tree.newId(), task.name);
    return;
  }

  const id = tree.newId();
  const duration = task.result && task.result.duration;
  tree.testStarted(id, parentId, task.name, fileLocation(filepath, line));
  if (state === 'fail') {
    tree.testFailed(id, task.name, failureOf((task.result.errors || [])[0]), duration);
  }
  tree.testFinished(id, task.name, duration);
}

function walkFile(file, tree) {
  const name = path.basename(file.name || file.filepath || 'file');
  const id = tree.newId();
  tree.suiteStarted(id, tree.ROOT, name, fileLocation(file.filepath));

  if (file.tasks && file.tasks.length) {
    for (const task of file.tasks) walkTask(task, tree, id, file.filepath);
  } else if (file.result && file.result.state === 'fail') {
    // Collection/import error: the file blew up before producing any tests (e.g. a bad
    // require). No child tasks exist, so surface the failure as a synthetic test node.
    const tid = tree.newId();
    tree.testStarted(tid, id, 'collection error');
    tree.testFailed(tid, 'collection error', failureOf((file.result.errors || [])[0]));
    tree.testFinished(tid, 'collection error');
  }

  tree.suiteFinished(id, name, file.result && file.result.duration);
}

export default class NodeSparkVitestReporter {
  onInit() {
    this.tree = createTree();
    this.tree.start();
  }

  onFinished(files = [], errors = []) {
    const tree = this.tree;
    for (const file of files) walkFile(file, tree);

    if (errors.length) {
      // Unhandled errors (e.g. thrown outside any test, uncaught rejections) aren't attached
      // to any file — give them a home so they aren't silently dropped.
      const suiteId = tree.newId();
      tree.suiteStarted(suiteId, tree.ROOT, 'Unhandled Errors');
      for (const err of errors) {
        const id = tree.newId();
        const name = (err && err.message) || 'unhandled error';
        tree.testStarted(id, suiteId, name);
        tree.testFailed(id, name, failureOf(err));
        tree.testFinished(id, name);
      }
      tree.suiteFinished(suiteId, 'Unhandled Errors');
    }

    tree.finish();
  }
}
