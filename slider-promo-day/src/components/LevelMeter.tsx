import type {FC} from 'react';
import {rgba} from '../util';

export type LevelMeterProps = {
  /** stage coords of the meter center */
  cx: number;
  cy: number;
  cols: number;
  rows: number;
  cell: number;
  gap: number;
  /** how many columns are fully lit (level reached) */
  litCols: number;
  /** 0..1 partial fill of the next column */
  partial: number;
  /** current theme color */
  color: string;
  /** master opacity */
  opacity: number;
  /** global frame - drives the neon pulse when litCols == cols */
  pulse: number;
  /** 0..1 neon mode (level 5) */
  neon: number;
};

/**
 * Blocky level meter: a cols x rows grid of small squares, one column per
 * level. Squares pop in column by column as the slider advances; at level 5
 * the whole meter joins the amber lightning pulse.
 */
export const LevelMeter: FC<LevelMeterProps> = ({
  cx,
  cy,
  cols,
  rows,
  cell,
  gap,
  litCols,
  partial,
  color,
  opacity,
  pulse,
  neon,
}) => {
  const totalW = cols * cell + (cols - 1) * gap;
  const totalH = rows * cell + (rows - 1) * gap;

  const cells: React.ReactNode[] = [];
  for (let c = 0; c < cols; c += 1) {
    const colState = c < litCols ? 1 : c === litCols ? partial : 0;
    for (let r = 0; r < rows; r += 1) {
      // stagger inside the column: bottom-up
      const local = Math.max(0, Math.min(1, colState * rows - (rows - 1 - r)));
      if (local <= 0.001) {
        // placeholder: empty cell outline
        cells.push(
          <div
            key={`${c}-${r}`}
            style={{
              position: 'absolute',
              left: c * (cell + gap),
              top: r * (cell + gap),
              width: cell,
              height: cell,
              borderRadius: 4,
              border: '1.5px solid rgba(17,17,17,0.10)',
              backgroundColor: 'rgba(255,255,255,0.4)',
            }}
          />,
        );
        continue;
      }
      // lightning flicker in neon mode
      const flickerSeed = Math.sin((c * 12.9898 + r * 78.233 + pulse * 0.55) * 43.17);
      const flick = neon > 0 ? 0.72 + 0.28 * Math.abs(flickerSeed) * neon : 1;
      const bright = 0.35 + 0.65 * local;
      cells.push(
        <div
          key={`${c}-${r}`}
          style={{
            position: 'absolute',
            left: c * (cell + gap),
            top: r * (cell + gap),
            width: cell,
            height: cell,
            borderRadius: 4,
            backgroundColor: rgba(color, Math.min(1, bright * flick)),
            boxShadow:
              neon > 0 && local > 0.5
                ? `0 0 ${8 * neon}px ${rgba(color, 0.55 * neon * flick)}`
                : undefined,
            scale: `${(0.7 + 0.3 * local).toFixed(3)}`,
          }}
        />,
      );
    }
  }

  return (
    <div
      style={{
        position: 'absolute',
        left: cx - totalW / 2,
        top: cy - totalH / 2,
        width: totalW,
        height: totalH,
        opacity,
      }}
    >
      {cells}
    </div>
  );
};
