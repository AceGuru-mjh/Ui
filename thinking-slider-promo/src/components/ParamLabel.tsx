import type {FC} from 'react';
import {FONT} from '../constants';

export type ParamLabelProps = {
  x: number;
  y: number;
  text: string;
  value?: string;
  opacity?: number;
  color?: string;
  size?: number;
  letterSpacing?: number;
  align?: 'center' | 'left';
  weight?: number;
};

/**
 * Standard UI parameter name label, e.g. "OPACITY" / "LIQUID GLASS".
 * Only standard control names/values appear - no captions or titles.
 */
export const ParamLabel: FC<ParamLabelProps> = ({
  x,
  y,
  text,
  value,
  opacity = 1,
  color = '#49454F',
  size = 24,
  letterSpacing,
  align = 'center',
  weight = 400,
}) => (
  <div
    style={{
      position: 'absolute',
      left: x,
      top: y,
      translate: align === 'center' ? '-50% -50%' : '0 -50%',
      fontFamily: FONT,
      fontSize: size,
      fontWeight: weight,
      color,
      opacity,
      whiteSpace: 'nowrap',
      letterSpacing: letterSpacing ? `${letterSpacing}px` : undefined,
    }}
  >
    {value === undefined ? text : `${text} · ${value}`}
  </div>
);
