import type {FC} from 'react';

/**
 * Hidden SVG filter defs shared by the glass thumb (day edition):
 *  - channel isolation filters for chromatic aberration
 *  - a gentle turbulence displacement for the liquid refraction edge
 * Different seed/frequency from the night build - its own signature.
 */
export const FilterDefsDay: FC = () => (
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
      <filter id="ui-warp-day" x="-20%" y="-20%" width="140%" height="140%">
        <feTurbulence
          type="fractalNoise"
          baseFrequency="0.014 0.045"
          numOctaves={2}
          seed={11}
          result="noise"
        />
        <feDisplacementMap
          in="SourceGraphic"
          in2="noise"
          scale={5}
          xChannelSelector="R"
          yChannelSelector="G"
        />
      </filter>
    </defs>
  </svg>
);
