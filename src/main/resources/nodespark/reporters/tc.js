'use strict';
/*
 * TeamCity service-message emitter for the IntelliJ SMTRunner test tree.
 *
 * Zero dependencies, CommonJS, no npm install into the user's project: this file is shipped
 * inside the NodeSpark plugin and passed to the test runner by absolute path.
 *
 * Protocol notes that are easy to get wrong (all verified against the platform implementation):
 *  - The IDE runs in "id based" mode (SMTRunnerConsoleProperties.setIdBasedTestTree(true)).
 *    In that mode EVERY test/suite message must carry name=, or it is rejected at parse time.
 *  - nodeId must be unique and never '0' — '0' is the invisible root (TreeNodeEvent.ROOT_NODE_ID).
 *    parentNodeId must name a node that already exists and has not finished, otherwise the whole
 *    subtree is silently dropped and the user sees an empty tree.
 *  - duration is an integer count of milliseconds.
 *  - The "Click to see difference" diff viewer is driven purely by non-empty expected= and actual=.
 *    type='comparisonFailure' is NOT consulted by IntelliJ.
 *  - error= is a presence flag, not a value: any value renders the node as an Error rather than a
 *    failed assertion.
 *  - Each message must occupy its own line and end with '\n'.
 */

// Capture the original bound write at load time. Jest's DefaultReporter._wrapStdio and Vitest both
// monkey-patch process.stdout.write and buffer it until the run completes, which would reorder or
// swallow our messages. Writing through the original binding sidesteps that entirely.
const write = process.stdout.write.bind(process.stdout);

const BACKSLASH = String.fromCharCode(92);

/** The 9 escapes the platform's parser understands (MapSerializerUtil.STD_ESCAPER). */
function escape(value) {
  if (value === null || value === undefined) return '';
  return String(value)
    .replace(/\|/g, '||')
    .replace(/'/g, "|'")
    .replace(/\n/g, '|n')
    .replace(/\r/g, '|r')
    .replace(/\[/g, '|[')
    .replace(/\]/g, '|]')
    .replace(/\u0085/g, '|x')
    .replace(/\u2028/g, '|l')
    .replace(/\u2029/g, '|p');
}

function message(name, attrs) {
  let out = '##teamcity[' + name;
  for (const key of Object.keys(attrs)) {
    const value = attrs[key];
    // Drop absent attributes, but keep empty strings and 0 — 'duration=0' is meaningful.
    if (value === null || value === undefined) continue;
    out += " " + key + "='" + escape(value) + "'";
  }
  write(out + ']\n');
}

/**
 * A location the IDE can navigate to. We use the platform's own FileUrlProvider scheme
 * (protocol 'file', path '<file>[:<line>[:<column>]]', 1-based line), so no custom
 * SMTestLocator is needed on the IDE side.
 *
 * ponytail: line-based URLs go stale as a file is edited, which only affects the persisted
 * gutter status icon, not navigation. Switch to a name-based 'test://path.suite.name' scheme
 * plus a custom locator if stale gutter icons become a real complaint.
 */
function fileLocation(file, line) {
  if (!file) return undefined;
  // Windows paths arrive from the runners with native separators.
  const p = String(file).split(BACKSLASH).join('/');
  return line ? 'file://' + p + ':' + line : 'file://' + p;
}

/** Monotonic node ids. Starts at 1 because '0' is reserved for the invisible root. */
function createTree() {
  let nextId = 1;
  const ROOT = '0';

  function newId() { return String(nextId++); }

  return {
    ROOT,
    newId,

    start() {
      message('enteredTheMatrix', {});
      message('testingStarted', {});
    },

    testCount(count) {
      message('testCount', { count: count });
    },

    suiteStarted(id, parentId, name, location) {
      message('testSuiteStarted', {
        nodeId: id, parentNodeId: parentId, name: name,
        locationHint: location, running: 'true',
      });
    },

    suiteFinished(id, name, durationMs) {
      message('testSuiteFinished', {
        nodeId: id, name: name,
        duration: durationMs == null ? undefined : Math.round(durationMs),
      });
    },

    testStarted(id, parentId, name, location) {
      message('testStarted', {
        nodeId: id, parentNodeId: parentId, name: name,
        locationHint: location, running: 'true',
      });
    },

    testFinished(id, name, durationMs) {
      message('testFinished', {
        nodeId: id, name: name,
        duration: durationMs == null ? undefined : Math.round(durationMs),
      });
    },

    /**
     * @param failure {{message, details, expected, actual, isError}}
     *   expected/actual drive the diff viewer; isError marks a crash rather than an assertion.
     */
    testFailed(id, name, failure, durationMs) {
      const f = failure || {};
      const hasDiff = f.expected !== undefined && f.actual !== undefined;
      message('testFailed', {
        nodeId: id, name: name,
        // message= must be non-null or the platform throws constructing the event.
        message: f.message == null ? '' : f.message,
        details: f.details,
        expected: hasDiff ? f.expected : undefined,
        actual: hasDiff ? f.actual : undefined,
        error: f.isError ? 'yes' : undefined,
        duration: durationMs == null ? undefined : Math.round(durationMs),
      });
    },

    testIgnored(id, name, comment) {
      message('testIgnored', { nodeId: id, name: name, message: comment || '' });
    },

    stdOut(id, name, text) { message('testStdOut', { nodeId: id, name: name, out: text }); },
    stdErr(id, name, text) { message('testStdErr', { nodeId: id, name: name, out: text }); },

    finish() { message('testingFinished', {}); },
  };
}

/**
 * Renders a value for the diff viewer. Objects are pretty-printed so the diff is readable;
 * strings are passed through so we do not add spurious quotes to a plain string comparison.
 */
function stringify(value) {
  if (typeof value === 'string') return value;
  if (value === undefined) return 'undefined';
  try {
    return JSON.stringify(value, replacer(), 2);
  } catch (e) {
    return String(value);
  }
}

/** Handles cycles and the non-JSON types that show up in real assertions. */
function replacer() {
  const seen = new WeakSet();
  return function (key, value) {
    if (typeof value === 'bigint') return value.toString() + 'n';
    if (typeof value === 'function') return '[Function ' + (value.name || 'anonymous') + ']';
    if (value instanceof Error) return value.stack || String(value);
    if (value instanceof Map) return { '[Map]': Array.from(value.entries()) };
    if (value instanceof Set) return { '[Set]': Array.from(value.values()) };
    if (typeof value === 'object' && value !== null) {
      if (seen.has(value)) return '[Circular]';
      seen.add(value);
    }
    return value;
  };
}

module.exports = { createTree, fileLocation, stringify, escape };
