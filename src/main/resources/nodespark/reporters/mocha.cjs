'use strict';
/*
 * Mocha reporter -> IntelliJ SMTRunner tree via TeamCity service messages.
 * Loaded by mocha itself (`--reporter <absolute path>`), so `require('mocha')` here resolves
 * against the user's project - guard it, older/newer mocha may not export Runner.constants.
 */
const tc = require('./tc.js');

module.exports = function NodeSparkMochaReporter(runner, options) {
  let mochaConstants;
  try { mochaConstants = require('mocha').Runner.constants; } catch (e) { /* fall through to string names */ }
  const C = (options && options.constants) || mochaConstants || {};

  const EVENT_RUN_BEGIN = C.EVENT_RUN_BEGIN || 'start';
  const EVENT_SUITE_BEGIN = C.EVENT_SUITE_BEGIN || 'suite';
  const EVENT_SUITE_END = C.EVENT_SUITE_END || 'suite end';
  const EVENT_TEST_BEGIN = C.EVENT_TEST_BEGIN || 'test';
  const EVENT_TEST_PASS = C.EVENT_TEST_PASS || 'pass';
  const EVENT_TEST_FAIL = C.EVENT_TEST_FAIL || 'fail';
  const EVENT_TEST_PENDING = C.EVENT_TEST_PENDING || 'pending';
  const EVENT_RUN_END = C.EVENT_RUN_END || 'end';

  const tree = tc.createTree();
  const stack = [tree.ROOT]; // open suite node ids; the root suite itself is never pushed
  const nodeIds = new WeakMap(); // test -> nodeId, so pass/fail/pending find the started node

  runner.on(EVENT_RUN_BEGIN, function () {
    tree.start();
    tree.testCount(runner.total);
  });

  runner.on(EVENT_SUITE_BEGIN, function (suite) {
    if (suite.root) return; // mocha's implicit top-level suite has no title, skip straight to root id
    const id = tree.newId();
    suite.__nodeId = id;
    tree.suiteStarted(id, stack[stack.length - 1], suite.title, tc.fileLocation(suite.file));
    stack.push(id);
  });

  runner.on(EVENT_SUITE_END, function (suite) {
    if (suite.root) return;
    stack.pop();
    tree.suiteFinished(suite.__nodeId, suite.title, suite.duration);
  });

  runner.on(EVENT_TEST_BEGIN, function (test) {
    const id = tree.newId();
    nodeIds.set(test, id);
    tree.testStarted(id, stack[stack.length - 1], test.title, tc.fileLocation(test.file));
  });

  runner.on(EVENT_TEST_PASS, function (test) {
    tree.testFinished(nodeIds.get(test), test.title, test.duration);
  });

  runner.on(EVENT_TEST_FAIL, function (test, err) {
    // A failing before/beforeEach hook shows up here as a `test` of type 'hook' that never got
    // EVENT_TEST_BEGIN - start it on the fly, otherwise the failure has nowhere to render.
    // ponytail: mocha itself already fired EVENT_TEST_BEGIN for the real test the hook guarded,
    // and never sends it a matching pass/fail/pending - that node is left "running" in the IDE.
    // Stock mocha reporters (spec, dot) have the same gap; fixing it needs the undocumented
    // hook.ctx.currentTest back-reference. Upgrade if a stuck spinner in the tree gets reported.
    let id = nodeIds.get(test);
    if (id === undefined) {
      id = tree.newId();
      nodeIds.set(test, id);
      tree.testStarted(id, stack[stack.length - 1], test.title, tc.fileLocation(test.file));
    }
    const hasDiff = !!err && (err.showDiff === true || (err.expected !== undefined && err.actual !== undefined));
    tree.testFailed(id, test.title, {
      message: err && err.message,
      details: err && err.stack,
      expected: hasDiff ? tc.stringify(err.expected) : undefined,
      actual: hasDiff ? tc.stringify(err.actual) : undefined,
      isError: !hasDiff,
    }, test.duration);
    tree.testFinished(id, test.title, test.duration);
  });

  runner.on(EVENT_TEST_PENDING, function (test) {
    // Plain it.skip() never fires EVENT_TEST_BEGIN; a runtime this.skip() does. Cover both.
    let id = nodeIds.get(test);
    if (id === undefined) {
      id = tree.newId();
      nodeIds.set(test, id);
      tree.testStarted(id, stack[stack.length - 1], test.title, tc.fileLocation(test.file));
    }
    tree.testIgnored(id, test.title);
  });

  runner.on(EVENT_RUN_END, function () {
    tree.finish();
  });
};
