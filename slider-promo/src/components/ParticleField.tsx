import type {FC} from 'react';
import {mod, mulberry32} from '../util';

type Particle = {
  x: number;
  y: number;
  r: number;
  sp: number;
  ph: number;
  tw: number;
  op: number;
  tint: string;
};

/** Deterministic particle set - built once from a fixed seed. */
const rng = mulberry32(0x5eed2026);
const PARTICLES: Particle[] = Array.from({length: 18}, () => ({
  x: rng() * 1080,
  y: rng() * 1920,
  r: 1.6 + rng() * 3.6,
  sp: 0.5 + rng() * 1.5,
  ph: rng() * Math.PI * 2,
  tw: 0.4 + rng() * 1.2,
  op: 0.12 + rng() * 0.3,
  tint: rng() < 0.3 ? 'rgba(190,215,255,0.9)' : 'rgba(255,255,255,0.9)',
}));

/**
 * Faint drifting light particles (<= 20) for the dark-void atmosphere.
 * `phase` is a continuous frame number so scene 4 can continue scene 3
 * without any jump; `master` fades the whole field in/out.
 */
export const ParticleField: FC<{phase: number; master: number}> = ({phase, master}) => (
  <>
    {PARTICLES.map((p, i) => {
      const y = mod(p.y - phase * 0.35 * p.sp, 1920 + 60) - 30;
      const x = p.x + 26 * Math.sin(phase * 0.006 * p.sp + p.ph);
      const twinkle = 0.55 + 0.45 * Math.sin(phase * 0.045 * p.tw + p.ph);
      const op = p.op * twinkle * master;
      if (op < 0.004) {
        return null;
      }
      return (
        <div
          key={i}
          style={{
            position: 'absolute',
            left: x - p.r,
            top: y - p.r,
            width: p.r * 2,
            height: p.r * 2,
            borderRadius: '50%',
            opacity: op,
            background: `radial-gradient(circle, ${p.tint} 0%, rgba(255,255,255,0) 70%)`,
          }}
        />
      );
    })}
  </>
);
