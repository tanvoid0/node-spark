/*
 * `node --test` custom reporter -> IntelliJ SMTRunner tree via TeamCity service messages.
 * Loaded as `--test-reporter=<absolute path> --test-reporter-destination=stdout`.
 *
 * node:test quirks worth remembering (verified empirically on node 24, do not trust memory):
 *  - There is no test:start/pass/fail for the file itself - only test:enqueue/dequeue/complete.
 *    We synthesize one suite node per file on the fly and close it ourselves at the end.
 *  - A describe block reports through the exact same test:start/pass/fail events as a leaf test;
 *    the only place its kind ('test' vs 'suite') is exposed ahead of test:start is test:enqueue's
 *    top-level data.type. test:pass/test:fail only carry it nested under data.details.type.
 *  - A failing assertion arrives as data.details.error = Error[ERR_TEST_FAILURE] wrapping the real
 *    AssertionError in .cause (code ERR_ASSERTION, has expected/actual). A failing suite's .cause
 *    is a plain string ("N subtests failed"), not an error - never call testFailed for a suite,
 *    only suiteFinished; the failing children already reported themselves.
 *  - test:stdout/test:stderr carry only {file, message}, no name/nesting - pipe to whatever node
 *    is currently open for that file.
 *  - tc.js writes straight to the real process.stdout.write; verified on node 24 that both that
 *    and a yielded string reach --test-reporter-destination=stdout intact and in order. We still
 *    yield nothing, since tc.js already owns the write and yielding would just double up.
 */
import { createRequire } from 'node:module';
import { basename } from 'node:path';
const require = createRequire(import.meta.url);
const { createTree, fileLocation, stringify } = require('./tc.js');

/** Unwraps node's ERR_TEST_FAILURE wrapper to the real AssertionError, if there is one. */
function toFailure(details) {
  const err = details.error;
  const real = err && err.cause && err.cause.code === 'ERR_ASSERTION' ? err.cause : err;
  const hasDiff = real && real.expected !== undefined && real.actual !== undefined;
  return {
    message: (real && real.message) || '',
    details: (real && real.stack) || (err && String(err.stack || err)),
    expected: hasDiff ? stringify(real.expected) : undefined,
    actual: hasDiff ? stringify(real.actual) : undefined,
    isError: !(real && real.code === 'ERR_ASSERTION'),
  };
}

export default async function* nodeSparkReporter(source) {
  const tree = createTree();
  tree.start();

  const files = new Map(); // file -> { stack: [{id,name,kind,nesting}], enqueued: [{name,nesting,type}] }

  function fileState(file) {
    let s = files.get(file);
    if (!s) {
      const id = tree.newId();
      tree.suiteStarted(id, tree.ROOT, basename(file), fileLocation(file));
      s = { stack: [{ id, name: basename(file), kind: 'suite', nesting: -1 }], enqueued: [] };
      files.set(file, s);
    }
    return s;
  }

  for await (const event of source) {
    const data = event.data;
    switch (event.type) {
      case 'test:enqueue':
        if (data.file) fileState(data.file).enqueued.push({ name: data.name, nesting: data.nesting, type: data.type });
        break;

      case 'test:start': {
        if (!data.file) break;
        const s = fileState(data.file);
        while (s.stack.length > 1 && s.stack[s.stack.length - 1].nesting >= data.nesting) s.stack.pop();
        const parent = s.stack[s.stack.length - 1];
        const idx = s.enqueued.findIndex((e) => e.name === data.name && e.nesting === data.nesting);
        const kind = idx === -1 ? 'test' : s.enqueued.splice(idx, 1)[0].type; // 'suite' or 'test'
        const id = tree.newId();
        const loc = fileLocation(data.file, data.line);
        if (kind === 'suite') tree.suiteStarted(id, parent.id, data.name, loc);
        else tree.testStarted(id, parent.id, data.name, loc);
        s.stack.push({ id, name: data.name, kind, nesting: data.nesting });
        break;
      }

      case 'test:pass':
      case 'test:fail': {
        if (!data.file) break;
        const s = files.get(data.file);
        if (!s || s.stack.length <= 1) break; // no matching test:start (shouldn't happen)
        const node = s.stack.pop(); // children of this node already popped themselves
        const duration = data.details && data.details.duration_ms;
        if (data.skip !== undefined && data.skip !== false || data.todo !== undefined && data.todo !== false) {
          tree.testIgnored(node.id, node.name, typeof data.skip === 'string' ? data.skip : (typeof data.todo === 'string' ? data.todo : ''));
        } else if (node.kind === 'suite') {
          tree.suiteFinished(node.id, node.name, duration);
        } else if (event.type === 'test:fail') {
          tree.testFailed(node.id, node.name, toFailure(data.details), duration);
          tree.testFinished(node.id, node.name, duration);
        } else {
          tree.testFinished(node.id, node.name, duration);
        }
        break;
      }

      case 'test:stdout':
      case 'test:stderr': {
        const s = files.get(data.file);
        if (!s) break;
        const top = s.stack[s.stack.length - 1];
        if (event.type === 'test:stdout') tree.stdOut(top.id, top.name, data.message);
        else tree.stdErr(top.id, top.name, data.message);
        break;
      }

      // test:plan/test:diagnostic carry no per-test id we can hang a message on (top-level
      // summary lines have no data.file at all) - nothing useful to render in the tree.
      default:
        break;
    }
  }

  // Safety net: close whatever is still open per file, innermost first. Normally this is just
  // the synthetic file suite, since every real test/suite closes itself via test:pass/test:fail.
  for (const s of files.values()) {
    while (s.stack.length) {
      const node = s.stack.pop();
      if (node.kind === 'suite') tree.suiteFinished(node.id, node.name);
      else tree.testFinished(node.id, node.name);
    }
  }
  tree.finish();
}
