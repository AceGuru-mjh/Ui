import {Easing, interpolate, spring} from 'remotion';
import {LEVELS} from './constants';

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
 * Decaying oscillation used for arrival bounces ("spring 回弹").
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
): number =>
  f < at
    ? 0
    : spring({frame: f - at, fps, config: {damping, stiffness, mass: 1}});

/** hsl() helper - neon colors are easier to reason about in HSL. */
export const hsl = (h: number, s: number, l: number, a = 1): string =>
  a >= 1 ? `hsl(${h.toFixed(1)} ${s}% ${l}%)` : `hsl(${h.toFixed(1)} ${s}% ${l}% / ${a})`;

/**
 * Continuous level color as a function of continuous level value 0..4
 * (0=OFF .. 3=HIGH, 4=ULTRA). Between integer levels the color ramps
 * gradually through the ramp; at 4 it is the animated rainbow.
 * `rainbowHue` is the frame-driven hue offset for ULTRA.
 */
export const levelColor = (v: number, rainbowHue = 0): string => {
  const x = clamp(v, 0, 4);
  if (x >= 4) {
    return hsl(mod(285 + rainbowHue, 360), 96, 62);
  }
  const i = Math.min(3, Math.floor(x));
  const t = x - i;
  const a = LEVELS[i].color;
  const b = LEVELS[i + 1 < 4 ? i + 1 : 3].color;
  if (t <= 0) return a;
  return mixHex(a, x >= 3 ? '#B03FD9' : b, t);
};

/** Linear hex color mix. */
export const mixHex = (a: string, b: string, t: number): string => {
  const pa = parseInt(a.slice(1), 16);
  const pb = parseInt(b.slice(1), 16);
  const r = Math.round(interpolate(t, [0, 1], [(pa >> 16) & 255, (pb >> 16) & 255]));
  const g = Math.round(interpolate(t, [0, 1], [(pa >> 8) & 255, (pb >> 8) & 255]));
  const bl = Math.round(interpolate(t, [0, 1], [pa & 255, pb & 255]));
  const to2 = (n: number) => n.toString(16).padStart(2, '0');
  return `#${to2(r)}${to2(g)}${to2(bl)}`;
};

/** Level index (0..4) as a function of the continuous level value. */
export const levelIndex = (v: number): number => clamp(Math.round(v), 0, 4);

export const LEVEL_NAMES = ['OFF', 'LOW', 'MID', 'HIGH', 'ULTRA'] as const;
