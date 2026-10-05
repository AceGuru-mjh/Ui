import type {FC} from 'react';
import {THINK} from '../constants';
import {clamp, hsl, mod, mulberry32, pop, seg} from '../util';

export type LightningGridProps = {
  frame: number;
  fps: number;
  /** overall opacity (scene exit etc.) */
  groupOpacity?: number;
};

/**
 * Deterministic lightning-strike schedule: first strike right after the
 * ULTRA eruption, then irregular strikes (the "lightning rhythm").
 * Returns the sorted strike start frames.
 */
const strikeFrames = (): number[] => {
  const rnd = mulberry32(0x5eed);
  const out: number[] = [];
  let t = 318;
  let i = 0;
  while (t < 596) {
    out.push(t);
    t += 34 + Math.floor(rnd() * 26); // irregular 0.57-1.0s gaps
    i += 1;
  }
  return out;
};

const STRIKES = strikeFrames();

/** Brightness of one cell at one frame: shimmer + strike spikes. */
const cellBrightness = (
  f: number,
  col: number,
  row: number,
  strikes: number[],
): number => {
  const rnd = mulberry32((col * 733 + row * 977) >>> 0);
  const base = rnd();
  // slow ambient shimmer, bucketed every 4 frames (stable per bucket)
  const bucket = Math.floor(f / 4);
  const r2 = mulberry32((bucket * 613 + col * 71 + row * 13) >>> 0)();
  let b = 0.30 + base * 0.34 + r2 * 0.22;

  // lightning: bolt sweeps left->right, 0.7 frames per column
  for (let k = 0; k < strikes.length; k++) {
    const t0 = strikes[k] + col * 0.7 + row * 2.2;
    if (f >= t0 && f < t0 + 26) {
      const d = f - t0;
      const mag = 1.15 * Math.exp(-d / 5.0);
      b = Math.min(1.35, b + mag);
    }
  }
  // whole-grid flash on strike start
  for (let k = 0; k < strikes.length; k++) {
    if (f >= strikes[k] && f < strikes[k] + 8) {
      b = Math.min(1.4, b + 0.22 * Math.exp(-(f - strikes[k]) / 3));
    }
  }
  return b;
};

/**
 * The ULTRA finale: a 9x2 field of small neon squares in full-color rainbow,
 * flickering with an irregular lightning rhythm (bolt sweeps left->right,
 * decaying afterglow, white-hot cores on the brightest hits).
 * Everything is seeded and frame-driven - deterministic across renders.
 */
export const LightningGrid: FC<LightningGridProps> = ({frame: f, fps, groupOpacity = 1}) => {
  const appear = 300;
  const master = seg(f, appear, appear + 10, 0, 1) * groupOpacity;
  if (master <= 0.005) return null;

  const settle = seg(f, 616, 646, 1, 0.35); // calm the storm for the ending
  const cell = THINK.gridCell;
  const gap = THINK.gridGap;
  const cols = THINK.gridCols;
  const rows = THINK.gridRows;
  const totalW = cols * cell + (cols - 1) * gap;
  const left0 = 540 - totalW / 2;
  const top0 = THINK.gridY - ((rows * cell + (rows - 1) * gap) / 2);

  const strikes = STRIKES;

  return (
    <div
      style={{
        position: 'absolute',
        left: 0,
        top: 0,
        width: 1080,
        height: 1920,
        opacity: master,
      }}
    >
      {/* neon squares */}
      {Array.from({length: rows}).map((_, row) =>
        Array.from({length: cols}).map((_, col) => {
          const t = appear + 4 + col * 3 + row * 8;
          const p = pop(f, t, fps, 13, 160);
          if (p <= 0.001) return null;
          const b = cellBrightness(f, col, row, strikes) * (0.45 + 0.55 * settle) + 0.05;
          const hue = mod(col * 27 + row * 152 + f * 1.5, 360);
          const hot = b > 0.72;
          const glow = 8 + 40 * Math.min(b, 1.2);
          return (
            <div
              key={`${row}-${col}`}
              style={{
                position: 'absolute',
                left: left0 + col * (cell + gap),
                top: top0 + row * (cell + gap),
                width: cell,
                height: cell,
                borderRadius: THINK.gridR,
                background: hot
                  ? `linear-gradient(135deg, #FFFFFF 0%, ${hsl(hue, 100, 78)} 45%, ${hsl(hue, 96, 60)} 100%)`
                  : hsl(hue, 100, 56, clamp(0.42 + 0.58 * Math.min(b, 1), 0, 1)),
                boxShadow:
                  `0 0 ${glow}px ${hsl(hue, 100, 60, clamp(0.85 * Math.min(b, 1.2), 0, 1))}, ` +
                  `0 0 ${(glow * 2.2).toFixed(0)}px ${hsl(hue, 100, 62, clamp(0.38 * Math.min(b, 1.2), 0, 0.7))}`,
                scale: `${(0.55 + 0.45 * p) * (1 + 0.14 * Math.min(b, 1.15))}`,
                opacity: p,
              }}
            />
          );
        }),
      )}
    </div>
  );
};
