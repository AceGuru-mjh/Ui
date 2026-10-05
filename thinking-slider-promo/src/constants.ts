/**
 * Global video configuration & design constants (single tunable entry).
 *
 * Concept: a day-mode 9:16 promo. One slider evolves
 *   M3 blocky assembly -> liquid glass -> 5-level "THINKING" slider
 * with a full-color neon lightning-grid finale at the ULTRA level.
 *
 * Timeline (absolute frames @60fps, total 1320 = 22.0s):
 *   Scene 1    0 - 323    M3 blocky slider assembly + drag demo
 *   fade 24f overlaps 300-323
 *   Scene 2  300 - 689     melt into liquid glass + viscous demo
 *   dissolve 30f overlaps 660-689
 *   Scene 3  660 - 1319    5-level thinking slider sweep + ULTRA lightning finale
 *
 * TransitionSeries total = 324 + 390 + 660 - 24 - 30 = 1320.
 */

export const VIDEO = {
  width: 1080,
  height: 1920,
  fps: 60,
  durationInFrames: 1320,
} as const;

/** Presentation scale applied to the 1080x1920 logical stage. */
export const STAGE_SCALE = 1.2;

export const FONT = "'Liberation Sans', 'DejaVu Sans', Arial, sans-serif";

/**
 * Day-mode canvas: cool light gray-white (user-selected), iOS-day feel.
 * All UI colors come from the Material 3 baseline LIGHT scheme
 * (source color #6750A4, m3.material.io) for scene 1.
 */
export const DAY = {
  bg: '#F5F7FA',
  onSurface: '#1D1B20',
  onSurfaceVariant: '#49454F',
  outlineVariant: '#CAC4D0',
  labelDim: '#7A828E',
  shadow: 'rgba(31, 41, 55, 0.14)',
} as const;

/** Material 3 baseline light scheme tokens used by the flat slider. */
export const M3 = {
  primary: '#6750A4',
  onPrimary: '#FFFFFF',
  primaryContainer: '#EADDFF',
  onPrimaryContainer: '#21005D',
  secondaryContainer: '#E8DEF8',
  surfaceContainerHighest: '#E6E0E9',
  inverseSurface: '#322F35',
  inverseOnSurface: '#F5EFF7',
} as const;

/** Scene 1 flat M3 slider layout (logical px inside the stage). */
export const L = {
  stageX: 540,
  stageY: 900,
  trackLeft: 330,
  trackW: 420,
  trackH: 20,
  trackR: 10,
  thumbR: 30,
  stopDotR: 7,
  labelFont: 26,
  tooltipFont: 22,
  tooltipGap: 26,
} as const;

/**
 * Scene 2 liquid-glass slider layout. Same left edge / travel as the flat
 * track so the melt is spatially continuous (330 -> 750 logical x).
 */
export const GLASS = {
  capsuleLeft: 300,
  capsuleW: 480,
  capsuleH: 60,
  capsuleR: 30,
  thumbInset: 34,
  travel: 412,
  thumbR: 29,
  badgeFont: 22,
  badgeGap: 7,
  valueFont: 26,
} as const;

/**
 * Scene 3 five-level "THINKING" slider layout.
 * Five levels share the same travel as the glass capsule (continuous x).
 */
export const THINK = {
  trackLeft: 270,
  trackW: 540,
  trackH: 76,
  trackR: 38,
  notchR: 5,
  thumbW: 92,
  thumbH: 48,
  thumbR: 24,
  /** half thumb + breathing room, so stops sit symmetrically in the track */
  thumbInset: 62,
  travel: 416,
  tickFont: 22,
  titleFont: 26,
  levelFont: 78,
  levelGap: 120,
  gridY: 1130,
  gridCols: 9,
  gridRows: 2,
  gridCell: 26,
  gridGap: 12,
  gridR: 7,
} as const;

/**
 * The five levels: widely-used "intensity stepper" naming.
 * Colors ramp gradually from cool gray through blue / violet / magenta
 * into the animated rainbow at ULTRA (see levelColor / rainbow in util).
 */
export const LEVELS = [
  {name: 'OFF', color: '#8A93A3'},
  {name: 'LOW', color: '#2E7CF6'},
  {name: 'MID', color: '#7048E8'},
  {name: 'HIGH', color: '#D6339B'},
] as const;

export const ULTRA = {
  name: 'ULTRA',
} as const;

/** Scene durations in frames (@60fps). 324+390+660 -24 -30 = 1320. */
export const SCENES = {
  s1: 324,
  s2: 390,
  s3: 660,
} as const;

export const TRANSITIONS = {
  t12: 24,
  t23: 30,
} as const;

/** Scene 3 key beats (local frames). */
export const SWEEP = {
  intro: 42, // morph from glass capsule to thinking slider done by here
  goLow: 70,
  goMid: 140,
  goHigh: 210,
  goUltra: 280,
  arriveUltra: 316,
  lightningIn: 300,
  settle: 616,
} as const;
