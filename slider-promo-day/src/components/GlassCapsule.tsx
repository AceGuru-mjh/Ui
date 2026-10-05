import type {FC, ReactNode} from 'react';
import {useId} from 'react';
import {C} from '../constants';

export type CapsuleGeometry = {
  left: number;
  top: number;
  w: number;
  h: number;
  radius: number;
};

export type GlassCapsuleProps = {
  geo: CapsuleGeometry;
  /** 0..1 materialization */
  mat: number;
  /** capsule-local x of the liquid leading edge */
  fillEdge: number;
  /** wave amplitude px (0 = flat edge) */
  amp: number;
  wavePhase: number;
  /** gradient colors of the liquid fill */
  fillTop: string;
  fillBottom: string;
  fillOpacity?: number;
  /** gaussian blur applied to the liquid fill ("Blur" beat) */
  innerBlur?: number;
  /** edge/border strength multiplier */
  edge?: number;
  /** backdrop brightness base (BRIGHTNESS beat lifts it) */
  glassBright?: number;
  /** children rendered inside the capsule (clipped), e.g. LED matrix */
  children?: ReactNode;
};

const WAVE_POINTS = 15;

const buildWavePath = (
  edge: number,
  amp: number,
  phase: number,
  h: number,
): string => {
  const parts: string[] = [];
  for (let i = 0; i <= WAVE_POINTS; i += 1) {
    const y = (i / WAVE_POINTS) * h;
    const x = edge + amp * Math.sin((2 * Math.PI * y) / 30 + phase);
    parts.push(`${x.toFixed(1)} ${y.toFixed(1)}`);
  }
  return `M 0 0 L ${parts.join(' L ')} L 0 ${h} Z`;
};

/**
 * Light-mode liquid glass capsule: translucent white glass over a pure white
 * void. Depth comes from hairline borders, soft shadows, the tinted liquid
 * fill inside and a specular top sheen. Pure function of the frame.
 */
export const GlassCapsule: FC<GlassCapsuleProps> = ({
  geo,
  mat,
  fillEdge,
  amp,
  wavePhase,
  fillTop,
  fillBottom,
  fillOpacity = 0.92,
  innerBlur = 0,
  edge = 1,
  glassBright = 1.03,
  children,
}) => {
  const uid = useId().replace(/[^a-zA-Z0-9]/g, '');
  const d = buildWavePath(fillEdge, amp, wavePhase, geo.h);
  const dEcho = buildWavePath(fillEdge - 14, amp * 0.5, wavePhase + Math.PI, geo.h);
  const filterDef =
    innerBlur > 0.05 ? (
      <filter id={uid} x="-20%" y="-20%" width="140%" height="140%">
        <feGaussianBlur stdDeviation={innerBlur} />
      </filter>
    ) : null;

  return (
    <div
      style={{
        position: 'absolute',
        left: geo.left,
        top: geo.top,
        width: geo.w,
        height: geo.h,
        borderRadius: geo.radius,
        opacity: mat,
        overflow: 'visible',
      }}
    >
      {/* contact shadow - the key depth cue on white */}
      <div
        style={{
          position: 'absolute',
          left: 6,
          top: geo.h - 2,
          width: geo.w - 12,
          height: 22,
          borderRadius: geo.radius,
          filter: 'blur(14px)',
          backgroundColor: C.shadowFar,
          opacity: 0.9 * mat,
        }}
      />
      {/* glass body */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: geo.radius,
          overflow: 'hidden',
          backgroundColor: `rgba(255,255,255,${(0.55 * mat).toFixed(3)})`,
          backdropFilter: `blur(6px) saturate(130%) brightness(${glassBright.toFixed(3)})`,
          border: `1px solid rgba(17,17,17,${(0.12 * edge).toFixed(3)})`,
          boxShadow: `0 18px 40px rgba(17,17,17,${(0.10 * mat).toFixed(3)}), 0 2px 8px rgba(17,17,17,${(0.05 * mat).toFixed(3)})`,
        }}
      >
        {/* liquid fill */}
        <svg
          width={geo.w}
          height={geo.h}
          viewBox={`0 0 ${geo.w} ${geo.h}`}
          style={{position: 'absolute', left: 0, top: 0, display: 'block'}}
        >
          <defs>
            <linearGradient id={`${uid}-grad`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" stopColor={fillTop} />
              <stop offset="1" stopColor={fillBottom} />
            </linearGradient>
            {filterDef}
          </defs>
          <g filter={innerBlur > 0.05 ? `url(#${uid})` : undefined}>
            <path d={dEcho} fill={fillBottom} fillOpacity={fillOpacity * 0.28} />
            <path
              d={d}
              fill={`url(#${uid}-grad)`}
              fillOpacity={fillOpacity}
            />
          </g>
        </svg>
        {/* meniscus glow at the leading edge */}
        <div
          style={{
            position: 'absolute',
            left: fillEdge - 19,
            top: 0,
            width: 38,
            height: geo.h,
            opacity: 0.8,
            background: `radial-gradient(ellipse 19px 30px at center, ${fillBottom}55 0%, rgba(255,255,255,0) 75%)`,
          }}
        />
        {/* top sheen */}
        <div
          style={{
            position: 'absolute',
            left: 12,
            top: 4,
            width: geo.w - 24,
            height: 2.5,
            borderRadius: 2,
            backgroundColor: 'rgba(255,255,255,0.85)',
            opacity: 0.8 * mat,
          }}
        />
        {/* bottom inner shade */}
        <div
          style={{
            position: 'absolute',
            left: 12,
            bottom: 3,
            width: geo.w - 24,
            height: 2,
            borderRadius: 2,
            backgroundColor: 'rgba(17,17,17,0.05)',
          }}
        />
        {children}
      </div>
      {/* specular top-left highlight arc (outside the clip, above border) */}
      <div
        style={{
          position: 'absolute',
          left: 10,
          top: -1,
          width: geo.w * 0.42,
          height: 10,
          borderRadius: 6,
          filter: 'blur(2.5px)',
          backgroundColor: 'rgba(255,255,255,0.9)',
          opacity: 0.85 * mat,
        }}
      />
    </div>
  );
};
