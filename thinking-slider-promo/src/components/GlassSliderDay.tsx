import type {FC} from 'react';
import {GLASS, L, FONT} from '../constants';
import {CapsuleFill} from './CapsuleFill';
import {ParamLabel} from './ParamLabel';
import {clamp, seg} from '../util';

/** Magnification of the refraction copy inside the glass ball. */
const MAG = 1.18;

/** Hidden SVG filter defs: channel isolation + liquid warp (repo-proven). */
const FilterDefs: FC = () => (
  <svg width={0} height={0} style={{position: 'absolute', left: 0, top: 0}}>
    <defs>
      <filter id="day-chroma-r" x="-20%" y="-20%" width="140%" height="140%">
        <feColorMatrix
          type="matrix"
          values="1 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 1 0"
        />
      </filter>
      <filter id="day-chroma-b" x="-20%" y="-20%" width="140%" height="140%">
        <feColorMatrix
          type="matrix"
          values="0 0 0 0 0  0 0 0 0 0  0 0 0 1 0  0 0 0 1 0"
        />
      </filter>
      <filter id="day-warp" x="-20%" y="-20%" width="140%" height="140%">
        <feTurbulence
          type="fractalNoise"
          baseFrequency="0.012 0.05"
          numOctaves={2}
          seed={7}
          result="noise"
        />
        <feDisplacementMap
          in="SourceGraphic"
          in2="noise"
          scale={6}
          xChannelSelector="R"
          yChannelSelector="G"
        />
      </filter>
    </defs>
  </svg>
);

export type GlassSliderDayProps = {
  /** 0..100 */
  value: number;
  /** |d value/d phase| in %/frame, drives liquid agitation */
  vel?: number;
  /** continuous animation phase (local frames) for waves/jelly */
  phase: number;
  /** 0..1 materialization (melt ramp) */
  materialization?: number;
  /** fill hue while glass (before the thinking levels take over) */
  color?: string;
  /** group scale */
  scale?: number;
  /** label opacity ramp */
  labelsOpacity?: number;
  /** overall opacity */
  groupOpacity?: number;
  /** arrival frame list for jelly ringdown */
  arrivals?: number[];
};

/** The refraction ball thumb (light-bg edition). */
const GlassBall: FC<{
  x: number;
  y: number;
  r: number;
  fillEdge: number;
  amp: number;
  wavePhase: number;
  glassiness: number;
  color: string;
  scaleX: number;
  scaleY: number;
  gradId: string;
}> = ({x, y, r, fillEdge, amp, wavePhase, glassiness, color, scaleX, scaleY, gradId}) => {
  const left = r - MAG * (x - GLASS.capsuleLeft);
  const top = r - MAG * (GLASS.capsuleH / 2);
  const g = clamp(glassiness, 0, 1);

  const refractionLayer = (
    dx: number,
    filter: string,
    opacity: number,
  ) => (
    <div
      style={{
        position: 'absolute',
        left: 0,
        top: 0,
        width: r * 2,
        height: r * 2,
        borderRadius: '50%',
        overflow: 'hidden',
        opacity,
      }}
    >
      <div
        style={{
          position: 'absolute',
          left: left + dx,
          top,
          width: GLASS.capsuleW,
          height: GLASS.capsuleH,
          scale: `${MAG}`,
          transformOrigin: '0px 0px',
          filter,
        }}
      >
        <CapsuleFill
          fillEdge={fillEdge}
          amp={amp}
          wavePhase={wavePhase}
          color={color}
          gradId={gradId}
          fillOpacity={0.9}
          glowOpacity={0.85}
        />
      </div>
    </div>
  );

  return (
    <div
      style={{
        position: 'absolute',
        left: x - r,
        top: y - r,
        width: r * 2,
        height: r * 2,
        scale: `${scaleX} ${scaleY}`,
      }}
    >
      {/* flat fallback (pre-melt): plain primary circle */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: '50%',
          backgroundColor: '#6750A4',
          opacity: 1 - g,
        }}
      />
      {/* translucent glass base with backdrop blur */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: '50%',
          backgroundColor: 'rgba(255,255,255,0.30)',
          backdropFilter: 'blur(4px) saturate(160%) brightness(1.06)',
          border: '1.5px solid rgba(255,255,255,0.95)',
          boxShadow: `0 10px 24px rgba(31, 41, 55, ${0.18 * g})`,
          opacity: g,
        }}
      />
      {/* edge refraction (magnified, warped) */}
      {refractionLayer(0, 'url(#day-warp)', 0.9 * g)}
      {/* chromatic aberration fringes */}
      {refractionLayer(-1.6, 'url(#day-warp) url(#day-chroma-r)', 0.28 * g)}
      {refractionLayer(1.6, 'url(#day-warp) url(#day-chroma-b)', 0.28 * g)}
      {/* top-left highlight arc */}
      <div
        style={{
          position: 'absolute',
          left: '6%',
          top: '4%',
          width: '64%',
          height: '58%',
          borderRadius: '50%',
          background:
            'radial-gradient(circle at 34% 30%, rgba(255,255,255,0.9) 0%, rgba(255,255,255,0.0) 68%)',
          filter: 'blur(2px)',
          opacity: 0.95 * g,
        }}
      />
      {/* bottom-right counter-light */}
      <div
        style={{
          position: 'absolute',
          right: '10%',
          bottom: '8%',
          width: '46%',
          height: '40%',
          borderRadius: '50%',
          background:
            'radial-gradient(circle at 70% 75%, rgba(255,255,255,0.35) 0%, rgba(255,255,255,0.0) 70%)',
          filter: 'blur(3px)',
          opacity: 0.7 * g,
        }}
      />
      {/* specular dot */}
      <div
        style={{
          position: 'absolute',
          left: '24%',
          top: '20%',
          width: 5,
          height: 5,
          borderRadius: '50%',
          backgroundColor: 'rgba(255,255,255,0.98)',
          filter: 'blur(1px)',
          opacity: g,
        }}
      />
    </div>
  );
};

