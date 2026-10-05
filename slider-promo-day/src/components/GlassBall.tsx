import type {FC, ReactNode} from 'react';
import type {CapsuleGeometry} from './GlassCapsule';
import {C} from '../constants';
import {rgba} from '../util';

/** Magnification of the refraction copy inside the glass ball. */
const MAG = 1.18;

export type GlassBallProps = {
  /** thumb center x (stage coords) */
  x: number;
  /** thumb center y (stage coords) */
  y: number;
  r: number;
  geo: CapsuleGeometry;
  /** capsule-local liquid edge, for the refraction copy */
  fillEdge: number;
  amp: number;
  wavePhase: number;
  fillTop: string;
  fillBottom: string;
  /** 0 = flat white block, 1 = fully liquid glass */
  glassiness: number;
  scaleX: number;
  scaleY: number;
  /** current level theme color (tints refraction & rim) */
  tint: string;
};

/**
 * Light-mode "liquid glass ball" thumb:
 *  - edge refraction: a magnified, warped copy of the capsule content
 *  - chromatic aberration: R/B channel-split copies
 *  - internal highlight arc + counter-light + specular dot
 *  - backdrop blur + hairline rim + soft shadow so it reads on pure white
 */
export const GlassBall: FC<GlassBallProps> = ({
  x,
  y,
  r,
  geo,
  fillEdge,
  amp,
  wavePhase,
  fillTop,
  fillBottom,
  glassiness,
  scaleX,
  scaleY,
  tint,
}) => {
  // place the magnified capsule copy so the point under the thumb center
  // lands exactly at the thumb's center
  const left = r - MAG * (x - geo.left);
  const top = r - MAG * (geo.h / 2);

  const refractionLayer = (
    dx: number,
    filter: string,
    opacity: number,
    blend?: 'screen',
  ): ReactNode => (
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
        mixBlendMode: blend,
      }}
    >
      <div
        style={{
          position: 'absolute',
          left: left + dx,
          top,
          width: geo.w,
          height: geo.h,
          scale: `${MAG}`,
          transformOrigin: '0px 0px',
          filter,
        }}
      >
        <svg width={geo.w} height={geo.h} style={{display: 'block'}}>
          <rect
            x={Math.max(0, fillEdge - 60)}
            y={0}
            width={Math.max(0, geo.w - Math.max(0, fillEdge - 60))}
            height={geo.h}
            fill={fillBottom}
            opacity={0.9}
          />
          <rect
            x={Math.max(0, fillEdge - 8)}
            y={0}
            width={Math.max(0, geo.w - Math.max(0, fillEdge - 8))}
            height={geo.h}
            fill={fillTop}
            opacity={0.55}
          />
        </svg>
      </div>
    </div>
  );

  const g = Math.max(0, Math.min(1, glassiness));

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
      {/* contact shadow */}
      <div
        style={{
          position: 'absolute',
          left: '10%',
          top: r * 2 - 10,
          width: '80%',
          height: 14,
          borderRadius: '50%',
          filter: 'blur(7px)',
          backgroundColor: C.shadowFar,
          opacity: 0.9 * g,
        }}
      />
      {/* glass base with backdrop blur */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: '50%',
          backgroundColor: 'rgba(255,255,255,0.50)',
          backdropFilter: 'blur(3px) saturate(140%) brightness(1.06)',
          border: `1px solid ${rgba(tint, 0.35 * g + 0.10)}`,
          boxShadow: `0 10px 24px ${C.shadowNear}, inset 0 -6px 12px ${rgba(tint, 0.10 * g)}`,
          opacity: g,
        }}
      />
      {/* edge refraction (magnified capsule content) */}
      {refractionLayer(0, 'url(#ui-warp-day)', 0.85 * g)}
      {/* chromatic dispersion: thin red/blue fringe rings at the rim */}
      <div
        style={{
          position: 'absolute',
          inset: -1,
          borderRadius: '50%',
          border: '2px solid rgba(255,70,70,0.14)',
          translate: '-2px 0',
          opacity: g,
        }}
      />
      <div
        style={{
          position: 'absolute',
          inset: -1,
          borderRadius: '50%',
          border: '2px solid rgba(70,90,255,0.13)',
          translate: '2px 0',
          opacity: g,
        }}
      />
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
            'radial-gradient(circle at 34% 30%, rgba(255,255,255,0.95) 0%, rgba(255,255,255,0) 68%)',
          filter: 'blur(2px)',
          opacity: 0.95 * g,
        }}
      />
      {/* bottom-right counter-light, tinted */}
      <div
        style={{
          position: 'absolute',
          right: '10%',
          bottom: '8%',
          width: '46%',
          height: '40%',
          borderRadius: '50%',
          background: `radial-gradient(circle at 70% 75%, ${rgba(tint, 0.20)} 0%, rgba(255,255,255,0) 70%)`,
          filter: 'blur(3px)',
          opacity: 0.75 * g,
        }}
      />
      {/* specular dot */}
      <div
        style={{
          position: 'absolute',
          left: '24%',
          top: '18%',
          width: 6,
          height: 6,
          borderRadius: '50%',
          backgroundColor: 'rgba(255,255,255,0.98)',
          filter: 'blur(1px)',
          opacity: g,
        }}
      />
    </div>
  );
};
