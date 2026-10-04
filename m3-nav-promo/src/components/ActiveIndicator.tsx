import type {FC} from 'react';
import {spring} from 'remotion';
import {M3} from '../constants';
import {clamp01} from '../util';

/**
 * Material 3 active indicator: a compact morphing pill that travels tight
 * behind the nav items (squashing along the tangent, M3 Expressive motion)
 * and blooms its soft container-colored halo only while resting - exactly
 * how an M3 navigation bar pill behaves. On arrival it pulses a squircle
 * (M3 shape morph) and settles back to a circle.
 */
export type ActiveIndicatorProps = {
  x: number;
  y: number;
  r: number;
  /** 0 circle .. 1 squircle (Material 3 shape morph) */
  morph?: number;
  /** tangent squash: scaleX along travel direction */
  stretch?: number;
  /** rotation of the travel frame (degrees) */
  rot?: number;
  /** halo bloom 0..1 (rest = 1, moving = 0) */
  glow?: number;
  color?: string;
  glowColor?: string;
  opacity?: number;
  fps: number;
  /** frame relative to indicator birth, for the pop spring */
  bornAt: number;
  frame: number;
};

export const ActiveIndicator: FC<ActiveIndicatorProps> = ({
  x,
  y,
  r,
  morph = 0,
  stretch = 1,
  rot = 0,
  glow = 1,
  color = M3.primaryContainer,
  glowColor = 'rgba(208,188,255,0.30)',
  opacity = 1,
  fps,
  bornAt,
  frame,
}) => {
  if (frame < bornAt || opacity <= 0.004) return null;
  const pop =
    frame >= bornAt
      ? spring({
          frame: frame - bornAt,
          fps,
          from: 0,
          to: 1,
          config: {damping: 14, stiffness: 170, mass: 1},
        })
      : 0;
  if (pop <= 0.001) return null;

  const radius = `${50 - 18 * clamp01(morph)}%`;

  return (
    <div
      style={{
        position: 'absolute',
        left: x - r,
        top: y - r,
        width: r * 2,
        height: r * 2,
        scale: `${pop}`,
        opacity: clamp01(opacity),
      }}
    >
      {/* halo - blooms while resting, collapses while travelling */}
      <div
        style={{
          position: 'absolute',
          inset: -14,
          borderRadius: '50%',
          background: `radial-gradient(circle, ${glowColor} 0%, rgba(0,0,0,0) 70%)`,
          opacity: clamp01(glow),
        }}
      />
      {/* compact morphing pill body, squashed along the tangent */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          scale: `${stretch} ${2 - stretch}`,
          rotate: `${rot}deg`,
          borderRadius: radius,
          backgroundColor: color,
          opacity: 0.95,
          border: `1px solid rgba(255,255,255,0.10)`,
        }}
      />
    </div>
  );
};

/**
 * Material 3 press ripple: an expanding ring that fades out - fired when
 * the hub FAB "receives" a selection change.
 */
export const RippleRing: FC<{
  x: number;
  y: number;
  frame: number;
  t0: number;
  fps: number;
  color?: string;
  maxR?: number;
}> = ({x, y, frame, t0, fps, color = M3.primary, maxR = 120}) => {
  if (frame < t0) return null;
  const p = spring({
    frame: frame - t0,
    fps,
    from: 0,
    to: 1,
    config: {damping: 22, stiffness: 90, mass: 1},
  });
  const op = clamp01(1 - p) * 0.55;
  if (op < 0.01) return null;
  const r = 20 + p * maxR;
  return (
    <div
      style={{
        position: 'absolute',
        left: x - r,
        top: y - r,
        width: r * 2,
        height: r * 2,
        borderRadius: '50%',
        border: `2px solid ${color}`,
        opacity: op,
      }}
    />
  );
};
