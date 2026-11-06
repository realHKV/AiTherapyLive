const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const INDIA_UTC_OFFSET = 5 * HOUR + 30 * MINUTE;

// India has a fixed UTC offset, so this works regardless of the viewer's time zone.
export function getDemoScheduleState(now = Date.now()) {
  const indiaTimeOfDay = ((now + INDIA_UTC_OFFSET) % DAY + DAY) % DAY;

  if (indiaTimeOfDay >= 23 * HOUR) {
    return { phase: 'closing', remainingMs: DAY - indiaTimeOfDay };
  }
  if (indiaTimeOfDay < 7 * HOUR) {
    return { phase: 'offline', remainingMs: 7 * HOUR - indiaTimeOfDay };
  }
  return { phase: 'online', remainingMs: 0 };
}

export function formatCountdown(remainingMs) {
  const totalSeconds = Math.max(0, Math.ceil(remainingMs / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return [hours, minutes, seconds].map(value => String(value).padStart(2, '0')).join(':');
}
