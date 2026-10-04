import type {FC, ReactNode} from 'react';
import {C, GLASS} from '../constants';
import {CapsuleContent} from './CapsuleContent';

/** Magnification of the refraction copy inside the glass ball. */
const MAG = 1.18;

export type GlassThumbProps = {
  /** thumb center x (stage coords) */
  x: number;
  /** thumb center y (stage coords) */
  y: number;
  r: number;
  /** capsule-local liquid edge, for the refraction copy */
  fillEdge: number;
  amp: number;
  wavePhase: number;
  /** 0 = flat white circle, 1 = fully liquid glass */
  glassiness: number;
  /** combined jelly squash & breathing scale */
  scaleX: number;
  scaleY: number;
};

/**
 * The "liquid glass ball" thumb:
 *  - edge refraction: a magnified, warped copy of the capsule content behind it
 *  - chromatic aberration: R/B channel-split copies offset in opposite directions
 *  - internal highlight arc (top-left) + counter-light + specular dot
 *  - backdrop blur for real translucency
 * All layers are pure functions of the frame - no CSS transitions.
 */
export const GlassThumb: FC<GlassThumbProps> = ({
  x,
  y,
  r,
  fillEdge,
  amp,
  wavePhase,
  glassiness,
  scaleX,
  scaleY,
}) => {
  // Position the magnified capsule copy so that the world point beneath the
  // thumb center lands exactly at the thumb's center.
  const left = r - MAG * (x - GLASS.capsuleLeft);
  const top = r - MAG * (GLASS.capsuleH / 2);

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
          width: GLASS.capsuleW,
          height: GLASS.capsuleH,
          scale: `${MAG}`,
          transformOrigin: '0px 0px',
          filter,
        }}
      >
        <CapsuleContent
          fillEdge={fillEdge}
          amp={amp}
          wavePhase={wavePhase}
          fillOpacity={0.9}
          glowOpacity={0.85}
        />
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
      {/* flat fallback (pre-melt) */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: '50%',
          backgroundColor: C.thumb,
          opacity: 1 - g,
        }}
      />
      {/* translucent glass base with backdrop blur */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: '50%',
          backgroundColor: 'rgba(255,255,255,0.07)',
          backdropFilter: 'blur(3px) saturate(150%) brightness(1.15)',
          border: '1px solid rgba(255,255,255,0.22)',
          opacity: g,
        }}
      />
      {/* edge refraction (magnified, warped) */}
      {refractionLayer(0, 'url(#ui-warp)', 0.9 * g)}
      {/* chromatic aberration: channel-split copies */}
      {refractionLayer(-1.6, 'url(#ui-warp) url(#ui-chroma-r)', 0.55 * g, 'screen')}
      {refractionLayer(1.6, 'url(#ui-warp) url(#ui-chroma-b)', 0.55 * g, 'screen')}
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
            'radial-gradient(circle at 34% 30%, rgba(255,255,255,0.85) 0%, rgba(255,255,255,0.0) 68%)',
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
            'radial-gradient(circle at 70% 75%, rgba(255,255,255,0.16) 0%, rgba(255,255,255,0.0) 70%)',
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
          backgroundColor: 'rgba(255,255,255,0.95)',
          filter: 'blur(1px)',
          opacity: g,
        }}
      />
    </div>
  );
};
