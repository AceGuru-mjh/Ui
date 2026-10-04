/**
 * Global video configuration & Material 3 design constants.
 *
 * Concept: a Material 3 circular navigation bar (ring of nav items, M3
 * Expressive FAB-Menu spirit) evolves from a flat tonal assembly into a
 * concentric hierarchy, then melts into Liquid Glass and settles.
 *
 * Timeline (absolute frames @30fps, total 900 = 30s):
 *   Scene 1  0 - 179    M3 circular nav bar assembly + selection orbit
 *   fade 15f overlaps 165-179
 *   Scene 2  165 - 419  concentric hierarchy + cascading navigation + float
 *   dissolve 20f overlaps 400-419
 *   Scene 3  400 - 719  melt into liquid glass + jelly interactions
 *   Scene 4  720 - 899  converge, settle & breathe finale
 *
 * TransitionSeries total = 180 + 255 + 320 + 180 - 15 - 20 = 900.
 */

import type {IconName} from './icons';

export const VIDEO = {
  width: 1080,
  height: 1920,
  fps: 30,
  durationInFrames: 900,
} as const;

/** Presentation scale applied to the 1080x1920 logical stage. */
export const STAGE_SCALE = 1.25;

/**
 * Material 3 baseline dark scheme (source color #6750A4, m3.material.io).
 * Pure #000000 void is kept as the canvas (AMOLED-style M3 dark).
 */
export const M3 = {
  bg: '#000000',
  primary: '#D0BCFF',
  onPrimary: '#381E72',
  primaryContainer: '#4F378B',
  onPrimaryContainer: '#EADDFF',
  secondary: '#CCC2DC',
  secondaryContainer: '#4A4458',
  onSecondaryContainer: '#E8DEF8',
  tertiary: '#F2B8B6',
  tertiaryContainer: '#633B48',
  surface: '#141218',
  surfaceContainerLow: '#1D1B20',
  surfaceContainer: '#211F26',
  surfaceContainerHigh: '#2B2930',
  onSurface: '#E6E0E9',
  onSurfaceVariant: '#CAC4D0',
  outline: '#938F99',
  outlineVariant: '#49454F',
} as const;

/** Circular navigation bar layout (logical px inside the stage). */
export const NAV = {
  cx: 540,
  cy: 880,
  /** outer ring: 5 main destinations */
  outerR: 300,
  outerItemR: 62,
  outerIcon: 46,
  /** inner ring: 4 secondary destinations (scene 2 hierarchy) */
  innerR: 176,
  innerItemR: 44,
  innerIcon: 32,
  /** center hub FAB (M3 Expressive FAB Menu spirit) */
  hubR: 58,
  hubIcon: 40,
  /** ring track stroke */
  trackWidth: 10,
  innerTrackWidth: 6,
  /** halo radius around the active item */
  haloPad: 12,
} as const;

/** Outer ring items, clockwise from the top (degrees, SVG y-down). */
export const OUTER_ITEMS: {icon: IconName; angle: number}[] = [
  {icon: 'home', angle: -90},
  {icon: 'search', angle: -18},
  {icon: 'plus', angle: 54},
  {icon: 'heart', angle: 126},
  {icon: 'person', angle: 198},
];

/** Inner ring items (scene 2). */
export const INNER_ITEMS: {icon: IconName; angle: number}[] = [
  {icon: 'star', angle: -90},
  {icon: 'mail', angle: 0},
  {icon: 'play', angle: 90},
  {icon: 'chat', angle: 180},
];

/** Spokes (inner angle -> outer angle) showing the hierarchy. */
export const SPOKES: {from: number; to: number}[] = [
  {from: -90, to: -90},
  {from: 0, to: -18},
  {from: 90, to: 126},
  {from: 180, to: 198},
];

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

/** Shared travel easing - M3 "emphasized" feel. */
export const TRAVEL_EASE = [0.5, 0.05, 0.25, 1] as const;
