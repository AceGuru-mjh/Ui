/**
 * Global video configuration & design constants (day edition).
 *
 * Timeline (absolute frames @60fps, total 1320 = 22.0s):
 *   Scene 1  0   - 389   block-built slider assembly + demo (积木拼装)
 *   fade 24f overlaps 366-389
 *   Scene 2  366 - 695   melt into liquid glass + material beats
 *   dissolve 30f overlaps 666-695
 *   Scene 3  666 - 1319  five-level neural glass slider + neon finale
 *
 * TransitionSeries total = 390 + 330 + 654 - 24 - 30 = 1320.
 *
 * Day mode: pure white void, depth comes from hairlines and soft shadows,
 * the palette warms up level by level and ends in amber neon (#FFB020).
 */

export const VIDEO = {
  width: 1080,
  height: 1920,
  fps: 60,
  durationInFrames: 1320,
} as const;

/** Presentation scale applied to the 1080x1920 logical stage. */
export const STAGE_SCALE = 1.25;

export const FONT = "'Liberation Sans', 'DejaVu Sans', Arial, sans-serif";
export const MONO = "'Liberation Mono', 'DejaVu Sans Mono', monospace";

/** Minimalist day palette on pure white. */
export const C = {
  bg: '#FFFFFF',
  ink: '#111111',
  inkSoft: '#3C3C43',
  label: '#3C3C43',
  labelDim: '#9A9AA0',
  brickRest: '#E3E2DE',
  brickLit: '#111111',
  endpoint: '#111111',
  thumbFace: '#FFFFFF',
  thumbLine: '#111111',
  hairline: 'rgba(17,17,17,0.16)',
  glassBorder: 'rgba(17,17,17,0.12)',
  glassFillTop: '#F6F4F0',
  glassFillBottom: '#E7E3DC',
  shadowFar: 'rgba(17,17,17,0.10)',
  shadowNear: 'rgba(17,17,17,0.05)',
} as const;

/** Flat block-built slider layout (logical px inside the stage). */
export const L = {
  stageX: 540,
  stageY: 940,
  trackLeft: 320,
  trackW: 440,
  bricks: 8,
  brickGap: 6,
  brickH: 12,
  endpointW: 10,
  endpointH: 44,
  thumbSize: 52,
  thumbR: 14,
  labelFont: 26,
} as const;

/** Liquid glass capsule (scene 2). Thumb travel 340..740. */
export const GLASS = {
  capsuleLeft: 310,
  capsuleW: 460,
  capsuleH: 56,
  capsuleR: 28,
  thumbInset: 30,
  travel: 400,
  thumbR: 26,
} as const;

/** Five-level neural glass slider (scene 3). */
export const LEVEL = {
  capsuleLeft: 240,
  capsuleW: 600,
  capsuleH: 64,
  capsuleR: 32,
  /** stop positions in % of inner travel */
  stops: [8, 29, 50, 71, 92],
  thumbR: 30,
  dividerFs: [18.5, 39.5, 60.5, 81.5],
  /** blocky level meter above the capsule */
  meterCols: 5,
  meterRows: 3,
  cell: 18,
  cellGap: 7,
  meterCy: 866,
  labelCy: 792,
  badgesCy: 1010,
  terminalCy: 1076,
} as const;

/** Level themes: a warm ramp that lands on amber neon. */
export const LEVELS = [
  {name: 'OPACITY', color: '#7D8590', deep: '#565D66'},
  {name: 'BRIGHTNESS', color: '#A89A85', deep: '#7C7160'},
  {name: 'BLUR', color: '#C9A96A', deep: '#96793F'},
  {name: 'LIQUID GLASS', color: '#F0B24A', deep: '#B57F1D'},
  {name: 'NEURAL GLASS', color: '#FFB020', deep: '#C77F00'},
] as const;

export const AMBER = '#FFB020';
export const AMBER_DEEP = '#C77F00';

/** Scene durations in frames. */
export const SCENES = {
  s1: 390,
  s2: 330,
  s3: 654,
} as const;

export const TRANSITIONS = {
  t12: 24,
  t23: 30,
} as const;
