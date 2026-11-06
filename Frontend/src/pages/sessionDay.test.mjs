import test from 'node:test';
import assert from 'node:assert/strict';
import { canResumeSessionToday } from './sessionDay.js';

test('resumes later on the same local day', () => {
  assert.equal(canResumeSessionToday(new Date(2026, 10, 6, 0, 5).toISOString(), new Date(2026, 10, 6, 23, 55)), true);
});

test('starts a new session after local midnight even within 24 hours', () => {
  assert.equal(canResumeSessionToday(new Date(2026, 10, 6, 23, 55).toISOString(), new Date(2026, 10, 7, 0, 5)), false);
  assert.equal(canResumeSessionToday('invalid date', new Date(2026, 10, 7)), false);
});
