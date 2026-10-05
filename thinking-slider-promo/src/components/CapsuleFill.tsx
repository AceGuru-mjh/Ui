import type {FC} from 'react';
import {GLASS} from '../constants';

export type CapsuleFillProps = {
  /** capsule-local x of the liquid leading edge */
  fillEdge: number;
  /** wave amplitude in px (0 = flat fill) */
  amp: number;
  /** wave phase */
  wavePhase: number;
  /** fill color (level color or glass accent) */
  color: string;
  /** unique gradient id (component is re-used inside refraction copies) */
  gradId: string;
  fillOpacity?: number;
  glowOpacity?: number;
};

const WAVE_POINTS = 15;

/** Wavy vertical liquid edge: x(y) = edge + amp * sin(2*PI*y/30 + phase). */
const buildWavePath = (edge: number, amp: number, phase: number): string => {
  const parts: string[] = [];
  for (let i = 0; i <= WAVE_POINTS; i += 1) {
    const y = (i / WAVE_POINTS) * GLASS.capsuleH;
    const x = edge + amp * Math.sin((2 * Math.PI * y) / 30 + phase);
    parts.push(`${x.toFixed(1)} ${y.toFixed(1)}`);
  }
  return `M 0 0 L ${parts.join(' L ')} L 0 ${GLASS.capsuleH} Z`;
};

/**
 * The colored liquid inside the glass capsule: gradient fill with a rippling
 * leading edge, an echo layer for depth, and a meniscus glow.
 * Used both by the real capsule and by the glass thumb's refraction copy.
 */
export const CapsuleFill: FC<CapsuleFillProps> = ({
  fillEdge,
  amp,
  wavePhase,
  color,
  gradId,
  fillOpacity = 0.78,
  glowOpacity = 1,
}) => {
  const d = buildWavePath(fillEdge, amp, wavePhase);
  const dEcho = buildWavePath(fillEdge - 12, amp * 0.5, wavePhase + Math.PI);
  return (
    <div
      style={{
        position: 'absolute',
        left: 0,
        top: 0,
        width: GLASS.capsuleW,
        height: GLASS.capsuleH,
      }}
    >
      <svg
        width={GLASS.capsuleW}
        height={GLASS.capsuleH}
        viewBox={`0 0 ${GLASS.capsuleW} ${GLASS.capsuleH}`}
        style={{position: 'absolute', left: 0, top: 0, display: 'block'}}
      >
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#FFFFFF" stopOpacity="0.45" />
            <stop offset="45%" stopColor={color} stopOpacity="1" />
            <stop offset="100%" stopColor={color} stopOpacity="1" />
          </linearGradient>
        </defs>
        <path d={dEcho} fill={color} fillOpacity={fillOpacity * 0.25} />
        <path d={d} fill={`url(#${gradId})`} fillOpacity={fillOpacity} />
      </svg>
      {/* meniscus glow at the leading edge */}
      <div
        style={{
          position: 'absolute',
          left: fillEdge - 17,
          top: 0,
          width: 34,
          height: GLASS.capsuleH,
          opacity: glowOpacity,
          background: `radial-gradient(ellipse 17px 28px at center, ${color}55 0%, transparent 75%)`,
        }}
      />
      {/* subtle top sheen */}
      <div
        style={{
          position: 'absolute',
          left: 10,
          top: 5,
          width: GLASS.capsuleW - 20,
          height: 2,
          borderRadius: 1,
          backgroundColor: 'rgba(255,255,255,0.35)',
        }}
      />
    </div>
  );
};
