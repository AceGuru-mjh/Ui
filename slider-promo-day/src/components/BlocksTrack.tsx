import type {FC} from 'react';
import {C, L} from '../constants';
import {pop, wobble} from '../util';

export type BlocksTrackProps = {
  /** track center y (stage coords) */
  y: number;
  /** local frame, drives the staggered drop-in when assembling */
  f: number;
  fps: number;
  /** thumb center x - bricks left of it are lit (blocky fill) */
  thumbX: number;
  /** 0..1 movement intensity of the thumb (proximity puff) */
  moving: number;
  /** skip the drop-in (scene 2 shows the finished assembly) */
  assembled?: boolean;
  opacity?: number;
  /** extra vertical offset for the melt animation */
  yOffset?: number;
};

export const BRICK_W = (L.trackW - (L.bricks - 1) * L.brickGap) / L.bricks;

export const brickCenterX = (i: number): number =>
  L.trackLeft + BRICK_W / 2 + i * (BRICK_W + L.brickGap);

const BRICK_AT = (i: number): number => 4 + i * 7;

/** One Lego-ish track brick: body + two studs on top, drops in with a squash. */
const Brick: FC<{
  x: number;
  y: number;
  w: number;
  h: number;
  lit: boolean;
  p: number;
  squash: number;
  puff: number;
  assembled: boolean;
}> = ({x, y, w, h, lit, p, squash, puff, assembled}) => {
  const color = lit ? C.brickLit : C.brickRest;
  const scale = assembled ? 1 : Math.max(0.0001, p);
  const sy = scale * (1 + squash * 0.55 + puff * 0.10);
  const sx = scale * (1 - squash * 0.35 + puff * 0.14);
  // clamp at 0 so the spring overshoot never dips below the rail line
  const drop = assembled ? 0 : Math.min(0, (1 - p) * -76);
  return (
    <div
      style={{
        position: 'absolute',
        left: x - w / 2,
        top: y - h / 2 + drop,
        width: w,
        height: h,
        scale: `${sx} ${sy}`,
        transformOrigin: '50% 100%',
      }}
    >
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: 3,
          backgroundColor: color,
          border: lit ? 'none' : '1px solid rgba(17,17,17,0.07)',
        }}
      />
      {/* two studs on top - the building-block signature */}
      {[0.3, 0.7].map((k) => (
        <div
          key={k}
          style={{
            position: 'absolute',
            left: w * k - 5,
            top: -3.5,
            width: 10,
            height: 5,
            borderRadius: 2,
            backgroundColor: color,
            opacity: sy > 0.9 ? 1 : sy,
          }}
        />
      ))}
    </div>
  );
};

/**
 * The block-built track: 8 bricks drop in one by one, endpoints snap last.
 * Bricks light up discretely as the thumb passes - a blocky fill, no easing.
 */
export const BlocksTrack: FC<BlocksTrackProps> = ({
  y,
  f,
  fps,
  thumbX,
  moving,
  assembled = false,
  opacity = 1,
  yOffset = 0,
}) => {
  const epL = assembled ? 1 : pop(f, 30, fps, 11, 140);
  const epR = assembled ? 1 : pop(f, 38, fps, 11, 140);

  return (
    <div style={{position: 'absolute', inset: 0, opacity}}>
      {Array.from({length: L.bricks}, (_, i) => {
        const at = BRICK_AT(i);
        const p = assembled ? 1 : pop(f, at, fps, 11, 170);
        const squash = assembled ? 0 : wobble(f, at + 6, 0.10, 5, 1.6);
        const puff =
          moving *
          0.9 *
          Math.exp(-(((thumbX - brickCenterX(i)) / 24) ** 2));
        return (
          <Brick
            key={i}
            x={brickCenterX(i)}
            y={y + yOffset}
            w={BRICK_W}
            h={L.brickH}
            lit={thumbX >= brickCenterX(i)}
            p={p}
            squash={assembled ? 0 : squash}
            puff={puff}
            assembled={assembled}
          />
        );
      })}
      {/* endpoint end-plates */}
      {[
        {x: L.trackLeft - 14, s: epL},
        {x: L.trackLeft + L.trackW + 4, s: epR},
      ].map((e, idx) => (
        <div
          key={idx}
          style={{
            position: 'absolute',
            left: e.x,
            top: y + yOffset - L.endpointH / 2,
            width: L.endpointW,
            height: L.endpointH,
            borderRadius: 3,
            backgroundColor: C.endpoint,
            scale: `${Math.max(0.0001, e.s)} 1`,
            opacity: e.s,
          }}
        />
      ))}
    </div>
  );
};
