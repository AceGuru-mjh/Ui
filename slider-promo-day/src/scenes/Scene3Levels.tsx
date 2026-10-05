import type {FC} from 'react';
import {AbsoluteFill, Easing, useCurrentFrame} from 'remotion';
import {AMBER, C, FONT, LEVEL, LEVELS, L, MONO} from '../constants';
import {FilterDefsDay} from '../components/FilterDefsDay';
import {GlassBall} from '../components/GlassBall';
import {GlassCapsule} from '../components/GlassCapsule';
import {LedStrip} from '../components/LedStrip';
import {LevelMeter} from '../components/LevelMeter';
import {NeonField} from '../components/NeonField';
import {Stage} from '../components/Stage';
import {clamp, clamp01, mixHex, rgba, seg, velocity, wobble} from '../util';

/**
 * Scene 3 (local frames 0-653 @60fps): the five-level neural glass slider.
 *  0-34    capsule stretches 460 -> 600 wide, 56 -> 64 tall
 *  36-80   dividers & stop squares pop in
 *  46-100  thumb glides from center to stop 1 (OPACITY)
 *  88-130  blocky level meter + numbered badges assemble
 *  130-430 beat-by-beat advance: OPACITY -> BRIGHTNESS -> BLUR ->
 *          LIQUID GLASS -> NEURAL GLASS, theme warms level by level
 *  430     LEVEL 5 ignition: amber bloom, LED matrix, lightning square field
 *  560     completion pulse
 *  600-654 breathing hold (ends lit, no fade to white)
 */
const ARRIVALS = [100, 196, 274, 352, 430];
const VIRTUAL_START = 50; // scene 2 hands over at 50%

export const scene3Value = (f: number): number => {
  if (f < 46) {
    return VIRTUAL_START;
  }
  if (f < 100) {
    return seg(f, 46, 100, VIRTUAL_START, LEVEL.stops[0], Easing.out(Easing.cubic));
  }
  if (f < 130) {
    return LEVEL.stops[0];
  }
  if (f < 196) {
    return seg(f, 130, 196, LEVEL.stops[0], LEVEL.stops[1], Easing.bezier(0.5, 0.05, 0.25, 1));
  }
  if (f < 274) {
    return seg(f, 196, 274, LEVEL.stops[1], LEVEL.stops[2], Easing.bezier(0.5, 0.05, 0.25, 1));
  }
  if (f < 352) {
    return seg(f, 274, 352, LEVEL.stops[2], LEVEL.stops[3], Easing.bezier(0.5, 0.05, 0.25, 1));
  }
  if (f < 430) {
    return seg(f, 352, 430, LEVEL.stops[3], LEVEL.stops[4], Easing.bezier(0.5, 0.05, 0.25, 1));
  }
  return LEVEL.stops[4];
};

const levelIndex = (f: number): number =>
  f < ARRIVALS[1] ? 0 : f < ARRIVALS[2] ? 1 : f < ARRIVALS[3] ? 2 : f < ARRIVALS[4] ? 3 : 4;

/** Theme color, easing level by level as arrivals pass. */
const themeAt = (f: number): string => {
  let color: string = LEVELS[0].color;
  for (let k = 1; k < LEVELS.length; k += 1) {
    if (f > ARRIVALS[k]) {
      color = mixHex(color, LEVELS[k].color, seg(f, ARRIVALS[k], ARRIVALS[k] + 22, 0, 1, Easing.out(Easing.cubic)));
    }
  }
  return color;
};

/** Deep variant of the theme ramp (for text/contrast on white). */
const themeDeepAt = (f: number): string => {
  let color: string = LEVELS[0].deep;
  for (let k = 1; k < LEVELS.length; k += 1) {
    if (f > ARRIVALS[k]) {
      color = mixHex(color, LEVELS[k].deep, seg(f, ARRIVALS[k], ARRIVALS[k] + 22, 0, 1, Easing.out(Easing.cubic)));
    }
  }
  return color;
};

