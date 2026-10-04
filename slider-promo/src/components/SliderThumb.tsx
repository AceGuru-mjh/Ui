import type {FC} from 'react';
import {clamp01} from '../util';

export type SliderThumbProps = {
  x: number;
  y: number;
  r: number;
  color: string;
  opacity?: number;
  scaleX?: number;
  scaleY?: number;
  /** 0..1 soft white glow strength */
  glow?: number;
};

/** Flat slider thumb: plain circle with optional soft glow. */
export const SliderThumb: FC<SliderThumbProps> = ({
  x,
  y,
  r,
  color,
  opacity = 1,
  scaleX = 1,
  scaleY = 1,
  glow = 0,
}) => (
  <div
    style={{
      position: 'absolute',
      left: x - r,
      top: y - r,
      width: r * 2,
      height: r * 2,
      borderRadius: '50%',
      backgroundColor: color,
      opacity,
      scale: `${scaleX} ${scaleY}`,
      boxShadow:
        glow > 0.01
          ? `0 0 ${Math.round(r * 1.5)}px rgba(255,255,255,${(clamp01(glow) * 0.35).toFixed(3)})`
          : undefined,
    }}
  />
);
