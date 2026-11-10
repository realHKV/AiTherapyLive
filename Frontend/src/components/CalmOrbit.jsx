/** Decorative, CSS-only motif shared across the welcome and conversation screens. */
export default function CalmOrbit({ compact = false }) {
  return <div className={`calm-orbit${compact ? ' calm-orbit-compact' : ''}`} aria-hidden="true">
    <span className="calm-orbit-ring" /><span className="calm-orbit-ring" />
    <span className="calm-orbit-core">✦</span>
  </div>;
}
