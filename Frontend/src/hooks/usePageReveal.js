import { useEffect, useRef } from 'react';

export default function usePageReveal() {
  const ref = useRef(null);
  useEffect(() => {
    const nodes = ref.current?.querySelectorAll('[data-reveal]') || [];
    if (!window.IntersectionObserver || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const observer = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('reveal-visible');
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.08 });
    nodes.forEach(node => { node.classList.add('reveal-ready'); observer.observe(node); });
    return () => { observer.disconnect(); nodes.forEach(node => node.classList.remove('reveal-ready')); };
  }, []);
  return ref;
}
