import type {FC} from 'react';
import {GLASS, L, THINK, FONT, LEVELS} from '../constants';
import {clamp, seg, wobble, levelColor, mixHex, LEVEL_NAMES, hsl, mod} from '../util';

/** Glass-capsule geometry (morph source) -> thinking-track geometry. */
const lerp = (a: number, b: number, t: number) => a + (b - a) * t;

/** Five thumb stop centers along the thinking track (inset 62 each side). */
export const stopX = (k: number): number =>
  THINK.trackLeft + THINK.thumbInset + (THINK.travel * k) / 4;

const NOTCH_X = [1, 2, 3].map((i) => (stopX(i - 1) + stopX(i)) / 2);

export type ThinkingSliderProps = {
  frame: number;
  fps: number;
  /** 0..1 morph from the liquid-glass capsule into the thinking track */
  morph?: number;
  /** group scale (finale pulse) */
  scale?: number;
  /** overall opacity */
  groupOpacity?: number;
};

/**
 * The advanced five-level "thinking intensity" slider:
 *  glassy pill track + 4 notch dots + chunky pill thumb that snaps through
 *  OFF / LOW / MID / HIGH / ULTRA. The fill and all accents ramp gradually
 *  through the level palette into an animated rainbow at ULTRA.
 */
export const ThinkingSlider: FC<ThinkingSliderProps> = ({
  frame: f,
  fps,
  morph = 1,
  scale = 1,
  groupOpacity = 1,
}) => {
  const m = clamp(morph, 0, 1);

  // continuous level value 0..4 from the sweep beats (see constants.SWEEP)
  const v = thinkingValue(f);
  const vIdx = clamp(Math.round(v), 0, 4);
  // geometry: glass capsule -> thinking track
  const gL = lerp(GLASS.capsuleLeft, THINK.trackLeft, m);
  const gW = lerp(GLASS.capsuleW, THINK.trackW, m);
  const gH = lerp(GLASS.capsuleH, THINK.trackH, m);
  const gR = lerp(GLASS.capsuleR, THINK.trackR, m);

  // thumb: glass ball (circle d58) -> pill (92x48), returning to OFF
  const ballX0 = GLASS.capsuleLeft + GLASS.thumbInset + 0.6 * GLASS.travel;
  const tw = lerp(58, THINK.thumbW, m);
  const th = lerp(58, THINK.thumbH, m);
  const tR = lerp(29, THINK.thumbR, m);
  // continuous slide through the stops (no snapping jumps mid-sweep)
  const tx = lerp(ballX0, stopX(v), m);
  const ty = L.stageY;

  // fill edge hides under the thumb center (cap never visible), and the
  // fill only grows in once the sweep leaves OFF (no ghost stub at rest)
  const fillEdge = (tx - gL) * smooth01(clamp(v / 0.3, 0, 1));

  // rainbow hue offset once at ULTRA
  const ultraHue = v >= 3.6 ? mod((f - 250) * 2.4, 360) : 0;
  const color = levelColor(v, ultraHue);
  const isUltra = v >= 3.98;
  // during the morph the liquid still carries the glass blue, blending out
  const morphColor = mixHex('#2E7CF6', levelColor(Math.max(v, 0.001)), m);

  // jelly squash on each arrival
  const arrivals = [110, 180, 250, 316];
  let sx = 1;
  let sy = 1;
  for (const t of arrivals) {
    if (f > t) {
      const d = f - t;
      const w = Math.exp(-d / 8) * Math.cos(d * 0.9) * 0.12;
      sx += w;
      sy -= w * 0.8;
    }
  }

  // notch pulses when the thumb passes
  const notchPulse = (i: number): number => {
    const t = [86, 156, 226, 296][i] ?? 0;
    return f > t && f < t + 18 ? 1 + 0.5 * Math.exp(-(f - t) / 6) : 1;
  };

  const ticksOp = seg(f, 14, 40, 0, 1);
  const titleOp = seg(f, 8, 34, 0, 1);
  const notchesOp = seg(f, 16, 42, 0, 1);

  // ULTRA: rainbow gradient stops for the fill & thumb
  const rainbow = (id: string) => {
    const stops = [0, 1, 2, 3, 4].map((i) => ({
      off: `${(i * 25).toFixed(0)}%`,
      color: hsl(mod(ultraHue + i * 60, 360), 95, 60),
    }));
    return (
      <linearGradient id={id} x1="0" y1="0" x2="1" y2="0">
        {stops.map((s, i) => (
          <stop key={i} offset={s.off} stopColor={s.color} />
        ))}
      </linearGradient>
    );
  };

  const thumbFill = isUltra ? 'url(#think-rainbow-thumb)' : morphColor;
  const fillPaint = isUltra ? 'url(#think-rainbow-fill)' : morphColor;

  return (
    <div
      style={{
        position: 'absolute',
        left: 0,
        top: 0,
        width: 1080,
        height: 1920,
        scale: `${scale}`,
        transformOrigin: '540px 960px',
        opacity: groupOpacity,
      }}
    >
      <svg width={0} height={0} style={{position: 'absolute'}}>
        <defs>
          {rainbow('think-rainbow-fill')}
          {rainbow('think-rainbow-thumb')}
        </defs>
      </svg>

      {/* track: glassy pill container */}
      <div
        style={{
          position: 'absolute',
          left: gL,
          top: ty - gH / 2,
          width: gW,
          height: gH,
          borderRadius: gR,
          overflow: 'hidden',
          backgroundColor: 'rgba(255,255,255,0.55)',
          backdropFilter: 'blur(12px) saturate(150%)',
          border: '1.5px solid rgba(255,255,255,0.95)',
          boxShadow: '0 16px 40px rgba(31, 41, 55, 0.13)',
        }}
      >
        {/* colored fill (edge tucked under the thumb) */}
        <svg
          width={gW}
          height={gH}
          viewBox={`0 0 ${gW} ${gH}`}
          style={{position: 'absolute', left: 0, top: 0, display: 'block'}}
        >
          <rect
            x={0}
            y={gH * 0.18}
            width={Math.max(0, Math.min(fillEdge, gW))}
            height={gH * 0.64}
            rx={gH * 0.32}
            fill={fillPaint}
          />
        </svg>
        {/* top sheen */}
        <div
          style={{
            position: 'absolute',
            left: 12,
            top: 5,
            width: gW - 24,
            height: 10,
            borderRadius: 8,
            background: 'linear-gradient(180deg, rgba(255,255,255,0.6) 0%, rgba(255,255,255,0) 100%)',
            opacity: 0.9,
          }}
        />
      </div>

      {/* notch dots at level boundaries */}
      {NOTCH_X.map((x, i) => (
        <div
          key={i}
          style={{
            position: 'absolute',
            left: x - THINK.notchR,
            top: ty - THINK.notchR,
            width: THINK.notchR * 2,
            height: THINK.notchR * 2,
            borderRadius: '50%',
            backgroundColor: mixHex('#C4CBD8', color, 0.25 + 0.25 * clamp(v - i, 0, 1)),
            opacity: notchesOp,
            scale: `${notchPulse(i)}`,
          }}
        />
      ))}

      {/* pill thumb */}
      <div
        style={{
          position: 'absolute',
          left: tx - tw / 2,
          top: ty - th / 2,
          width: tw,
          height: th,
          borderRadius: tR,
          background: thumbFill,
          border: '2px solid #FFFFFF',
          boxShadow: isUltra
            ? `0 0 ${18 + 10 * Math.sin(f * 0.3)}px rgba(214, 51, 155, 0.45), 0 10px 26px rgba(31, 41, 55, 0.18)`
            : `0 8px 22px rgba(31, 41, 55, 0.20)`,
          scaleX: `${sx}`,
          scaleY: `${sy}`,
        }}
      />

      {/* titles */}
      <div
        style={{
          position: 'absolute',
          left: L.stageX,
          top: ty - 172,
          translate: '-50% -50%',
          fontFamily: FONT,
          fontSize: THINK.titleFont,
          letterSpacing: 10,
          fontWeight: 600,
          color: '#7A828E',
          opacity: titleOp,
          whiteSpace: 'nowrap',
        }}
      >
        THINKING
      </div>
      <div
        style={{
          position: 'absolute',
          left: L.stageX,
          top: ty - 112,
          translate: '-50% -50%',
          fontFamily: FONT,
          fontSize: THINK.levelFont,
          fontWeight: 800,
          color: isUltra ? hsl(mod(285 + ultraHue, 360), 92, 52) : color,
          opacity: titleOp,
          whiteSpace: 'nowrap',
          textShadow: isUltra
            ? `0 0 26px ${hsl(mod(285 + ultraHue, 360), 95, 62, 0.55)}`
            : 'none',
        }}
      >
        {LEVEL_NAMES[vIdx]}
      </div>

      {/* level tick labels */}
      {LEVEL_NAMES.map((name, k) => {
        const active = k === vIdx;
        return (
          <div
            key={name}
            style={{
              position: 'absolute',
              left: stopX(k),
              top: ty + THINK.trackH / 2 + 30,
              translate: '-50% -50%',
              fontFamily: FONT,
              fontSize: THINK.tickFont,
              fontWeight: active ? 800 : 500,
              letterSpacing: 2,
              color: active
                ? k === 4
                  ? hsl(mod(285 + ultraHue, 360), 92, 52)
                  : LEVELS[k < 4 ? k : 3].color
                : '#9AA0AC',
              opacity: ticksOp * (active ? 1 : 0.75),
              whiteSpace: 'nowrap',
            }}
          >
            {name}
          </div>
        );
      })}
    </div>
  );
};

/** Sweep value curve: continuous level 0..4 as a pure function of frame. */
const smooth01 = (t: number): number => t * t * (3 - 2 * t);

export const thinkingValue = (f: number): number => {
  const seg2 = (go: number, arr: number) =>
    seg(f, go, arr, 0, 1, (t: number) => t * t * (3 - 2 * t));
  // staged: 0 ->(70,110) 1 ->(140,180) 2 ->(210,250) 3 ->(280,316) 4
  if (f < 70) return 0;
  if (f < 118) return seg2(70, 110);
  if (f < 148) return 1;
  if (f < 188) return 1 + seg2(140, 180);
  if (f < 218) return 2;
  if (f < 258) return 2 + seg2(210, 250);
  if (f < 288) return 3;
  if (f < 324) return 3 + seg2(280, 316);
  return 4;
};
