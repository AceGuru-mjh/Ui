import type {FC} from 'react';
import {AbsoluteFill, Easing, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, FONT, GLASS, L} from '../constants';
import {BlockThumb} from '../components/BlockThumb';
import {BlocksTrack} from '../components/BlocksTrack';
import {FilterDefsDay} from '../components/FilterDefsDay';
import {GlassBall} from '../components/GlassBall';
import {GlassCapsule} from '../components/GlassCapsule';
import {Stage} from '../components/Stage';
import {clamp, mixHex, seg, velocity, wobble} from '../util';

/**
 * Scene 2 (local frames 0-329 @60fps): the block-built slider melts into a
 * liquid glass capsule.
 *  0-42    flat assembly lingers, then dissolves
 *  22-88   the glass capsule fuses out of the brick line (thin line -> pill)
 *  64-100  blocky thumb hands over to the liquid glass ball
 *  96-116  "LIQUID GLASS" settles
 *  116-170 BRIGHTNESS beat: value 50 -> 100, the fill brightens
 *  170-230 BLUR beat: value 100 -> 0, the liquid inside blurs
 *  230-330 LIQUID GLASS beat: viscous slide back to 50 with jelly arrivals
 */
export const scene2Value = (f: number): number => {
  if (f < 116) {
    return 50;
  }
  if (f < 168) {
    return seg(f, 116, 168, 50, 100, Easing.bezier(0.5, 0.05, 0.25, 1));
  }
  if (f < 228) {
    return seg(f, 168, 228, 100, 0, Easing.bezier(0.5, 0.05, 0.25, 1));
  }
  if (f < 304) {
    return seg(f, 228, 304, 0, 50, Easing.bezier(0.35, 0, 0.25, 1));
  }
  return 50;
};

const ARRIVALS = [168, 228, 304];

type Beat = {s: number; e: number; text: string};

const BEATS: Beat[] = [
  {s: 96, e: 116, text: 'LIQUID GLASS'},
  {s: 116, e: 170, text: 'BRIGHTNESS'},
  {s: 170, e: 230, text: 'BLUR'},
  {s: 230, e: 330, text: 'LIQUID GLASS'},
];

