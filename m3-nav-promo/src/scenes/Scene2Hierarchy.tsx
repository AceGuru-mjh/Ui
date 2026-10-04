import type {FC} from 'react';
import {AbsoluteFill, Easing, useCurrentFrame, useVideoConfig} from 'remotion';
import {INNER_ITEMS, M3, NAV, OUTER_ITEMS, SPOKES} from '../constants';
import {ActiveIndicator, RippleRing} from '../components/ActiveIndicator';
import {NavItem} from '../components/NavItem';
import {ringPos} from '../components/ringGeometry';
import {Stage} from '../components/Stage';
import {Icon} from '../icons';
import {clamp, seg, velocity, wobble} from '../util';
import {popIn, TRAVEL, angDist} from './Scene1M3Basic';

/** Scene-2 outer indicator: Heart(126) -> Person(198) -> Home(270). */
export const s2OuterAngle = (f: number): number => {
  if (f < 78) return 126;
  if (f < 108) return seg(f, 78, 106, 126, 198, TRAVEL);
  if (f < 130) return 198;
  if (f < 160) return seg(f, 130, 158, 198, 270, TRAVEL);
  return 270;
};

/** Scene-2 inner indicator: Star(-90) -> Chat(180), counter-clockwise. */
export const s2InnerAngle = (f: number): number => {
  if (f < 93) return -90;
  if (f < 123) return seg(f, 93, 121, -90, -180, TRAVEL);
  return 180;
};

/** Scene-2 outer arrival frames (person @106, home @158). */
export const S2_OUTER_ARRIVALS = [106, 158];
export const S2_INNER_ARRIVAL = 121;

/**
 * Scene 2 (frames 165-419 absolute): hierarchy & depth.
 * A concentric second ring and a center FAB hub grow out of the single
 * circular nav bar - the M3 tonal-elevation ladder reads as depth:
 *   12-48   inner ring (surface-container, lower elevation) pops in
 *   24-60   center FAB hub (primary-container) pops in + glow
 *   36-72   flowing dashed spokes connect the hierarchy levels
 *   78-106  outer selection travels Heart -> Person
 *   93-121  inner selection cascades 15f later Star -> Chat
 *   130-158 outer selection travels Person -> Home; hub ripples on arrivals
 *   168-195 the whole assembly floats up (-10px) - content suspended in void
 *   195-235 slow counter-rotating orbital drift (parallax depth)
 */
