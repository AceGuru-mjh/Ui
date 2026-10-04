import type {FC} from 'react';
import {AbsoluteFill, Easing, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {M3, NAV, OUTER_ITEMS} from '../constants';
import {ActiveIndicator} from '../components/ActiveIndicator';
import {NavItem} from '../components/NavItem';
import {ringPos} from '../components/ringGeometry';
import {Stage} from '../components/Stage';
import {clamp, seg, velocity, wobble} from '../util';

/** Shared travel easing (M3 emphasized). */
export const TRAVEL = Easing.bezier(0.5, 0.05, 0.25, 1);

/** Spring 0->1 pop-in. */
export const popIn = (
  frame: number,
  fps: number,
  config: {damping: number; stiffness: number},
): number =>
  frame <= 0
    ? 0
    : spring({frame, fps, from: 0, to: 1, config: {...config, mass: 1}});

/** Angular distance in degrees (wraps). */
export const angDist = (a: number, b: number): number =>
  Math.abs((((a - b) % 360) + 540) % 360 - 180);

/** Scene-1 indicator angle: Home(-90) -> Search(-18) -> Heart(126). */
export const s1Angle = (f: number): number => {
  if (f < 92) return -90;
  if (f < 122) return seg(f, 92, 120, -90, -18, TRAVEL);
  if (f < 130) return -18;
  if (f < 160) return seg(f, 130, 158, -18, 126, TRAVEL);
  return 126;
};

/** Scene-1 arrival frames (search @120, heart @158). */
export const S1_ARRIVALS = [120, 158];

/**
 * Scene 1 (frames 0-179): the Material 3 circular navigation bar assembles.
 *   0-18    ring track draws in (M3 emphasized decelerate)
 *   16-64   five nav items pop in with staggered expressive springs
 *   64-84   active indicator pops at Home (primary-container halo)
 *   92-122  indicator travels Home -> Search (tangent squash, jello arrival)
 *   130-160 indicator travels Search -> Heart (passing ripple on Plus)
 *   160-180 settle: shape-morph relaxes to circle, halo breathes
 */
export const Scene1: FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();

  // ring track draw-in
  const trackP = seg(f, 0, 18, 0, 1, Easing.out(Easing.cubic));

  // selection weight per item (indicator proximity)
  const ang = s1Angle(f);
  const sel = (angle: number) => clamp(1 - angDist(ang, angle) / 46, 0, 1);

  // indicator dynamics: tight pill while travelling, halo blooms at rest
  const v = Math.abs(velocity((x) => s1Angle(x), f));
  const stretch = 1 + Math.min(v, 3) * 0.02;
  const glow = clamp(1 - v * 5, 0, 1);
  // M3 shape morph: squircle pulse that decays after each arrival
  const morph = clamp(
    Math.abs(wobble(f, 120, 0.9, 7, 0.55)) +
      Math.abs(wobble(f, 158, 0.9, 7, 0.55)),
    0,
    1,
  );
  const pos = ringPos(NAV.cx, NAV.cy, NAV.outerR, ang);

  // item ripple: arrival bounce + passing ripple on Plus (~f145)
  const rippleOf = (angle: number): number => {
    let r = 0;
    if (angle === -18) r += wobble(f, 120, 0.03, 6, 1.1);
    if (angle === 126) r += wobble(f, 158, 0.03, 6, 1.1);
    if (angle === 54) r += wobble(f, 145, 0.025, 6, 1.0);
    return r;
  };

  return (
    <AbsoluteFill style={{backgroundColor: M3.bg}}>
      <Stage>
      {/* ring track (draw-in sweep) */}
      <svg
        width={1080}
        height={1920}
        style={{position: 'absolute', left: 0, top: 0}}
      >
        <circle
          cx={NAV.cx}
          cy={NAV.cy}
          r={NAV.outerR}
          fill="none"
          stroke={M3.surfaceContainerLow}
          strokeWidth={NAV.trackWidth}
          pathLength={1}
          strokeDasharray={1}
          strokeDashoffset={1 - trackP}
          strokeLinecap="round"
          transform={`rotate(-90 ${NAV.cx} ${NAV.cy})`}
        />
      </svg>

      {/* active indicator (rendered first => sits behind the items) */}
      <ActiveIndicator
        x={pos.x}
        y={pos.y}
        r={NAV.outerItemR + 6}
        morph={morph}
        stretch={stretch}
        rot={ang + 90}
        glow={glow}
        frame={f}
        bornAt={64}
        fps={fps}
        color={M3.primaryContainer}
        glowColor="rgba(208,188,255,0.24)"
      />

      {/* nav items */}
      {OUTER_ITEMS.map((it, i) => {
        const born = 16 + i * 8;
        const p = ringPos(NAV.cx, NAV.cy, NAV.outerR, it.angle);
        return (
          <NavItem
            key={it.icon}
            x={p.x}
            y={p.y}
            r={NAV.outerItemR}
            icon={it.icon}
            iconSize={NAV.outerIcon}
            selected={sel(it.angle)}
            appear={popIn(f - born, fps, {damping: 14, stiffness: 190})}
            ripple={rippleOf(it.angle)}
            level="high"
          />
        );
      })}
      </Stage>
    </AbsoluteFill>
  );
};