/**
 * The day-mode liquid glass slider: translucent capsule (backdrop blur on
 * soft ambient light), colored liquid fill with rippling edge, refracting
 * glass ball with chromatic dispersion, and two standard parameter labels.
 */
export const GlassSliderDay: FC<GlassSliderDayProps> = ({
  value,
  vel = 0,
  phase,
  materialization = 1,
  color = '#2E7CF6',
  scale = 1,
  labelsOpacity = 1,
  groupOpacity = 1,
  arrivals = [],
}) => {
  const mat = clamp(materialization, 0, 1);
  const clamped = clamp(value, 0, 100);
  const thumbX = GLASS.capsuleLeft + GLASS.thumbInset + (clamped / 100) * GLASS.travel;
  const fillEdge = thumbX - GLASS.capsuleLeft - 36;

  // liquid waves react to travel speed, calm when at rest
  const amp = (1.5 + Math.min(vel * 2.4, 7)) * mat;
  const wavePhase = phase * 0.55;

  // jelly: squash & stretch toward direction of travel + arrival ringdown
  let sx = 1;
  let sy = 1;
  for (const t of arrivals) {
    if (phase > t) {
      const d = phase - t;
      const w = Math.exp(-d / 9) * Math.cos(d * 0.85) * 0.14;
      sx += w;
      sy -= w * 0.8;
    }
  }
  const breath = 1 + 0.012 * Math.sin((2 * Math.PI * phase) / 60);

  return (
    <div
      style={{
        position: 'absolute',
        left: 0,
        top: 0,
        width: 1080,
        height: 1920,
        scale: `${scale}`,
        transformOrigin: '540px 900px',
        opacity: groupOpacity,
      }}
    >
      <FilterDefs />
      {/* translucent capsule track */}
      <div
        style={{
          position: 'absolute',
          left: GLASS.capsuleLeft,
          top: L.stageY - GLASS.capsuleH / 2,
          width: GLASS.capsuleW,
          height: GLASS.capsuleH,
          borderRadius: GLASS.capsuleR,
          overflow: 'hidden',
          opacity: mat,
          backgroundColor: 'rgba(255,255,255,0.28)',
          backdropFilter: 'blur(10px) saturate(150%) brightness(1.05)',
          border: '1.5px solid rgba(255,255,255,0.85)',
          boxShadow: `0 14px 34px rgba(31, 41, 55, ${0.16 * mat})`,
        }}
      >
        <CapsuleFill
          fillEdge={fillEdge}
          amp={amp}
          wavePhase={wavePhase}
          color={color}
          gradId="day-fill-main"
          fillOpacity={0.55}
        />
      </div>
      {/* top sheen across the whole capsule */}
      <div
        style={{
          position: 'absolute',
          left: GLASS.capsuleLeft + 14,
          top: L.stageY - GLASS.capsuleH / 2 + 6,
          width: GLASS.capsuleW - 28,
          height: 12,
          borderRadius: 8,
          background:
            'linear-gradient(180deg, rgba(255,255,255,0.55) 0%, rgba(255,255,255,0) 100%)',
          opacity: 0.9 * mat,
        }}
      />
      {/* liquid glass ball */}
      <GlassBall
        x={thumbX}
        y={L.stageY}
        r={GLASS.thumbR}
        fillEdge={fillEdge}
        amp={amp}
        wavePhase={wavePhase}
        glassiness={mat}
        color={color}
        scaleX={sx * breath}
        scaleY={sy * breath}
        gradId="day-fill-thumb"
      />
      {/* standard parameter labels */}
      <ParamLabel
        x={L.stageX}
        y={L.stageY - 96}
        text="LIQUID GLASS"
        opacity={labelsOpacity}
        color="#7A828E"
        size={GLASS.badgeFont}
        letterSpacing={GLASS.badgeGap}
        weight={600}
      />
      <div
        style={{
          position: 'absolute',
          left: L.stageX,
          top: L.stageY + 82,
          translate: '-50% -50%',
          fontFamily: FONT,
          fontSize: GLASS.valueFont,
          fontWeight: 500,
          color: '#49454F',
          opacity: labelsOpacity,
          whiteSpace: 'nowrap',
        }}
      >
        {`Refraction · ${Math.round(seg(clamped, 0, 100, 0, 100))}%`}
      </div>
    </div>
  );
};
