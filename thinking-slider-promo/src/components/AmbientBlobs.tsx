import type {FC} from 'react';
import {mulberry32} from '../util';

export type AmbientBlobsProps = {
  /** master opacity (fades in with glass materialization) */
  master: number;
  /** continuous phase (local frames) for slow drift */
  phase: number;
  /** warm-up 0..1 controls saturation/size ramp */
  warmth?: number;
};

const BLOBS = [
  {x: 250, y: 640, r: 330, color: '231, 221, 255', seed: 11}, // primaryContainer #EADDFF
  {x: 830, y: 780, r: 300, color: '255, 216, 228', seed: 23}, // tertiaryContainer #FFD8E4
  {x: 420, y: 1180, r: 360, color: '204, 224, 255', seed: 37}, // soft sky
  {x: 900, y: 1120, r: 280, color: '232, 222, 248', seed: 51}, // secondaryContainer #E8DEF8
] as const;

/**
 * Soft ambient tonal light field behind the glass: ultra-blurred M3
 * container-hue blobs drifting slowly, so the capsule's backdrop blur and
 * edge refraction have real content to bend (same role as the "环境光场"
 * in m3-nav-promo). No particles, no hard shapes - pure light.
 */
export const AmbientBlobs: FC<AmbientBlobsProps> = ({master, phase, warmth = 1}) => {
  if (master <= 0.005) return null;
  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        overflow: 'hidden',
        opacity: master,
      }}
    >
      {BLOBS.map((b, i) => {
        const rnd = mulberry32(b.seed);
        const dx = Math.sin(phase * 0.004 + rnd() * 6.28) * 60;
        const dy = Math.cos(phase * 0.0033 + rnd() * 6.28) * 44;
        const op = (0.55 + 0.25 * Math.sin(phase * 0.006 + i)) * warmth;
        return (
          <div
            key={i}
            style={{
              position: 'absolute',
              left: b.x - b.r + dx,
              top: b.y - b.r + dy,
              width: b.r * 2,
              height: b.r * 2,
              borderRadius: '50%',
              background: `radial-gradient(circle, rgba(${b.color}, ${op.toFixed(3)}) 0%, rgba(${b.color}, 0) 70%)`,
              filter: 'blur(40px)',
            }}
          />
        );
      })}
    </div>
  );
};
