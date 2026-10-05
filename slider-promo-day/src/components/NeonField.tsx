import type {FC} from 'react';
import {AMBER, AMBER_DEEP} from '../constants';
import {mulberry32, rgba} from '../util';

export type NeonFieldProps = {
  /** global scene-local frame */
  p: number;
  /** 0..1 master appearance */
  master: number;
  /** capsule rect (stage coords) - squares avoid it */
  capsule: {left: number; top: number; w: number; h: number};
  /** 0..1 how much squares are lit overall (dim at the very end) */
  lit: number;
};

type Sq = {
  x: number;
  y: number;
  size: number;
  phase: number;
  deep: boolean;
};

const buildSquares = (): Sq[] => {
  const rand = mulberry32(20261005);
  const items: Sq[] = [];
  for (let i = 0; i < 26; i += 1) {
    // band around the slider: y 640..1240, x 120..960, minus capsule rect
    const x = 120 + rand() * 840;
    const y = 640 + rand() * 600;
    const size = 8 + Math.floor(rand() * 3) * 5; // 8 / 13 / 18 px
    const deep = rand() < 0.35;
    items.push({x, y, size, phase: rand() * Math.PI * 2, deep});
  }
  return items;
};

const SQUARES = buildSquares();

/**
 * Lightning-pulsing neon square field (level 5 signature).
 * Small amber squares scattered around the slider, pulsing with a
 * deterministic lightning cadence: a traveling wave from the capsule plus
 * per-square random flicker. Pure function of the frame - renders identically
 * on every pass.
 */
export const NeonField: FC<NeonFieldProps> = ({p, master, capsule, lit}) => {
  if (master <= 0.005) return null;
  const items = SQUARES.filter((s) => {
    const inCap =
      s.x + s.size > capsule.left - 26 &&
      s.x < capsule.left + capsule.w + 26 &&
      s.y + s.size > capsule.top - 26 &&
      s.y < capsule.top + capsule.h + 26;
    return !inCap;
  });

  const capsuleCx = capsule.left + capsule.w / 2;
  const capsuleCy = capsule.top + capsule.h / 2;

  return (
    <div style={{position: 'absolute', inset: 0, opacity: master}}>
      {items.map((s, i) => {
        const dist = Math.hypot(s.x + s.size / 2 - capsuleCx, s.y + s.size / 2 - capsuleCy);
        // lightning cascade: wave traveling outward + sharp random re-fires
        const wave = 0.5 + 0.5 * Math.sin(p * 0.16 - dist * 0.012 + s.phase);
        const gate = Math.sin((p * 0.23 + i * 1.7) * 12.9898);
        const spark = Math.abs(gate) > 0.86 ? 1 : 0;
        const b = Math.max(wave * 0.55 + spark * 0.45, 0.12) * lit;
        const color = s.deep ? AMBER_DEEP : AMBER;
        const glow = 6 + spark * 10;
        return (
          <div
            key={i}
            style={{
              position: 'absolute',
              left: s.x,
              top: s.y,
              width: s.size,
              height: s.size,
              borderRadius: 3,
              backgroundColor: rgba(color, 0.18 + 0.82 * b),
              boxShadow:
                b > 0.45
                  ? `0 0 ${glow}px ${rgba(color, 0.30 + 0.45 * b)}`
                  : undefined,
              scale: (0.72 + 0.28 * b).toFixed(3),
            }}
          />
        );
      })}
    </div>
  );
};
