'use strict';
/*
 * Jest reporter emitting TeamCity service messages for the IntelliJ SMTRunner tree.
 * See tc.js in this directory for the protocol notes (id rules, escaping, diff viewer).
 */
const path = require('path');
const { createTree, fileLocation, stringify } = require('./tc.js');

const tree = createTree();

module.exports = class NodeSparkJestReporter {
  constructor(globalConfig, options) {
    // When the IDE runs a single test it passes --testNamePattern, and jest then reports every
    // other test in the file as 'pending'. Those are filter casualties, not skipped tests, so they
    // are dropped entirely — otherwise running one test shows a tree full of ignored siblings.
    this._filtered = !!(globalConfig && globalConfig.testNamePattern);
  }

  onRunStart(results, options) {
    tree.start();
    // numTotalTests is 0 here (nothing is collected yet); only report a real count.
    if (results && results.numTotalTests > 0) tree.testCount(results.numTotalTests);
  }

  onTestFileStart(test) {}

  // jest 27 only calls onTestResult; jest 28+ calls onTestFileResult. Both receive identical args
  // — dedup on the testResult object so a jest version that fires both doesn't double-emit.
  onTestResult(test, testResult, aggregatedResult) {
    this.onTestFileResult(test, testResult, aggregatedResult);
  }

  onTestFileResult(test, testResult, aggregatedResult) {
    if (this._seen && this._seen.has(testResult)) return;
    (this._seen || (this._seen = new WeakSet())).add(testResult);
    const filePath = testResult.testFilePath;
    const fileName = path.basename(filePath);
    const fileId = tree.newId();
    tree.suiteStarted(fileId, tree.ROOT, fileName, fileLocation(filePath));

    // A file that crashed before any test ran (syntax error, throw at module scope, etc.)
    // has no testResults — surface the failure as a single child so the tree isn't empty.
    if (!testResult.testResults.length && (testResult.testExecError || testResult.failureMessage)) {
      const errId = tree.newId();
      const msg = testResult.failureMessage || (testResult.testExecError && testResult.testExecError.message) || 'Failed to run test file';
      tree.testStarted(errId, fileId, fileName, fileLocation(filePath));
      tree.testFailed(errId, fileName, { message: msg, isError: true });
      tree.testFinished(errId, fileName);
      tree.suiteFinished(fileId, fileName, testResult.perfStats.end - testResult.perfStats.start);
      return;
    }

    // ancestorTitles path (JSON-encoded, to avoid collisions on titles containing separators) ->
    // {id, name}, so sibling assertions under the same describe() share a suite node.
    const suiteIds = new Map([['[]', { id: fileId, name: fileName }]]);

    for (const a of testResult.testResults) {
      if (this._filtered && a.status === 'pending') continue;
      let parentId = fileId;
      const path_ = [];
      for (const title of a.ancestorTitles) {
        path_.push(title);
        const key = JSON.stringify(path_);
        let node = suiteIds.get(key);
        if (node === undefined) {
          const id = tree.newId();
          tree.suiteStarted(id, parentId, title, fileLocation(filePath, a.location && a.location.line));
          node = { id, name: title };
          suiteIds.set(key, node);
        }
        parentId = node.id;
      }

      const testId = tree.newId();
      const loc = fileLocation(filePath, a.location && a.location.line);
      tree.testStarted(testId, parentId, a.title, loc);

      if (a.status === 'failed') {
        for (const detail of a.failureDetails.length ? a.failureDetails : [{}]) {
          const mr = detail && detail.matcherResult;
          tree.testFailed(testId, a.title, {
            message: mr ? mr.message : (a.failureMessages[0] || ''),
            details: mr ? undefined : a.failureMessages.join('\n'),
            expected: mr ? stringify(mr.expected) : undefined,
            actual: mr ? stringify(mr.actual) : undefined,
            isError: !mr,
          });
        }
        tree.testFinished(testId, a.title, a.duration);
      } else if (a.status === 'passed') {
        tree.testFinished(testId, a.title, a.duration);
      } else {
        // pending/todo/skipped/disabled
        tree.testIgnored(testId, a.title, a.status);
      }
    }

    // Close in reverse insertion order (deepest suite first), file suite last.
    const nodes = Array.from(suiteIds.values()).reverse();
    for (const node of nodes) {
      if (node.id === fileId) continue;
      tree.suiteFinished(node.id, node.name);
    }
    tree.suiteFinished(fileId, fileName, testResult.perfStats.end - testResult.perfStats.start);
  }

  onRunComplete(contexts, results) {
    tree.finish();
  }
};
