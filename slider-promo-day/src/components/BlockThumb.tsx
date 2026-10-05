import type {FC} from 'react';
import {C, L} from '../constants';

export type BlockThumbProps = {
  /** thumb center x */
  x: number;
  /** track center y */
  y: number;
  /** 0..1 drop-in progress */
  p: number;
  /** vertical offset while dropping (negative = above) */
  drop: number;
  /** impact squash & stretch */
  sx: number;
  sy: number;
  opacity?: number;
};

/**
 * The blocky thumb: a rounded square with two studs on top - a building
 * block riding the brick track. White face, ink border, soft shadow.
 */
export const BlockThumb: FC<BlockThumbProps> = ({
  x,
  y,
  p,
  drop,
  sx,
  sy,
  opacity = 1,
}) => {
  const s = L.thumbSize;
  return (
    <div
      style={{
        position: 'absolute',
        left: x - s / 2,
        top: y - s / 2 + drop,
        width: s,
        height: s,
        scale: `${sx * Math.max(0.0001, p)} ${sy * Math.max(0.0001, p)}`,
        transformOrigin: '50% 100%',
        opacity,
      }}
    >
      {/* soft contact shadow (the depth cue on pure white) */}
      <div
        style={{
          position: 'absolute',
          left: 4,
          top: s - 8,
          width: s - 8,
          height: 10,
          borderRadius: 6,
          filter: 'blur(6px)',
          backgroundColor: C.shadowFar,
        }}
      />
      {/* block body */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: L.thumbR,
          backgroundColor: C.thumbFace,
          border: `2.5px solid ${C.thumbLine}`,
          boxShadow: `0 10px 22px ${C.shadowNear}`,
        }}
      />
      {/* two studs on top - same brick language as the track */}
      {[0.32, 0.68].map((k) => (
        <div
          key={k}
          style={{
            position: 'absolute',
            left: s * k - 9,
            top: -7.5,
            width: 18,
            height: 7,
            borderRadius: 2.5,
            backgroundColor: C.thumbFace,
            border: `2.5px solid ${C.thumbLine}`,
            borderBottom: 'none',
          }}
        />
      ))}
    </div>
  );
};
