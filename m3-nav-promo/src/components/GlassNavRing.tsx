import type {FC} from 'react';
import {Easing} from 'remotion';
import {M3, NAV, OUTER_ITEMS} from '../constants';
import {AmbientField} from './AmbientField';
import {GlassCircle, GLASS_ICON_ACTIVE, GLASS_ICON_IDLE} from './GlassCircle';
import {Icon} from '../icons';
import {ParticleField} from './ParticleField';
import {liquidRingPath, ringPos} from './ringGeometry';
import {clamp, seg, velocity, wobble} from '../util';
import {mix} from './NavItem';
import {TRAVEL, angDist} from '../scenes/Scene1M3Basic';

/**
 * Liquid-glass indicator angle, as a pure function of the animation phase
 * (scene-3 local frames; scene 4 continues at phase 320+):
 *   Home(-90) --p90-138--> Search(-18) --p160-208--> Heart(126)
 *   --p230-278--> Person(198) --p330-363--> Home(270) [scene 4 finale]
 */
export const glassAngle = (p: number): number => {
  if (p < 90) return -90;
  if (p < 138) return seg(p, 90, 138, -90, -18, TRAVEL);
  if (p < 160) return -18;
  if (p < 208) return seg(p, 160, 208, -18, 126, TRAVEL);
  if (p < 230) return 126;
  if (p < 278) return seg(p, 230, 278, 126, 198, TRAVEL);
  if (p < 330) return 198;
  if (p < 363) return seg(p, 330, 363, 198, 270, TRAVEL);
  return 270;
};

/** Glass-phase arrivals: search @138, heart @208, person @278, home @363. */
export const GLASS_ARRIVALS = [138, 208, 278, 363];

export type GlassNavRingProps = {
  /** continuous animation phase (scene-3 local, 320+ in scene 4) */
  phase: number;
  /** 0 = flat M3 tonal circles, 1 = fully liquid glass */
  glassiness: number;
  /** unwinding ring drift (scene 3 opening inherits scene 2's drift) */
  angleDrift?: number;
  /** whole-assembly scale (entrance spring / finale pulse) */
  groupScale?: number;
  /** whole-assembly opacity */
  groupOpacity?: number;
  /** ambient tonal fields behind the glass */
  ambientMaster?: number;
  /** particle field visibility */
  particleMaster?: number;
  /** 0..1 specular rim sweep (scene 4 finale highlight orbit) */
  finaleSweep?: number;
};

/**
 * The liquid glass circular navigation bar: wobbling molten track, five
 * refracting glass circles, a glass indicator blob with jelly squash, and a
 * magnifying glass FAB hub at the center. Pure function of `phase`.
 */