export const Scene3: FC = () => {
  const f = useCurrentFrame();
  const v = clamp(scene3Value(f), 0, 100);
  const vel = Math.abs(velocity(scene3Value, f, 2));

  // geometry morph GLASS -> LEVEL
  const m = seg(f, 0, 34, 0, 1, Easing.out(Easing.cubic));
  const geoW = seg(f, 0, 34, 460, LEVEL.capsuleW, Easing.out(Easing.cubic));
  const geoH = seg(f, 0, 34, 56, LEVEL.capsuleH, Easing.out(Easing.cubic));
  const geoLeft = 540 - geoW / 2;
  const capsuleTop = L.stageY - geoH / 2;
  const geo = {left: geoLeft, top: capsuleTop, w: geoW, h: geoH, radius: geoH / 2};
  const inset = 30;
  const travel = geoW - 2 * inset;

  const thumbX = geoLeft + inset + (v / 100) * travel;
  const fillEdge = thumbX - geoLeft - 32;
  const stopX = (i: number): number => geoLeft + inset + (LEVEL.stops[i] / 100) * travel;

  const li = levelIndex(f);
  const lv = Math.max(0, li); // 0-based level at rest
  const theme = themeAt(f);
  const themeDeep = themeDeepAt(f);
  const neon = seg(f, ARRIVALS[4], ARRIVALS[4] + 22, 0, 1, Easing.out(Easing.cubic));

  // meter progress: virtual level -1 (approaching L1), then 0..4
  const stopAt = (i: number): number => (i < 0 ? VIRTUAL_START : LEVEL.stops[i]);
  const meterLit = lv + 1;
  const meterPartial =
    lv < 4
      ? clamp01((v - stopAt(lv)) / (stopAt(lv + 1) - stopAt(lv)))
      : 0;

  // liquid physics
  const amp = 1.1 + Math.min(vel * 2.4, 5.5);
  const wavePhase = f * 0.5;
  let sx = 1;
  let sy = 1;
  const stretch = Math.min(vel * 0.010, 0.09);
  sx += stretch;
  sy -= stretch * 0.7;
  for (const t of ARRIVALS) {
    if (f > t) {
      const d = f - t;
      const w = Math.exp(-d / 9) * Math.cos(d * 0.85) * 0.13;
      sx += w;
      sy -= w * 0.8;
    }
  }
  const breath = 1 + 0.010 * Math.sin((2 * Math.PI * f) / 78);

  // fill colors follow the theme
  const fillTop = mixHex('#F6F4F0', theme, 0.22 + 0.16 * neon);
  const fillBottom = mixHex('#E7E3DC', theme, 0.52 + 0.26 * neon);

  // finale group dynamics: completion pulse + breathing
  const pulse =
    f > 560 ? 0.026 * Math.exp(-(f - 560) / 11) * Math.sin((f - 560) * 0.5) : 0;
  const breathe = f > 600 ? 0.006 * Math.sin(((f - 600) * 2 * Math.PI) / 96) : 0;
  const groupScale = 1 + pulse + breathe;

  const labelS = (k: number): number => (k === 0 ? 88 : ARRIVALS[k]);
  const labelE = (k: number): number => (k < 4 ? ARRIVALS[k + 1] : 654);

  return (
    <AbsoluteFill style={{backgroundColor: C.bg}}>
      <FilterDefsDay />
      <NeonField
        p={f}
        master={seg(f, 444, 502, 0, 1, Easing.out(Easing.cubic))}
        capsule={{left: geoLeft, top: capsuleTop, w: geoW, h: geoH}}
        lit={seg(f, 600, 634, 1, 0.55, Easing.inOut(Easing.cubic))}
      />
      <Stage>
        <div
          style={{
            position: 'absolute',
            inset: 0,
            scale: `${groupScale}`,
            transformOrigin: `540px ${L.stageY}px`,
          }}
        >
          {/* amber bloom behind the capsule at level 5 */}
          {neon > 0.005 ? (
            <div
              style={{
                position: 'absolute',
                left: geoLeft - 50,
                top: capsuleTop - 50,
                width: geoW + 100,
                height: geoH + 100,
                borderRadius: geoH / 2 + 50,
                background: `radial-gradient(ellipse at center, ${rgba(AMBER, 0.38 * neon)} 0%, rgba(255,176,32,0) 70%)`,
                filter: 'blur(18px)',
                opacity: neon * (0.9 + 0.1 * Math.sin(f * 0.21)),
              }}
            />
          ) : null}

          {/* the five-level glass capsule */}
          <GlassCapsule
            geo={geo}
            mat={1}
            fillEdge={fillEdge}
            amp={amp}
            wavePhase={wavePhase}
            fillTop={fillTop}
            fillBottom={fillBottom}
            edge={1 + neon * 0.6}
            glassBright={1.03 + 0.05 * neon}
          >
            {/* dividers */}
            {LEVEL.dividerFs.map((fs, i) => {
              const p = seg(f, 36 + i * 6, 48 + i * 6, 0, 1, Easing.out(Easing.cubic));
              if (p <= 0.005) return null;
              return (
                <div
                  key={`d${i}`}
                  style={{
                    position: 'absolute',
                    left: inset + (fs / 100) * travel,
                    top: 12,
                    width: 1.5,
                    height: geoH - 24,
                    backgroundColor: rgba(C.ink, 0.10 * p + 0.04 * neon),
                    scale: `1 ${p}`,
                  }}
                />
              );
            })}
            {/* stop squares */}
            {LEVEL.stops.map((_, i) => {
              const p = seg(f, 44 + i * 7, 58 + i * 7, 0, 1, Easing.out(Easing.cubic));
              if (p <= 0.005) return null;
              const reached = i <= lv && f >= ARRIVALS[i] - 2;
              const color = reached ? theme : 'rgba(17,17,17,0.18)';
              return (
                <div
                  key={`s${i}`}
                  style={{
                    position: 'absolute',
                    left: stopX(i) - geoLeft - 6,
                    top: geoH / 2 - 6,
                    width: 12,
                    height: 12,
                    borderRadius: 3,
                    border: reached ? 'none' : '1.5px solid rgba(17,17,17,0.22)',
                    backgroundColor: reached ? color : 'rgba(255,255,255,0.6)',
                    boxShadow:
                      reached && i === 4
                        ? `0 0 ${(10 * neon).toFixed(1)}px ${rgba(AMBER, 0.7 * neon)}`
                        : undefined,
                    scale: `${p}`,
                  }}
                />
              );
            })}
            {/* LED matrix at level 5 */}
            <LedStrip w={geoW} h={geoH} p={f} master={neon * seg(f, 430, 464, 0.3, 1, Easing.out(Easing.cubic))} />
          </GlassCapsule>

          {/* arrival tick rings */}
          {ARRIVALS.map((a, i) => {
            if (f <= a + 2 || f > a + 30) return null;
            const t = (f - a - 2) / 28;
            return (
              <div
                key={`r${i}`}
                style={{
                  position: 'absolute',
                  left: stopX(i) - 21,
                  top: L.stageY - 21,
                  width: 42,
                  height: 42,
                  borderRadius: 10,
                  border: `2px solid ${theme}`,
                  opacity: 0.35 * (1 - t),
                  scale: `${(0.7 + t * 0.8).toFixed(3)}`,
                }}
              />
            );
          })}

          {/* liquid glass ball thumb */}
          <GlassBall
            x={thumbX}
            y={L.stageY}
            r={LEVEL.thumbR}
            geo={geo}
            fillEdge={fillEdge}
            amp={amp}
            wavePhase={wavePhase}
            fillTop={fillTop}
            fillBottom={fillBottom}
            glassiness={1}
            scaleX={sx * breath}
            scaleY={sy * breath}
            tint={theme}
          />

          {/* blocky level meter */}
          <LevelMeter
            cx={540}
            cy={LEVEL.meterCy}
            cols={LEVEL.meterCols}
            rows={LEVEL.meterRows}
            cell={LEVEL.cell}
            gap={LEVEL.cellGap}
            litCols={meterLit}
            partial={meterPartial}
            color={theme}
            opacity={seg(f, 88, 120, 0, 1, Easing.out(Easing.cubic))}
            pulse={f}
            neon={neon}
          />

          {/* big level label with swap transitions (single flex div) */}
          {LEVELS.map((lv2, k) => {
            const s = labelS(k);
            const e = labelE(k);
            if (f < s || f > e) return null;
            const op =
              seg(f, s, s + 12, 0, 1, Easing.out(Easing.cubic)) *
              (k < 4 ? seg(f, e - 10, e, 1, 0, Easing.in(Easing.cubic)) : 1);
            const ty = seg(f, s, s + 14, 14, 0, Easing.out(Easing.cubic));
            return (
              <div
                key={lv2.name}
                style={{
                  position: 'absolute',
                  left: 540,
                  top: LEVEL.labelCy + ty,
                  translate: '-50% -50%',
                  fontFamily: FONT,
                  fontSize: 30,
                  fontWeight: 600,
                  letterSpacing: 4,
                  color: C.ink,
                  whiteSpace: 'nowrap',
                  opacity: op,
                }}
              >
                {lv2.name} · L{k + 1}
              </div>
            );
          })}

          {/* numbered badges under the stops */}
          {LEVELS.map((_, i) => {
            const p = seg(f, 96 + i * 7, 112 + i * 7, 0, 1, Easing.out(Easing.cubic));
            if (p <= 0.005) return null;
            const isCur = i === lv && f >= ARRIVALS[i] - 2;
            const passed = i < lv + 1 && f >= ARRIVALS[i] - 2;
            const bg = isCur ? theme : passed ? rgba(theme, 0.15) : '#FFFFFF';
            const fg = isCur ? '#FFFFFF' : passed ? themeDeep : 'rgba(17,17,17,0.32)';
            const border = isCur ? 'none' : `1.5px solid ${passed ? rgba(theme, 0.5) : 'rgba(17,17,17,0.14)'}`;
            return (
              <div
                key={`b${i}`}
                style={{
                  position: 'absolute',
                  left: stopX(i) - 18,
                  top: LEVEL.badgesCy - 18,
                  width: 36,
                  height: 36,
                  borderRadius: 9,
                  backgroundColor: bg,
                  border,
                  boxShadow: isCur ? `0 6px 16px ${rgba(theme, 0.35)}` : undefined,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontFamily: FONT,
                  fontSize: 17,
                  fontWeight: 600,
                  color: fg,
                  scale: `${p}`,
                  opacity: Math.min(1, p * 1.4),
                }}
              >
                {i + 1}
              </div>
            );
          })}

          {/* terminal flavor at level 5 - monospace, blinking block cursor */}
          {neon > 0.005 ? (
            <div
              style={{
                position: 'absolute',
                left: 540,
                top: LEVEL.terminalCy,
                translate: '-50% -50%',
                fontFamily: MONO,
                fontSize: 20,
                color: C.inkSoft,
                whiteSpace: 'nowrap',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                opacity:
                  neon * seg(f, ARRIVALS[4] + 22, ARRIVALS[4] + 34, 0, 1, Easing.out(Easing.cubic)),
              }}
            >
              <span>{'> neural_glass --level 5'}</span>
              <span
                style={{
                  display: 'inline-block',
                  width: 11,
                  height: 22,
                  backgroundColor: AMBER,
                  opacity: f % 44 < 26 ? 1 : 0.15,
                }}
              />
            </div>
          ) : null}
        </div>
      </Stage>
    </AbsoluteFill>
  );
};
