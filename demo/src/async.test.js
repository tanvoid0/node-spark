// Tests async patterns — good for verifying the runner handles async test output correctly

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function fetchUser(id) {
  await delay(10);
  if (id <= 0) throw new Error('Invalid user id');
  return { id, name: `User ${id}`, active: true };
}

async function fetchUsers(ids) {
  return Promise.all(ids.map(fetchUser));
}

describe('async operations', () => {
  test('fetchUser returns user object', async () => {
    const user = await fetchUser(1);
    expect(user).toEqual({ id: 1, name: 'User 1', active: true });
  });

  test('fetchUser throws on invalid id', async () => {
    await expect(fetchUser(-1)).rejects.toThrow('Invalid user id');
  });

  test('fetchUsers returns multiple users', async () => {
    const users = await fetchUsers([1, 2, 3]);
    expect(users).toHaveLength(3);
    expect(users[0].id).toBe(1);
    expect(users[2].name).toBe('User 3');
  });

  test('delay resolves after timeout', async () => {
    const start = Date.now();
    await delay(50);
    expect(Date.now() - start).toBeGreaterThanOrEqual(50);
  });
});

describe('promise chaining', () => {
  test('chained then resolves correctly', () => {
    return fetchUser(5)
      .then(user => {
        expect(user.id).toBe(5);
        expect(user.active).toBe(true);
      });
  });

  test('catch handles rejection', () => {
    return fetchUser(0)
      .then(() => { throw new Error('Should have rejected'); })
      .catch(err => {
        expect(err.message).toBe('Invalid user id');
      });
  });
});