export const GlassNavRing: FC<GlassNavRingProps> = ({
  phase,
  glassiness,
  angleDrift = 0,
  groupScale = 1,
  groupOpacity = 1,
  ambientMaster = 1,
  particleMaster = 1,
  finaleSweep = 0,
}) => {
  const g = clamp(glassiness, 0, 1);

  const ang = glassAngle(phase) + angleDrift;
  const v = Math.abs(velocity((x) => glassAngle(x), phase));
  const stretch = 1 + Math.min(v, 3) * 0.06;

  // selection weight per item
  const sel = (angle: number) => clamp(1 - angDist(ang, angle) / 46, 0, 1);

  // liquid track wobble amplitude reacts to indicator speed
  const amp = 2 + Math.min(v, 3) * 1.1;
  const trackPath = liquidRingPath(NAV.cx, NAV.cy, NAV.outerR, amp, phase);

  // per-item ripple on indicator arrival (+ passing ripple on Plus ~p184)
  const rippleOf = (angle: number): number => {
    let r = 0;
    if (angle === -18) r += wobble(phase, 138, 0.03, 6, 1.1);
    if (angle === 126) r += wobble(phase, 208, 0.03, 6, 1.1);
    if (angle === 198) r += wobble(phase, 278, 0.03, 6, 1.1);
    if (angle === -90 || angle === 270) r += wobble(phase, 363, 0.03, 6, 1.1);
    if (angle === 54) r += wobble(phase, 184, 0.025, 6, 1.0);
    return r;
  };

  // hub breathing + arrival pulses
  const hubBreath = 1 + 0.04 * Math.sin((phase / 60) * Math.PI * 2 + Math.PI / 3);
  const hubPulse =
    Math.abs(wobble(phase, 138, 0.09, 5, 1.2)) +
    Math.abs(wobble(phase, 208, 0.09, 5, 1.2)) +
    Math.abs(wobble(phase, 278, 0.09, 5, 1.2)) +
    Math.abs(wobble(phase, 363, 0.09, 5, 1.2));

  const pos = ringPos(NAV.cx, NAV.cy, NAV.outerR, ang);

  // flat-vs-glass icon colors (continuous through the melt)
  const iconColor = (w: number): string =>
    mix(
      mix(M3.onSurfaceVariant, M3.onPrimaryContainer, w),
      w > 0.5 ? GLASS_ICON_ACTIVE : GLASS_ICON_IDLE,
      g,
    );

  return (
    <div
      style={{
        position: 'absolute',
        left: 0,
        top: 0,
        width: 1080,
        height: 1920,
        scale: `${groupScale}`,
        opacity: clamp(groupOpacity, 0, 1),
        transformOrigin: `${NAV.cx}px ${NAV.cy}px`,
      }}
    >
      {/* ambient tonal fields + particles (the "content behind the glass") */}
      <AmbientField phase={phase} master={ambientMaster} />
      <ParticleField phase={phase} master={particleMaster} />

      {/* molten ring track */}
      <svg width={1080} height={1920} style={{position: 'absolute', left: 0, top: 0}}>
        <circle
          cx={NAV.cx}
          cy={NAV.cy}
          r={NAV.outerR}
          fill="none"
          stroke={M3.surfaceContainerLow}
          strokeWidth={NAV.trackWidth}
          opacity={(1 - g) * 0.9}
        />
        <path
          d={trackPath}
          fill="none"
          stroke="rgba(255,255,255,0.10)"
          strokeWidth={8}
          strokeLinecap="round"
          opacity={g}
        />
      </svg>

      {/* glass FAB hub (center lens) */}
      <GlassCircle
        x={NAV.cx}
        y={NAV.cy}
        r={NAV.hubR + 4}
        phase={phase}
        glassiness={g}
        flatColor={M3.primaryContainer}
        flatBorder="rgba(255,255,255,0.12)"
        mag={1.3}
        disp={2.2}
        scaleX={hubBreath * (1 + hubPulse)}
        scaleY={hubBreath * (1 + hubPulse)}
      >
        <div style={{position: 'absolute', inset: 0, scale: `${1 + hubPulse * 0.5}`}}>
          <Icon
            name="plus"
            size={NAV.hubIcon}
            color={mix(M3.onPrimaryContainer, '#FFFFFF', g)}
            opacity={0.55 + 0.45 * g}
          />
        </div>
      </GlassCircle>

      {/* glass indicator blob */}
      <GlassCircle
        x={pos.x}
        y={pos.y}
        r={NAV.outerItemR + NAV.haloPad}
        phase={phase}
        glassiness={g}
        flatColor={M3.primaryContainer}
        flatBorder="rgba(255,255,255,0.10)"
        mag={1.22}
        disp={2.0}
        scaleX={stretch}
        scaleY={2 - stretch}
      />

      {/* glass nav items */}
      {OUTER_ITEMS.map((it) => {
        const p = ringPos(NAV.cx, NAV.cy, NAV.outerR, it.angle + angleDrift);
        const w = sel(it.angle);
        const breath = 1 + 0.008 * Math.sin((phase / 60) * Math.PI * 2 + it.angle);
        const s = breath * (1 + rippleOf(it.angle));
        return (
          <GlassCircle
            key={it.icon}
            x={p.x}
            y={p.y}
            r={NAV.outerItemR}
            phase={phase}
            glassiness={g}
            flatColor={mix(M3.surfaceContainerHigh, M3.primaryContainer, w)}
            flatBorder={M3.outlineVariant}
            mag={1.16}
            disp={1.6}
            scaleX={s}
            scaleY={s}
          >
            <div style={{position: 'absolute', inset: 0}}>
              <Icon name={it.icon} size={NAV.outerIcon} color={iconColor(w)} />
            </div>
          </GlassCircle>
        );
      })}

      {/* finale specular sweep: a bright highlight orbiting the rim */}
      {finaleSweep > 0.001 && finaleSweep < 0.999 && (
        <>
          {[0, 0.035, 0.07].map((lag, i) => {
            const s = clamp(finaleSweep - lag, 0, 1);
            const a = -90 + s * 360;
            const sp = ringPos(NAV.cx, NAV.cy, NAV.outerR, a);
            const fade = Math.sin(Math.PI * clamp(s, 0.02, 0.98));
            const size = 30 - i * 8;
            return (
              <div
                key={i}
                style={{
                  position: 'absolute',
                  left: sp.x - size / 2,
                  top: sp.y - size / 2,
                  width: size,
                  height: size,
                  borderRadius: '50%',
                  background:
                    'radial-gradient(circle, rgba(255,255,255,0.95) 0%, rgba(208,188,255,0.4) 40%, rgba(0,0,0,0) 70%)',
                  filter: 'blur(2px)',
                  opacity: (i === 0 ? 0.95 : 0.5) * fade,
                }}
              />
            );
          })}
        </>
      )}
    </div>
  );
};

/** Melt-in glass materialization easing (re-exported for scenes). */
export const MELT_EASE = Easing.inOut(Easing.cubic);
