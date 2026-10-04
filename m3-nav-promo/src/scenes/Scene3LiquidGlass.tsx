import type {FC} from 'react';
import {AbsoluteFill, Easing, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {INNER_ITEMS, M3, NAV, SPOKES} from '../constants';
import {NavItem} from '../components/NavItem';
import {GlassNavRing, MELT_EASE} from '../components/GlassNavRing';
import {FilterDefs} from '../components/GlassCircle';
import {ringPos} from '../components/ringGeometry';
import {Stage} from '../components/Stage';
import {Icon} from '../icons';
import {clamp01, seg} from '../util';

/** Scene-2 final state constants for the opening crossfade. */
const S2_FINAL = {
  outerDrift: 4,
  innerDrift: -6,
  innerSel: 180, // chat selected
};

/**
 * Scene 3 (frames 400-719 absolute): the material transformation.
 * The concentric hierarchy melts into one liquid glass navigation bar:
 *   0-34    flat stack (scene-2 final state) dissolves; the inner ring is
 *           absorbed into the hub FAB (blobs merging = liquid)
 *   12-70   glass materializes: refraction, dispersion, backdrop blur,
 *           molten wobbling track; ambient tonal fields fade in behind
 *   20-80   particle field fades in (refracted through the glass)
 *   90-363  viscous glass indicator orbits with jelly squash & jello
 *           arrivals (Search p138, Heart p208, Person p278)
 *   280-320 arrival and rest at Person
 */
export const Scene3: FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();

  // flat residue (scene-2 final state) dissolving
  const flatOp = seg(f, 4, 34, 1, 0, Easing.in(Easing.cubic));

  // inner-ring absorption into the hub (liquid merge)
  const absorbP = seg(f, 8, 34, 0, 1, Easing.inOut(Easing.cubic));
  const innerScale = 1 - 0.85 * absorbP;
  const innerRadius = NAV.innerR * (1 - absorbP) + 0.0;

  // glass materialization
  const g = seg(f, 12, 70, 0, 1, MELT_EASE);
  const groupScale =
    f < 8
      ? 0.94
      : spring({
          frame: f - 8,
          fps,
          from: 0.94,
          to: 1,
          config: {damping: 14, stiffness: 120, mass: 1},
        });

  const ambientMaster = seg(f, 10, 60, 0, 1, Easing.out(Easing.cubic));
  const particleMaster = seg(f, 20, 80, 0, 1, Easing.out(Easing.cubic));

  // ring drift unwinds as the material melts
  const drift = seg(f, 0, 70, S2_FINAL.outerDrift, 0, MELT_EASE);

  return (
    <AbsoluteFill style={{backgroundColor: M3.bg}}>
      <FilterDefs />
      <Stage>
        {/* flat residue: tracks, spokes, absorbing inner ring, flat hub */}
        {flatOp > 0.004 && (
          <div style={{position: 'absolute', left: 0, top: 0, width: 1080, height: 1920, opacity: flatOp}}>
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
                opacity={1 - absorbP}
              />
              {SPOKES.map((s, i) => {
                const from = ringPos(NAV.cx, NAV.cy, NAV.innerR, s.from + S2_FINAL.innerDrift);
                const to = ringPos(NAV.cx, NAV.cy, NAV.outerR, s.to + S2_FINAL.outerDrift);
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
                    opacity={0.9 * (1 - absorbP)}
                  />
                );
              })}
            </svg>
            {/* inner ring gliding into the hub (absorbed) */}
            {INNER_ITEMS.map((it) => {
              const a = it.angle + S2_FINAL.innerDrift;
              const p0 = ringPos(NAV.cx, NAV.cy, NAV.innerR, a);
              const dx = (NAV.cx - p0.x) * absorbP;
              const dy = (NAV.cy - p0.y) * absorbP;
              return (
                <div
                  key={it.icon}
                  style={{
                    position: 'absolute',
                    left: p0.x - NAV.innerItemR + dx,
                    top: p0.y - NAV.innerItemR + dy,
                    width: NAV.innerItemR * 2,
                    height: NAV.innerItemR * 2,
                    scale: `${innerScale}`,
                    opacity: clamp01(1 - absorbP * 1.15),
                  }}
                >
                  <NavItem
                    x={NAV.innerItemR}
                    y={NAV.innerItemR}
                    r={NAV.innerItemR}
                    icon={it.icon}
                    iconSize={NAV.innerIcon}
                    selected={it.angle === S2_FINAL.innerSel ? 1 : 0}
                    appear={1}
                    level="container"
                  />
                </div>
              );
            })}
          </div>
        )}

        {/* flat hub crossfading into the glass hub (handled by glassiness) */}
        {flatOp > 0.004 && (
          <div
            style={{
              position: 'absolute',
              left: NAV.cx - NAV.hubR,
              top: NAV.cy - NAV.hubR,
              width: NAV.hubR * 2,
              height: NAV.hubR * 2,
              opacity: flatOp,
            }}
          >
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

        {/* the liquid glass navigation bar */}
        <GlassNavRing
          phase={f}
          glassiness={g}
          angleDrift={drift}
          groupScale={groupScale}
          ambientMaster={ambientMaster}
          particleMaster={particleMaster}
        />
      </Stage>
    </AbsoluteFill>
  );
};