export const Scene2: FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();

  const outerAng = s2OuterAngle(f);
  const innerAng = s2InnerAngle(f);

  // hub
  const hubBorn = 24;
  const hubPop = popIn(f - hubBorn, fps, {damping: 12, stiffness: 170});
  const hubPress =
    Math.abs(wobble(f, 106, 0.1, 5, 1.25)) + Math.abs(wobble(f, 158, 0.1, 5, 1.25));

  // float up + slight grow
  const floatY = seg(f, 168, 195, 0, -10, Easing.out(Easing.cubic));
  const groupScale = 1 + 0.01 * seg(f, 168, 195, 0, 1, Easing.out(Easing.cubic));

  // slow orbital drift (parallax)
  const outerDrift = seg(f, 195, 235, 0, 4, Easing.inOut(Easing.cubic));
  const innerDrift = seg(f, 195, 235, 0, -6, Easing.inOut(Easing.cubic));

  // spokes opacity + flowing dash
  const spokeOp = seg(f, 36, 72, 0, 0.9, Easing.out(Easing.cubic));
  const dashOffset = -f * 0.55;

  // selection weights
  const selOuter = (angle: number) => clamp(1 - angDist(outerAng, angle) / 46, 0, 1);
  const selInner = (angle: number) => clamp(1 - angDist(innerAng, angle) / 40, 0, 1);

  // outer indicator dynamics
  const vOut = Math.abs(velocity((x) => s2OuterAngle(x), f));
  const stretchOut = 1 + Math.min(vOut, 3) * 0.035;
  const glowOut = clamp(1 - vOut * 5, 0, 1);
  const posOut = ringPos(NAV.cx, NAV.cy, NAV.outerR, outerAng);
  const posIn = ringPos(NAV.cx, NAV.cy, NAV.innerR, innerAng);
  const morph = clamp(
    Math.abs(wobble(f, 106, 0.9, 7, 0.55)) +
      Math.abs(wobble(f, 158, 0.9, 7, 0.55)),
    0,
    1,
  );

  const vIn = Math.abs(velocity((x) => s2InnerAngle(x), f));
  const glowIn = clamp(1 - vIn * 5, 0, 1);

  // ripple arrivals
  const rippleOuter = (angle: number): number => {
    let r = 0;
    if (angle === 198) r += wobble(f, 106, 0.03, 6, 1.1);
    if (angle === 270 || angle === -90) r += wobble(f, 158, 0.03, 6, 1.1);
    return r;
  };

  return (
    <AbsoluteFill style={{backgroundColor: M3.bg}}>
      <Stage>
      <div
        style={{
          position: 'absolute',
          left: 0,
          top: 0,
          width: 1080,
          height: 1920,
          translate: `0px ${floatY}px`,
          scale: `${groupScale}`,
          transformOrigin: `${NAV.cx}px ${NAV.cy}px`,
        }}
      >
        {/* tracks */}
        <svg width={1080} height={1920} style={{position: 'absolute', left: 0, top: 0}}>
          <circle
            cx={NAV.cx}
            cy={NAV.cy}
            r={NAV.outerR}
            fill="none"
            stroke={M3.surfaceContainerLow}
            strokeWidth={NAV.trackWidth}
          />
          <circle
            cx={NAV.cx}
            cy={NAV.cy}
            r={NAV.innerR}
            fill="none"
            stroke={M3.surfaceContainer}
            strokeWidth={NAV.innerTrackWidth}
            opacity={seg(f, 12, 40, 0, 1, Easing.out(Easing.cubic))}
          />
          {/* hierarchy spokes (flowing dashes) */}
          {SPOKES.map((s, i) => {
            const from = ringPos(NAV.cx, NAV.cy, NAV.innerR, s.from + innerDrift);
            const to = ringPos(NAV.cx, NAV.cy, NAV.outerR, s.to + outerDrift);
            return (
              <line
                key={i}
                x1={from.x}
                y1={from.y}
                x2={to.x}
                y2={to.y}
                stroke={M3.outlineVariant}
                strokeWidth={1.5}
                strokeDasharray="4 8"
                strokeDashoffset={dashOffset}
                opacity={spokeOp * 0.9}
              />
            );
          })}
        </svg>

        {/* hub FAB (M3 Expressive FAB-Menu spirit) */}
        {hubPop > 0.001 && (
          <div
            style={{
              position: 'absolute',
              left: NAV.cx - NAV.hubR,
              top: NAV.cy - NAV.hubR,
              width: NAV.hubR * 2,
              height: NAV.hubR * 2,
              scale: `${hubPop * (1 + hubPress)}`,
            }}
          >
            {/* elevation glow */}
            <div
              style={{
                position: 'absolute',
                inset: -26,
                borderRadius: '50%',
                background:
                  'radial-gradient(circle, rgba(208,188,255,0.30) 0%, rgba(0,0,0,0) 68%)',
                opacity: 0.9,
              }}
            />
            <div
              style={{
                position: 'absolute',
                inset: 0,
                borderRadius: '50%',
                backgroundColor: M3.primaryContainer,
              }}
            >
              <Icon name="plus" size={NAV.hubIcon} color={M3.onPrimaryContainer} />
            </div>
          </div>
        )}
        {/* M3 press ripples on the hub */}
        <RippleRing x={NAV.cx} y={NAV.cy} frame={f} t0={106} fps={fps} maxR={110} />
        <RippleRing x={NAV.cx} y={NAV.cy} frame={f} t0={158} fps={fps} maxR={110} />

        {/* inner-ring indicator */}
        <ActiveIndicator
          x={posIn.x}
          y={posIn.y}
          r={NAV.innerItemR + 5}
          morph={clamp(Math.abs(wobble(f, S2_INNER_ARRIVAL, 0.9, 7, 0.55)), 0, 1)}
          stretch={1 + Math.min(vIn, 3) * 0.035}
          rot={innerAng + 90}
          glow={glowIn}
          frame={f}
          bornAt={70}
          fps={fps}
          color={M3.secondaryContainer}
          glowColor="rgba(204,194,220,0.22)"
        />

        {/* outer-ring indicator */}
        <ActiveIndicator
          x={posOut.x}
          y={posOut.y}
          r={NAV.outerItemR + 6}
          morph={morph}
          stretch={stretchOut}
          rot={outerAng + 90}
          glow={glowOut}
          frame={f}
          bornAt={0}
          fps={fps}
          color={M3.primaryContainer}
          glowColor="rgba(208,188,255,0.24)"
        />

        {/* inner-ring items */}
        {INNER_ITEMS.map((it, i) => {
          const born = 12 + i * 9;
          const p = ringPos(NAV.cx, NAV.cy, NAV.innerR, it.angle + innerDrift);
          return (
            <NavItem
              key={it.icon}
              x={p.x}
              y={p.y}
              r={NAV.innerItemR}
              icon={it.icon}
              iconSize={NAV.innerIcon}
              selected={selInner(it.angle)}
              appear={popIn(f - born, fps, {damping: 14, stiffness: 190})}
              ripple={it.angle === 180 ? wobble(f, S2_INNER_ARRIVAL, 0.03, 6, 1.1) : 0}
              level="container"
            />
          );
        })}

        {/* outer-ring items (already assembled - carried over from scene 1) */}
        {OUTER_ITEMS.map((it) => {
          const p = ringPos(NAV.cx, NAV.cy, NAV.outerR, it.angle + outerDrift);
          return (
            <NavItem
              key={it.icon}
              x={p.x}
              y={p.y}
              r={NAV.outerItemR}
              icon={it.icon}
              iconSize={NAV.outerIcon}
              selected={selOuter(it.angle)}
              appear={1}
              ripple={rippleOuter(it.angle)}
              level="high"
            />
          );
        })}
      </div>
      </Stage>
    </AbsoluteFill>
  );
};
