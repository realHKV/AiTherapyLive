export function canResumeSessionToday(startedAt, now = new Date()) {
  const started = new Date(startedAt);
  return !Number.isNaN(started.getTime())
    && started.getFullYear() === now.getFullYear()
    && started.getMonth() === now.getMonth()
    && started.getDate() === now.getDate();
}
