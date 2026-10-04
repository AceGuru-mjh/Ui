import {M3, NAV} from '../constants';

/** Position on a ring (degrees, SVG y-down: -90 is the top). */
export const ringPos = (
  cx: number,
  cy: number,
  R: number,
  angleDeg: number,
): {x: number; y: number} => {
  const a = (angleDeg * Math.PI) / 180;
  return {x: cx + R * Math.cos(a), y: cy + R * Math.sin(a)};
};

/**
 * Liquid ring track as an SVG path: a circle whose radius wobbles with two
 * slow sine harmonics - the "molten" version of the nav bar's track.
 */
export const liquidRingPath = (
  cx: number,
  cy: number,
  R: number,
  amp: number,
  phase: number,
  samples = 120,
): string => {
  const pts: string[] = [];
  for (let i = 0; i <= samples; i++) {
    const t = (i / samples) * Math.PI * 2;
    const r =
      R +
      amp * Math.sin(3 * t + phase * 0.05) +
      amp * 0.6 * Math.sin(5 * t - phase * 0.037);
    const x = cx + r * Math.cos(t);
    const y = cy + r * Math.sin(t);
    pts.push(`${i === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`);
  }
  return `${pts.join(' ')}Z`;
};

/** Ring stroke colors per tonal level. */
export const TRACK_COLORS = {
  flatOuter: M3.surfaceContainerLow,
  flatInner: M3.surfaceContainer,
  liquid: 'rgba(255,255,255,0.10)',
} as const;

/** Convenience: outer/inner/hub centers with the stage offset applied. */
export const GEO = {
  outer: {cx: NAV.cx, cy: NAV.cy, R: NAV.outerR},
  inner: {cx: NAV.cx, cy: NAV.cy, R: NAV.innerR},
  hub: {cx: NAV.cx, cy: NAV.cy},
} as const;
