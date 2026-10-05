import type {FC} from 'react';
import {AMBER, AMBER_DEEP} from '../constants';
import {rgba} from '../util';

export type LedStripProps = {
  /** capsule width/height (local coords) */
  w: number;
  h: number;
  /** scene-local frame */
  p: number;
  /** 0..1 appearance */
  master: number;
};

const COLS = 12;
const ROWS = 2;

/**
 * The in-capsule LED matrix: 12x2 tiny amber squares riding the lower half of
 * the capsule, pulsing with a deterministic lightning cadence - traveling
 * wave + sharp random re-fires, like a signal flowing through the glass.
 */
export const LedStrip: FC<LedStripProps> = ({w, h, p, master}) => {
  if (master <= 0.005) return null;
  const cell = 9;
  const gapX = (w - 2 * 18 - COLS * cell) / (COLS - 1);
  const gapY = 6;
  const totalH = ROWS * cell + gapY;
  const top = h - totalH - 7;

  const cells: React.ReactNode[] = [];
  for (let c = 0; c < COLS; c += 1) {
    for (let r = 0; r < ROWS; r += 1) {
      const idx = r * COLS + c;
      const travel = 0.5 + 0.5 * Math.sin(p * 0.30 - c * 0.55 + r * 1.3);
      const gate = Math.sin((p * 0.31 + idx * 2.399) * 12.9898);
      const spark = Math.abs(gate) > 0.84 ? 1 : 0;
      const b = Math.max(travel * 0.6 + spark * 0.4, 0.10) * master;
      const deep = (c + r) % 3 === 0;
      const color = deep ? AMBER_DEEP : AMBER;
      cells.push(
        <div
          key={idx}
          style={{
            position: 'absolute',
            left: 18 + c * (cell + gapX),
            top: top + r * (cell + gapY),
            width: cell,
            height: cell,
            borderRadius: 2.5,
            backgroundColor: rgba(color, 0.16 + 0.84 * b),
            boxShadow:
              b > 0.5 ? `0 0 ${(5 + spark * 7).toFixed(1)}px ${rgba(color, 0.35 + 0.4 * b)}` : undefined,
            scale: (0.7 + 0.3 * b).toFixed(3),
          }}
        />,
      );
    }
  }

  return <div style={{position: 'absolute', inset: 0}}>{cells}</div>;
};
