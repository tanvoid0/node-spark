const { add, subtract, multiply, divide, factorial, fibonacci } = require('./math');

describe('add', () => {
  test('adds two positive numbers', () => {
    expect(add(2, 3)).toBe(6);
  });

  test('adds negative numbers', () => {
    expect(add(-1, -2)).toBe(-3);
  });

  test('adds zero', () => {
    expect(add(5, 0)).toBe(5);
  });
});

describe('subtract', () => {
  test('subtracts two numbers', () => {
    expect(subtract(10, 4)).toBe(6);
  });

  test('result can be negative', () => {
    expect(subtract(3, 7)).toBe(-4);
  });
});

describe('multiply', () => {
  test('multiplies two numbers', () => {
    expect(multiply(3, 4)).toBe(12);
  });

  test('multiply by zero returns zero', () => {
    expect(multiply(99, 0)).toBe(0);
  });
});

describe('divide', () => {
  test('divides evenly', () => {
    expect(divide(10, 2)).toBe(5);
  });

  test('returns float for uneven division', () => {
    expect(divide(7, 2)).toBe(3.5);
  });

  test('throws on division by zero', () => {
    expect(() => divide(5, 0)).toThrow('Division by zero');
  });
});

describe('factorial', () => {
  test('factorial of 0 is 1', () => {
    expect(factorial(0)).toBe(1);
  });

  test('factorial of 5 is 120', () => {
    expect(factorial(5)).toBe(120);
  });

  test('throws on negative input', () => {
    expect(() => factorial(-1)).toThrow('Negative input');
  });
});

describe('fibonacci', () => {
  test('fib(0) is 0', () => {
    expect(fibonacci(0)).toBe(0);
  });

  test('fib(1) is 1', () => {
    expect(fibonacci(1)).toBe(1);
  });

  test('fib(10) is 55', () => {
    expect(fibonacci(10)).toBe(55);
  });
});
