const { capitalize, reverseString, isPalindrome, truncate, countWords, camelToSnake } = require('./strings');

describe('capitalize', () => {
  test('capitalizes first letter', () => {
    expect(capitalize('hello')).toBe('Hello');
  });

  test('lowercases the rest', () => {
    expect(capitalize('hELLO')).toBe('Hello');
  });

  test('handles empty string', () => {
    expect(capitalize('')).toBe('');
  });

  test('handles null', () => {
    expect(capitalize(null)).toBe(null);
  });
});

describe('reverseString', () => {
  test('reverses a string', () => {
    expect(reverseString('hello')).toBe('olleh');
  });

  test('handles single char', () => {
    expect(reverseString('a')).toBe('a');
  });
});

describe('isPalindrome', () => {
  test('racecar is palindrome', () => {
    expect(isPalindrome('racecar')).toBe(true);
  });

  test('A man a plan a canal Panama', () => {
    expect(isPalindrome('A man a plan a canal Panama')).toBe(true);
  });

  test('hello is not palindrome', () => {
    expect(isPalindrome('hello')).toBe(false);
  });
});

describe('truncate', () => {
  test('does not truncate short strings', () => {
    expect(truncate('hi', 10)).toBe('hi');
  });

  test('truncates long strings with ellipsis', () => {
    expect(truncate('Hello World', 8)).toBe('Hello...');
  });

  test('supports custom suffix', () => {
    expect(truncate('Hello World', 7, '~')).toBe('Hello ~');
  });
});

describe('countWords', () => {
  test('counts words in sentence', () => {
    expect(countWords('hello world foo')).toBe(3);
  });

  test('handles extra spaces', () => {
    expect(countWords('  hello   world  ')).toBe(2);
  });

  test('empty string returns 0', () => {
    expect(countWords('')).toBe(0);
  });
});

describe('camelToSnake', () => {
  test('converts camelCase to snake_case', () => {
    expect(camelToSnake('camelCase')).toBe('camel_case');
  });

  test('handles multiple words', () => {
    expect(camelToSnake('myVariableName')).toBe('my_variable_name');
  });

  test('already lowercase unchanged', () => {
    expect(camelToSnake('hello')).toBe('hello');
  });
});
