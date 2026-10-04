import type {FC} from 'react';
import {Easing} from 'remotion';
import {C, GLASS, L} from '../constants';
import {clamp, seg, velocity} from '../util';
import {CapsuleContent} from './CapsuleContent';
import {GlassThumb} from './GlassThumb';
import {ParamLabel} from './ParamLabel';

/**
 * Liquid glass slider value curve, as a pure function of the animation phase.
 * Phase is scene-3 local frames; scene 4 continues at phase 320+.
 * 70 (inherits scene 2 cascade) -> 100 -> 0 -> 50 (final rest).
 */
export const glassValue = (p: number): number => {
  if (p < 80) {
    return 70;
  }
  if (p < 140) {
    return seg(p, 80, 138, 70, 100, Easing.bezier(0.5, 0.05, 0.25, 1));
  }
  if (p < 200) {
    return seg(p, 140, 198, 100, 0, Easing.bezier(0.5, 0.05, 0.25, 1));
  }
  if (p < 280) {
    return seg(p, 200, 276, 0, 50, Easing.bezier(0.35, 0, 0.25, 1));
  }
  return 50;
};

const ARRIVALS = [138, 198, 276];

export type GlassSliderProps = {
  /** continuous animation phase (scene-3 local, or 320+local in scene 4) */
  phase: number;
  /** 0..1 materialization (scene 3 melt ramp), defaults to 1 */
  materialization?: number;
  /** group scale (entrance / finale pulse) */
  scale?: number;
  /** label opacity ramp */
  labelsOpacity?: number;
  /** overall opacity */
  groupOpacity?: number;
};

/** Hidden SVG filter defs: channel isolation + liquid warp. */
const FilterDefs: FC = () => (
  <svg width={0} height={0} style={{position: 'absolute', left: 0, top: 0}}>
    <defs>
      <filter id="ui-chroma-r" x="-20%" y="-20%" width="140%" height="140%">
        <feColorMatrix
          type="matrix"
          values="1 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 1 0"
        />
      </filter>
      <filter id="ui-chroma-b" x="-20%" y="-20%" width="140%" height="140%">
        <feColorMatrix
          type="matrix"
          values="0 0 0 0 0  0 0 0 0 0  0 0 1 0 0  0 0 0 1 0"
        />
      </filter>
      <filter id="ui-warp" x="-20%" y="-20%" width="140%" height="140%">
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

/**
 * The complete liquid glass slider: translucent capsule (backdrop blur) with
 * a rippling liquid fill, a refracting glass ball thumb with viscous jelly
 * physics, and the two standard parameter labels.
 */
export const GlassSlider: FC<GlassSliderProps> = ({
  phase,
  materialization = 1,
  scale = 1,
  labelsOpacity = 1,
  groupOpacity = 1,
}) => {
  const value = glassValue(phase);
  const vel = Math.abs(velocity(glassValue, phase, 2));
  const clamped = clamp(value, 0, 100);
  const thumbX = GLASS.capsuleLeft + GLASS.thumbInset + (clamped / 100) * GLASS.travel;
  const fillEdge = thumbX - GLASS.capsuleLeft - 34;
  const mat = clamp(materialization, 0, 1);

  // liquid waves react to velocity, calm when at rest
  const amp = (1.5 + Math.min(vel * 3.2, 7)) * mat;
  const wavePhase = phase * 0.55;

  // jelly physics: velocity squash & stretch + arrival ringdown
  let sx = 1;
  let sy = 1;
  const stretch = Math.min(vel * 0.012, 0.11);
  sx += stretch;
  sy -= stretch * 0.7;
  for (const t of ARRIVALS) {
    if (phase > t) {
      const d = phase - t;
      const w = Math.exp(-d / 9) * Math.cos(d * 0.85) * 0.15;
      sx += w;
      sy -= w * 0.8;
    }
  }

  // breathing: one soft cycle every 60 frames
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
          backgroundColor: 'rgba(255,255,255,0.045)',
          backdropFilter: 'blur(6px) saturate(130%) brightness(1.1)',
          border: '1px solid rgba(255,255,255,0.14)',
        }}
      >
        <CapsuleContent fillEdge={fillEdge} amp={amp} wavePhase={wavePhase} />
      </div>
      {/* liquid glass ball */}
      <GlassThumb
        x={thumbX}
        y={L.stageY}
        r={GLASS.thumbR}
        fillEdge={fillEdge}
        amp={amp}
        wavePhase={wavePhase}
        glassiness={mat}
        scaleX={sx * breath}
        scaleY={sy * breath}
      />
      {/* standard parameter labels */}
      <ParamLabel
        x={L.stageX}
        y={L.stageY - 84}
        text="LIQUID GLASS"
        opacity={labelsOpacity}
        color={C.labelDim}
        size={GLASS.badgeFont}
        letterSpacing={GLASS.badgeGap}
      />
      <ParamLabel
        x={L.stageX}
        y={L.stageY + 72}
        text="Blur Intensity"
        value={`${Math.round(clamped)}%`}
        opacity={labelsOpacity}
        color={C.label}
        size={GLASS.valueFont}
      />
    </div>
  );
};
