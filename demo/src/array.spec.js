// Uses .spec.js extension — tests that NodeSpark detects both .test.js and .spec.js

function groupBy(arr, keyFn) {
  return arr.reduce((acc, item) => {
    const key = keyFn(item);
    (acc[key] = acc[key] || []).push(item);
    return acc;
  }, {});
}

function chunk(arr, size) {
  const result = [];
  for (let i = 0; i < arr.length; i += size) {
    result.push(arr.slice(i, i + size));
  }
  return result;
}

function flatten(arr) {
  return arr.reduce((acc, val) =>
    Array.isArray(val) ? acc.concat(flatten(val)) : acc.concat(val), []);
}

function unique(arr) {
  return [...new Set(arr)];
}

function zip(...arrays) {
  const maxLen = Math.max(...arrays.map(a => a.length));
  return Array.from({ length: maxLen }, (_, i) => arrays.map(a => a[i]));
}

describe('groupBy', () => {
  const people = [
    { name: 'Alice', dept: 'eng' },
    { name: 'Bob', dept: 'eng' },
    { name: 'Carol', dept: 'hr' },
  ];

  test('groups by department', () => {
    const result = groupBy(people, p => p.dept);
    expect(result.eng).toHaveLength(2);
    expect(result.hr).toHaveLength(1);
  });

  test('groups numbers by even/odd', () => {
    const result = groupBy([1, 2, 3, 4], n => n % 2 === 0 ? 'even' : 'odd');
    expect(result.even).toEqual([2, 4]);
    expect(result.odd).toEqual([1, 3]);
  });
});

describe('chunk', () => {
  test('splits array into chunks', () => {
    expect(chunk([1, 2, 3, 4, 5], 2)).toEqual([[1, 2], [3, 4], [5]]);
  });

  test('chunk size larger than array', () => {
    expect(chunk([1, 2], 5)).toEqual([[1, 2]]);
  });
});

describe('flatten', () => {
  test('flattens nested arrays', () => {
    expect(flatten([1, [2, [3, [4]]]])).toEqual([1, 2, 3, 4]);
  });

  test('flat array unchanged', () => {
    expect(flatten([1, 2, 3])).toEqual([1, 2, 3]);
  });
});

describe('unique', () => {
  test('removes duplicates', () => {
    expect(unique([1, 2, 2, 3, 1])).toEqual([1, 2, 3]);
  });

  test('strings deduplicated', () => {
    expect(unique(['a', 'b', 'a'])).toEqual(['a', 'b']);
  });
});

describe('zip', () => {
  test('zips two arrays', () => {
    expect(zip([1, 2], ['a', 'b'])).toEqual([[1, 'a'], [2, 'b']]);
  });

  test('handles unequal length arrays', () => {
    expect(zip([1, 2, 3], ['a'])).toEqual([[1, 'a'], [2, undefined], [3, undefined]]);
  });
});
