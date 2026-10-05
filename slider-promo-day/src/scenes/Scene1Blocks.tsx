import type {FC} from 'react';
import {AbsoluteFill, Easing, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, L} from '../constants';
import {BlockThumb} from '../components/BlockThumb';
import {BlocksTrack} from '../components/BlocksTrack';
import {ParamLabel} from '../components/ParamLabel';
import {Stage} from '../components/Stage';
import {clamp, seg, velocity, wobble} from '../util';

/**
 * Scene 1 value timeline (local frames 0-389 @60fps):
 *  0-78    assembly: bricks drop in one by one, endpoints snap, no thumb yet
 *  52-78   the blocky thumb falls onto the left end and lands with a squash
 *  88-108  "Opacity" label fades in
 *  118-160 0 -> 100     (full sweep, every brick lights up)
 *  168-210 100 -> 0
 *  218-260 0 -> 50
 *  274-312 50 -> 68     (slow quality-check slide)
 *  320-356 68 -> 50
 *  356-390 rest (under the outgoing fade)
 * interpolate()-driven with decaying arrival wobbles ("spring back-bounce").
 */
export const scene1Value = (f: number): number => {
  if (f < 118) {
    return 8;
  }
  let v: number;
  if (f < 168) {
    v = seg(f, 118, 160, 8, 100, Easing.inOut(Easing.cubic));
  } else if (f < 218) {
    v = seg(f, 168, 210, 100, 0, Easing.inOut(Easing.cubic));
  } else if (f < 274) {
    v = seg(f, 218, 260, 0, 50, Easing.inOut(Easing.cubic));
  } else if (f < 320) {
    v = seg(f, 274, 312, 50, 68, Easing.inOut(Easing.cubic));
  } else if (f < 356) {
    v = seg(f, 320, 356, 68, 50, Easing.inOut(Easing.cubic));
  } else {
    v = 50;
  }
  v += wobble(f, 160, 2.2) + wobble(f, 210, 2.0) + wobble(f, 260, 2.2);
  v += wobble(f, 312, 1.6) + wobble(f, 356, 1.8);
  return v;
};

/**
 * Scene 1 (frames 0-389): the most basic slider, assembled on screen like
 * building blocks - eight track bricks drop and click into a rail, end
 * plates snap on, the blocky thumb falls onto the track and starts sliding.
 */
export const Scene1: FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();

  const value = scene1Value(f);
  const v = clamp(value, 0, 100);
  const thumbX = L.trackLeft + (value / 100) * L.trackW;

  // thumb drop-in: accelerating fall, impact squash, dust ring
  const thumbVisible = f >= 52;
  const drop = seg(f, 52, 72, -430, 0, Easing.in(Easing.quad));
  const squash = wobble(f, 72, -0.16, 7, 1.5);
  const sx = 1 - squash * 0.8;
  const sy = 1 + squash;
  const ringP = seg(f, 72, 94, 0, 1, Easing.out(Easing.cubic));
  const ringOp = f < 72 ? 0 : 0.35 * (1 - ringP);

  const vel = Math.abs(velocity(scene1Value, f, 2));
  const moving = clamp(vel * 0.12, 0, 1);

  const labelOp = seg(f, 88, 106, 0, 1, Easing.out(Easing.cubic));

  return (
    <AbsoluteFill style={{backgroundColor: C.bg}}>
      <Stage>
        <BlocksTrack y={L.stageY} f={f} fps={fps} thumbX={thumbX} moving={moving} />
        {thumbVisible ? (
          <>
            {/* impact ring */}
            <div
              style={{
                position: 'absolute',
                left: thumbX - 40,
                top: L.stageY + L.thumbSize / 2 - 6,
                width: 80,
                height: 12,
                borderRadius: 6,
                border: `2px solid ${C.ink}`,
                opacity: ringOp,
                scale: `${0.5 + ringP * 1.2} 1`,
              }}
            />
            <BlockThumb
              x={thumbX}
              y={L.stageY}
              p={1}
              drop={drop}
              sx={sx}
              sy={sy}
            />
          </>
        ) : null}
        <ParamLabel
          x={clamp(thumbX, 420, 660)}
          y={L.stageY + 66}
          text="Opacity"
          value={`${Math.round(v)}%`}
          opacity={labelOp}
        />
      </Stage>
    </AbsoluteFill>
  );
};
