import { useEffect, useRef, useState } from 'react';
import { formatCountdown, getDemoScheduleState } from './demoSchedule';

const scheduleEnabled = import.meta.env.VITE_DEMO_SCHEDULE_ENABLED === 'true';

export default function DemoScheduleBanner() {
  const [schedule, setSchedule] = useState(() => getDemoScheduleState());
  const bannerRef = useRef(null);

  useEffect(() => {
    if (!scheduleEnabled) return undefined;
    const update = () => setSchedule(getDemoScheduleState());
    const interval = window.setInterval(update, 1000);
    window.addEventListener('focus', update);
    document.addEventListener('visibilitychange', update);
    return () => {
      window.clearInterval(interval);
      window.removeEventListener('focus', update);
      document.removeEventListener('visibilitychange', update);
    };
  }, []);

  useEffect(() => {
    if (!scheduleEnabled || schedule.phase === 'online' || !bannerRef.current) return undefined;
    const banner = bannerRef.current;
    const updateHeight = () => document.documentElement.style.setProperty('--demo-banner-height', `${banner.offsetHeight}px`);
    document.documentElement.classList.add('demo-schedule-visible');
    updateHeight();
    const observer = new ResizeObserver(updateHeight);
    observer.observe(banner);
    return () => {
      observer.disconnect();
      document.documentElement.classList.remove('demo-schedule-visible');
      document.documentElement.style.removeProperty('--demo-banner-height');
    };
  }, [schedule.phase]);

  if (!scheduleEnabled || schedule.phase === 'online') return null;

  const offline = schedule.phase === 'offline';
  return (
    <div
      ref={bannerRef}
      className={`relative z-[100] w-full px-4 py-2 text-center text-sm font-semibold shadow-sm ${offline
        ? 'bg-amber-100 text-amber-950 dark:bg-amber-950 dark:text-amber-100'
        : 'bg-orange-100 text-orange-950 dark:bg-orange-950 dark:text-orange-100'}`}
      role="status"
    >
      <span>{offline ? 'Demo backend is scheduled offline. Expected back at 7:00 a.m. IST in ' : 'Demo backend shuts down at midnight IST in '}</span>
      <time className="tabular-nums" aria-live="off">{formatCountdown(schedule.remainingMs)}</time>
      {offline && <span className="font-normal">. Startup may take a few more minutes.</span>}
    </div>
  );
}
