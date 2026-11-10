import assert from 'node:assert/strict';
import test from 'node:test';
import { formatCountdown, getDemoScheduleState } from './demoSchedule.js';

function atIndiaTime(date, time) {
  return Date.parse(`${date}T${time}+05:30`);
}

test('shows shutdown countdown only in the hour before midnight in India', () => {
  assert.deepEqual(getDemoScheduleState(atIndiaTime('2026-10-27', '22:59:59')), { phase: 'online', remainingMs: 0 });
  assert.deepEqual(getDemoScheduleState(atIndiaTime('2026-10-27', '23:00:00')), { phase: 'closing', remainingMs: 3600000 });
  assert.deepEqual(getDemoScheduleState(atIndiaTime('2026-10-27', '23:59:59')), { phase: 'closing', remainingMs: 1000 });
});

test('shows restart countdown across midnight until 7 a.m. India time', () => {
  assert.deepEqual(getDemoScheduleState(atIndiaTime('2026-10-28', '00:00:00')), { phase: 'offline', remainingMs: 25200000 });
  assert.deepEqual(getDemoScheduleState(atIndiaTime('2026-10-28', '06:59:59')), { phase: 'offline', remainingMs: 1000 });
  assert.deepEqual(getDemoScheduleState(atIndiaTime('2026-10-28', '07:00:00')), { phase: 'online', remainingMs: 0 });
});

test('formats time remaining without losing the last partial second', () => {
  assert.equal(formatCountdown(3600000), '01:00:00');
  assert.equal(formatCountdown(1001), '00:00:02');
  assert.equal(formatCountdown(1), '00:00:01');
});
