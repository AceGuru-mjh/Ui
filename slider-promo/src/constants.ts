/**
 * Global video configuration & design constants.
 *
 * Timeline (absolute frames @30fps, total 900 = 30s):
 *   Scene 1  0 - 179    basic slider assembly + demo
 *   fade 15f overlaps 165-179
 *   Scene 2  165 - 419  three-layer stack + cascade
 *   dissolve 20f overlaps 400-419
 *   Scene 3  400 - 719  melt into liquid glass + liquid physics demo
 *   Scene 4  720 - 899  settle & breathe finale
 *
 * TransitionSeries total = 180 + 255 + 320 + 180 - 15 - 20 = 900.
 */

export const VIDEO = {
  width: 1080,
  height: 1920,
  fps: 30,
  durationInFrames: 900,
} as const;

/** Presentation scale applied to the 1080x1920 logical stage. */
export const STAGE_SCALE = 1.25;

export const FONT = "'Liberation Sans', 'DejaVu Sans', Arial, sans-serif";

/** Minimalist grayscale palette on pure black void. */
export const C = {
  bg: '#000000',
  track: '#333333',
  trackL2: '#222222',
  trackL3: '#111111',
  endpoint: '#555555',
  endpointL2: '#3d3d3d',
  endpointL3: '#2d2d2d',
  thumb: '#FFFFFF',
  fill: '#FFFFFF',
  label: '#888888',
  labelDim: '#5f5f5f',
  connector: '#2e2e2e',
} as const;

/** Flat slider layout (logical px inside the stage). */
export const L = {
  stageX: 540,
  stageY: 900,
  trackLeft: 340,
  trackW: 400,
  trackH: 4,
  endpointR: 8,
  thumbR: 24,
  layerGap: 40,
  labelFont: 24,
} as const;

/** Liquid glass slider layout. Thumb travel 340..740 matches the flat track. */
export const GLASS = {
  capsuleLeft: 310,
  capsuleW: 460,
  capsuleH: 56,
  capsuleR: 28,
  thumbInset: 30,
  travel: 400,
  thumbR: 26,
  badgeFont: 20,
  badgeGap: 7,
  valueFont: 24,
} as const;

/** Scene durations in frames. */
export const SCENES = {
  s1: 180,
  s2: 255,
  s3: 320,
  s4: 180,
} as const;

export const TRANSITIONS = {
  t12: 15,
  t23: 20,
} as const;

/** Scene 4 continues scene 3's animation phase (hard cut, seamless). */
export const S4_PHASE_OFFSET = 320;
