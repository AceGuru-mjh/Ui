import type {FC} from 'react';
import {M3} from '../constants';

/**
 * Soft Material-You tonal light fields floating in the black void.
 * These are the "content behind the glass" that the liquid-glass circles
 * refract: radial gradients only (no filter blur) so refraction copies stay
 * cheap. Pure function of `phase` (a continuous frame number).
 */
export const AmbientField: FC<{phase: number; master: number}> = ({phase, master}) => {
  const m = Math.max(0, Math.min(1, master));
  if (m < 0.004) return null;

  const blobs = [
    {
      // primary lavender, upper area
      size: 640,
      color: M3.primary,
      opacity: 0.15,
      x: 320 + 42 * Math.sin(phase * 0.0037),
      y: 470 + 48 * Math.cos(phase * 0.0029),
    },
    {
      // tertiary rose, lower right
      size: 560,
      color: M3.tertiary,
      opacity: 0.12,
      x: 780 + 44 * Math.sin(phase * 0.0033 + 2.1),
      y: 1330 + 38 * Math.cos(phase * 0.0041 + 1.2),
    },
    {
      // secondary, mid, faint
      size: 500,
      color: M3.secondary,
      opacity: 0.09,
      x: 540 + 58 * Math.sin(phase * 0.0021 + 4.2),
      y: 900 + 64 * Math.cos(phase * 0.0024),
    },
    {
      // small primary accent near the hub
      size: 360,
      color: M3.primary,
      opacity: 0.08,
      x: 540 + 30 * Math.cos(phase * 0.0047),
      y: 880 + 26 * Math.sin(phase * 0.0039 + 0.7),
    },
  ];

  return (
    <>
      {blobs.map((b, i) => (
        <div
          key={i}
          style={{
            position: 'absolute',
            left: b.x - b.size / 2,
            top: b.y - b.size / 2,
            width: b.size,
            height: b.size,
            borderRadius: '50%',
            opacity: b.opacity * m,
            background: `radial-gradient(circle, ${b.color} 0%, rgba(0,0,0,0) 70%)`,
          }}
        />
      ))}
    </>
  );
};
