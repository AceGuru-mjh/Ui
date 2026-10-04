import type {FC} from 'react';
import {GLASS} from '../constants';

export type CapsuleContentProps = {
  /** capsule-local x of the liquid leading edge */
  fillEdge: number;
  /** wave amplitude in px (0 = flat fill) */
  amp: number;
  /** wave phase */
  wavePhase: number;
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
 * The inner content of the liquid glass capsule: white liquid fill with a
 * rippling leading edge, an echo layer for depth, and a meniscus glow.
 * Used both by the real capsule and by the glass thumb's refraction copy.
 */
export const CapsuleContent: FC<CapsuleContentProps> = ({
  fillEdge,
  amp,
  wavePhase,
  fillOpacity = 0.85,
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
        <path d={dEcho} fill="#FFFFFF" fillOpacity={fillOpacity * 0.25} />
        <path d={d} fill="#FFFFFF" fillOpacity={fillOpacity} />
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
          background:
            'radial-gradient(ellipse 17px 28px at center, rgba(255,255,255,0.30) 0%, rgba(255,255,255,0) 75%)',
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
          backgroundColor: 'rgba(255,255,255,0.10)',
        }}
      />
    </div>
  );
};