export const Scene2: FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();

  const value = scene2Value(f);
  const v = clamp(value, 0, 100);
  const vel = Math.abs(velocity(scene2Value, f, 2));

  // materialization: the brick line fuses into a capsule
  const mat = seg(f, 22, 88, 0, 1, Easing.inOut(Easing.cubic));
  const fuse =
    f < 22
      ? 0.16
      : spring({frame: f - 22, fps, from: 0.16, to: 1, config: {damping: 14, stiffness: 130, mass: 1}});
  const geoW = seg(f, 22, 76, L.trackW, GLASS.capsuleW, Easing.out(Easing.cubic));
  const geoLeft = 540 - geoW / 2;
  const geo = {
    left: geoLeft,
    top: L.stageY - GLASS.capsuleH / 2,
    w: geoW,
    h: GLASS.capsuleH,
    radius: GLASS.capsuleR,
  };

  // flat assembly handover
  const flatOp = seg(f, 8, 42, 1, 0, Easing.inOut(Easing.cubic));
  const flatThumbX = L.trackLeft + 0.5 * L.trackW;
  const blockOp = seg(f, 64, 92, 1, 0, Easing.out(Easing.cubic));

  // glass ball
  const glassiness = seg(f, 66, 100, 0, 1, Easing.inOut(Easing.cubic));
  const thumbX = GLASS.capsuleLeft + GLASS.thumbInset + (v / 100) * GLASS.travel;
  const fillEdge = thumbX - GLASS.capsuleLeft - 32;

  // liquid physics
  const amp = 1.2 + Math.min(vel * 2.6, 6);
  const wavePhase = f * 0.5;
  let sx = 1;
  let sy = 1;
  const stretch = Math.min(vel * 0.011, 0.10);
  sx += stretch;
  sy -= stretch * 0.7;
  for (const t of ARRIVALS) {
    if (f > t) {
      const d = f - t;
      const w = Math.exp(-d / 9) * Math.cos(d * 0.85) * 0.14;
      sx += w;
      sy -= w * 0.8;
    }
  }
  const breath = 1 + 0.011 * Math.sin((2 * Math.PI * f) / 72);

  // BRIGHTNESS beat: the fill warms up toward white-gold
  const bright =
    seg(f, 116, 162, 0, 1, Easing.inOut(Easing.cubic)) *
    seg(f, 228, 272, 1, 0.35, Easing.inOut(Easing.cubic));
  const fillTop = mixHex('#F3F0EA', '#FFFFFF', 0.55 * bright);
  const fillBottom = mixHex('#E0DBD2', '#FFF3DC', 0.6 * bright);

  // BLUR beat: the liquid inside goes out of focus, then settles soft
  const innerBlur =
    seg(f, 174, 202, 0.8, 5, Easing.inOut(Easing.cubic)) *
    seg(f, 218, 238, 1, 0.16, Easing.inOut(Easing.cubic));

  const flatLabelOp = seg(f, 4, 18, 1, 0, Easing.linear);

  return (
    <AbsoluteFill style={{backgroundColor: C.bg}}>
      <FilterDefsDay />
      <Stage>
        {/* melting flat assembly */}
        {flatOp > 0.005 ? (
          <div style={{position: 'absolute', inset: 0, opacity: flatOp}}>
            <BlocksTrack y={L.stageY} f={0} fps={fps} thumbX={flatThumbX} moving={0} assembled />
            <BlockThumb x={flatThumbX} y={L.stageY} p={1} drop={0} sx={1} sy={1} opacity={blockOp} />
            <div
              style={{
                position: 'absolute',
                left: flatThumbX,
                top: L.stageY + 66,
                translate: '-50% -50%',
                fontFamily: FONT,
                fontSize: 26,
                color: C.label,
                whiteSpace: 'nowrap',
                opacity: flatLabelOp,
              }}
            >
              Opacity · 50%
            </div>
          </div>
        ) : null}

        {/* liquid glass capsule */}
        <div
          style={{
            position: 'absolute',
            inset: 0,
            scale: `1 ${fuse}`,
            transformOrigin: `540px ${L.stageY}px`,
          }}
        >
          <GlassCapsule
            geo={geo}
            mat={mat}
            fillEdge={fillEdge}
            amp={amp}
            wavePhase={wavePhase}
            fillTop={fillTop}
            fillBottom={fillBottom}
            innerBlur={innerBlur}
            glassBright={1.03 + 0.1 * bright}
          />
        </div>

        {/* liquid glass ball */}
        <GlassBall
          x={thumbX}
          y={L.stageY}
          r={GLASS.thumbR}
          geo={geo}
          fillEdge={fillEdge}
          amp={amp}
          wavePhase={wavePhase}
          fillTop={fillTop}
          fillBottom={fillBottom}
          glassiness={glassiness}
          scaleX={sx * breath}
          scaleY={sy * breath}
          tint="#C9A96A"
        />

        {/* beat label with micro swap transitions (single flex div, centered) */}
        {BEATS.map((b, i) => {
          const last = i === BEATS.length - 1;
          const op =
            seg(f, b.s, b.s + 10, 0, 1, Easing.out(Easing.cubic)) *
            (last ? 1 : seg(f, b.e - 10, b.e, 1, 0, Easing.in(Easing.cubic)));
          const ty = seg(f, b.s, b.s + 12, 12, 0, Easing.out(Easing.cubic));
          if (op <= 0.005) return null;
          return (
            <div
              key={i}
              style={{
                position: 'absolute',
                left: 540,
                top: L.stageY - 90 + ty,
                translate: '-50% -50%',
                fontFamily: FONT,
                fontSize: 24,
                fontWeight: 500,
                letterSpacing: 3,
                color: C.label,
                whiteSpace: 'nowrap',
                opacity: op,
              }}
            >
              {b.text} · {Math.round(v)}%
            </div>
          );
        })}
      </Stage>
    </AbsoluteFill>
  );
};
