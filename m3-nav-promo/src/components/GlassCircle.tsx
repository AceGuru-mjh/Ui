import type {FC, ReactNode} from 'react';
import {M3} from '../constants';
import {BackdropSnapshot} from './BackdropSnapshot';

/**
 * Liquid Glass circle - adapted from the proven glass-ball technique:
 *  - edge refraction: a magnified, warped copy of the stage backdrop behind it
 *  - chromatic dispersion: R/B channel-split copies offset in opposite dirs
 *  - specular arc (top-left) + counter-light + flare dot
 *  - backdrop blur for real translucency
 * `flatColor` cross-fades the M3 tonal fill into glass via `glassiness`.
 * All layers are pure functions of the frame - no CSS transitions.
 */
export type GlassCircleProps = {
  /** center x (stage coords) */
  x: number;
  /** center y (stage coords) */
  y: number;
  r: number;
  /** continuous animation phase for the refraction copy */
  phase: number;
  /** 0 = flat M3 tonal circle, 1 = fully liquid glass */
  glassiness: number;
  /** flat Material 3 fill color (pre-glass) */
  flatColor: string;
  /** flat border color (pre-glass) */
  flatBorder: string;
  /** refraction magnification */
  mag?: number;
  /** chromatic dispersion offset px */
  disp?: number;
  /** jelly squash & stretch / breathing */
  scaleX?: number;
  scaleY?: number;
  /** icon rendered on top of the glass */
  children?: ReactNode;
};

export const GlassCircle: FC<GlassCircleProps> = ({
  x,
  y,
  r,
  phase,
  glassiness,
  flatColor,
  flatBorder,
  mag = 1.16,
  disp = 1.6,
  scaleX = 1,
  scaleY = 1,
  children,
}) => {
  const g = Math.max(0, Math.min(1, glassiness));

  // The magnified backdrop copy: place the world point beneath the circle
  // center exactly at the circle's center (origin 0,0, scale MAG).
  const left = r - mag * x;
  const top = r - mag * y;

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
          top: top + dx * 0.6,
          width: 1080,
          height: 1920,
          scale: `${mag}`,
          transformOrigin: '0px 0px',
          filter,
        }}
      >
        <BackdropSnapshot phase={phase} master={1} />
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
      {/* flat Material 3 tonal fill (pre-melt) */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: '50%',
          backgroundColor: flatColor,
          border: `1.5px solid ${flatBorder}`,
          opacity: 1 - g,
        }}
      />
      {/* translucent glass base with backdrop blur */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: '50%',
          backgroundColor: 'rgba(255,255,255,0.06)',
          backdropFilter: 'blur(3px) saturate(160%) brightness(1.12)',
          border: '1px solid rgba(255,255,255,0.20)',
          opacity: g,
        }}
      />
      {/* edge refraction (magnified, warped) */}
      {refractionLayer(0, 'url(#ui-warp)', 0.85 * g)}
      {/* chromatic dispersion: channel-split copies */}
      {refractionLayer(-disp, 'url(#ui-warp) url(#ui-chroma-r)', 0.5 * g, 'screen')}
      {refractionLayer(disp, 'url(#ui-warp) url(#ui-chroma-b)', 0.5 * g, 'screen')}
      {/* top-left specular arc */}
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
            'radial-gradient(circle at 70% 75%, rgba(208,188,255,0.18) 0%, rgba(255,255,255,0.0) 70%)',
          filter: 'blur(3px)',
          opacity: 0.7 * g,
        }}
      />
      {/* specular flare dot */}
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
      {/* content on the glass (icon) */}
      {children}
    </div>
  );
};

/** Hidden SVG filter defs: channel isolation + liquid warp (shared). */
export const FilterDefs: FC = () => (
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

/** Icon tint helpers for glass mode. */
export const GLASS_ICON_ACTIVE = M3.onSurface;
export const GLASS_ICON_IDLE = 'rgba(230,224,233,0.78)';
