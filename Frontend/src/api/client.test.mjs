import assert from 'node:assert/strict';
import test from 'node:test';
import { AxiosError } from 'axios';
import apiClient from './client.js';

const values = new Map();
globalThis.localStorage = {
  getItem: key => values.get(key) ?? null,
  setItem: (key, value) => values.set(key, value),
  removeItem: key => values.delete(key),
};
let expiredEvents = 0;
globalThis.window = { dispatchEvent: () => { expiredEvents++; } };

function unauthorized(config) {
  throw new AxiosError('Unauthorized', 'ERR_BAD_RESPONSE', config, null, {
    status: 401, data: {}, headers: {}, config,
  });
}

function ok(config, data) {
  return { status: 200, headers: {}, config, data: { success: true, data } };
}

test('concurrent expired reads share one refresh and retry with the new token', async () => {
  values.clear();
  values.set('accessToken', 'old');
  values.set('refreshToken', 'refresh-old');
  let refreshes = 0;
  apiClient.defaults.adapter = async config => {
    if (config.url === '/auth/refresh') {
      refreshes++;
      await new Promise(resolve => setTimeout(resolve, 5));
      return ok(config, { accessToken: 'new', refreshToken: 'refresh-new' });
    }
    if (config.headers.Authorization === 'Bearer old') return unauthorized(config);
    return ok(config, { value: config.url });
  };

  const results = await Promise.all([apiClient.get('/one'), apiClient.get('/two')]);
  assert.equal(refreshes, 1);
  assert.deepEqual(results, [{ value: '/one' }, { value: '/two' }]);
  assert.equal(values.get('refreshToken'), 'refresh-new');
});

test('expired mutation is not automatically replayed', async () => {
  values.clear();
  values.set('accessToken', 'old');
  values.set('refreshToken', 'refresh-old');
  let mutationCalls = 0;
  apiClient.defaults.adapter = async config => {
    if (config.url === '/auth/refresh') return ok(config, { accessToken: 'new', refreshToken: 'refresh-new' });
    mutationCalls++;
    return unauthorized(config);
  };

  await assert.rejects(apiClient.post('/change', { value: 1 }), /submit that action again/);
  assert.equal(mutationCalls, 1);
  assert.equal(values.get('accessToken'), 'new');
});

test('expired session without a refresh token clears stale authentication', async () => {
  values.clear();
  expiredEvents = 0;
  values.set('accessToken', 'expired');
  values.set('user', '{"id":"stale"}');
  apiClient.defaults.adapter = unauthorized;

  await assert.rejects(apiClient.get('/profile/me'));
  assert.equal(values.has('accessToken'), false);
  assert.equal(values.has('user'), false);
  assert.equal(expiredEvents, 1);
});
