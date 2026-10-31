import assert from 'node:assert/strict';
import test from 'node:test';
import apiClient from './client.js';
import { profileAPI } from './profile.js';

globalThis.localStorage = { getItem: () => null };

test('memory changes send the quoted version required by the backend', async () => {
  const requests = [];
  apiClient.defaults.adapter = async config => {
    requests.push(config);
    return { status: 200, headers: {}, config, data: { success: true, data: {} } };
  };

  await profileAPI.confirmCandidate('candidate-id', 3, { title: 'Corrected' });
  await profileAPI.dismissCandidate('candidate-id', 4);
  await profileAPI.editMemory('memory-id', 5, { status: 'resolved' });
  await profileAPI.forgetMemory('memory-id', 6);

  assert.deepEqual(requests.map(request => [request.method, request.url, request.headers['If-Match']]), [
    ['post', '/profile/me/memory-candidates/candidate-id/confirm', '"3"'],
    ['delete', '/profile/me/memory-candidates/candidate-id', '"4"'],
    ['patch', '/profile/me/memories/memory-id', '"5"'],
    ['post', '/profile/me/memories/memory-id/forget', '"6"'],
  ]);
});
