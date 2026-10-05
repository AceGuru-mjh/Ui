import {Easing, interpolate, spring} from 'remotion';

export type Ease = (t: number) => number;

export const clamp = (v: number, min: number, max: number): number =>
  Math.min(max, Math.max(min, v));

export const clamp01 = (v: number): number => clamp(v, 0, 1);

/**
 * Frame-driven segment interpolation with clamped extrapolation.
 * Returns `from` before `start` and `to` after `end`.
 */
export const seg = (
  f: number,
  start: number,
  end: number,
  from: number,
  to: number,
  easing: Ease = Easing.linear,
): number => {
  if (f <= start) return from;
  if (f >= end) return to;
  return interpolate(f, [start, end], [from, to], {
    easing,
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
};

/** Linear 0..1 progress of a frame window. */
export const fadeIn = (f: number, start: number, end: number): number =>
  clamp01((f - start) / (end - start));

/**
 * Decaying oscillation used for arrival bounces ("spring back-bounce").
 * Zero before t0, then amp * e^(-(f-t0)/decay) * sin((f-t0) * freq).
 */
export const wobble = (
  f: number,
  t0: number,
  amp: number,
  decay = 8,
  freq = 0.9,
): number => (f <= t0 ? 0 : amp * Math.exp(-(f - t0) / decay) * Math.sin((f - t0) * freq));

/** Deterministic PRNG (mulberry32) - same sequence on every render. */
export const mulberry32 = (seed: number): (() => number) => {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
};

/** Central-difference velocity of a pure frame function. */
export const velocity = (
  fn: (x: number) => number,
  f: number,
  dt = 2,
): number => (fn(f + dt) - fn(f - dt)) / (2 * dt);

export const mod = (a: number, m: number): number => ((a % m) + m) % m;

/** Spring 0 -> 1 "pop-in" progress starting at frame `at`. */
export const pop = (
  f: number,
  at: number,
  fps: number,
  damping = 12,
  stiffness = 130,
): number => (f < at ? 0 : spring({frame: f - at, fps, config: {damping, stiffness, mass: 1}}));

/* ---------------------------------- color --------------------------------- */

export type RGB = {r: number; g: number; b: number};

export const hexToRgb = (hex: string): RGB => {
  const h = hex.replace('#', '');
  return {
    r: parseInt(h.slice(0, 2), 16),
    g: parseInt(h.slice(2, 4), 16),
    b: parseInt(h.slice(4, 6), 16),
  };
};

/** Linear mix of two hex colors, t in 0..1. */
export const mixHex = (a: string, b: string, t: number): string => {
  const ca = hexToRgb(a);
  const cb = hexToRgb(b);
  const k = clamp01(t);
  const r = Math.round(ca.r + (cb.r - ca.r) * k);
  const g = Math.round(ca.g + (cb.g - ca.g) * k);
  const bl = Math.round(ca.b + (cb.b - ca.b) * k);
  const to = (n: number) => n.toString(16).padStart(2, '0');
  return `#${to(r)}${to(g)}${to(bl)}`;
};

export const rgba = (hex: string, alpha: number): string => {
  const {r, g, b} = hexToRgb(hex);
  return `rgba(${r},${g},${b},${clamp01(alpha).toFixed(3)})`;
};
