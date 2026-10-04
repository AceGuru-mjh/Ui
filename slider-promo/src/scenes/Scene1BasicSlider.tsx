import type {FC} from 'react';
import {AbsoluteFill, Easing, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, L} from '../constants';
import {ParamLabel} from '../components/ParamLabel';
import {SliderThumb} from '../components/SliderThumb';
import {SliderTrack} from '../components/SliderTrack';
import {Stage} from '../components/Stage';
import {clamp, seg, velocity, wobble} from '../util';

/**
 * Scene 1 value timeline (local frames 0-179):
 *  0-35   thumb rests at the left end
 *  35-62  spring slide to the middle (assembly)
 *  62-92  50% -> 100%
 *  97-127 100% -> 0%
 *  132-160 0% -> 50%
 *  165-180 rest at 50% (under the outgoing fade)
 * interpolate()-driven with decaying arrival wobbles ("spring 回弹").
 */
export const scene1Value = (f: number): number => {
  if (f < 35) {
    return 0;
  }
  if (f < 62) {
    return spring({
      frame: f - 35,
      fps: 30,
      from: 0,
      to: 50,
      config: {damping: 16, stiffness: 160, mass: 1},
    });
  }
  let v: number;
  if (f < 95) {
    v = seg(f, 62, 92, 50, 100, Easing.inOut(Easing.cubic));
  } else if (f < 130) {
    v = seg(f, 97, 127, 100, 0, Easing.inOut(Easing.cubic));
  } else if (f < 165) {
    v = seg(f, 132, 160, 0, 50, Easing.inOut(Easing.cubic));
  } else {
    v = 50;
  }
  v += wobble(f, 92, 2.5) + wobble(f, 127, 2.2) + wobble(f, 160, 2.5);
  return v;
};

/**
 * Scene 1 (frames 0-179): the most basic slider, assembled on screen from
 * primitives - a thin line, two endpoints, a round thumb, one label.
 */
export const Scene1: FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();

  const value = scene1Value(f);
  const v = clamp(value, 0, 100);
  const thumbX = L.trackLeft + (value / 100) * L.trackW;
  const fillW = (v / 100) * L.trackW;

  // assembly staging
  const trackGrow = seg(f, 0, 15, 0, 1, Easing.out(Easing.cubic));
  const epL =
    f < 16 ? 0 : spring({frame: f - 16, fps, config: {damping: 11, stiffness: 130, mass: 1}});
  const epR =
    f < 22 ? 0 : spring({frame: f - 22, fps, config: {damping: 11, stiffness: 130, mass: 1}});
  const thumbPop =
    f < 35 ? 0 : spring({frame: f - 35, fps, config: {damping: 10, stiffness: 170, mass: 1}});
  const labelOp = seg(f, 55, 60, 0, 1, Easing.out(Easing.cubic));
  const glow = seg(f, 50, 70, 0, 0.9, Easing.out(Easing.cubic));

  // subtle squash & stretch from movement velocity
  const vel = Math.abs(velocity(scene1Value, f, 2));
  const q = Math.min(vel * 0.008, 0.06);

  return (
    <AbsoluteFill style={{backgroundColor: C.bg}}>
      <Stage>
        <SliderTrack
          x={L.trackLeft}
          y={L.stageY}
          width={L.trackW}
          height={L.trackH}
          color={C.track}
          growScaleX={trackGrow}
          fillWidth={fillW}
          fillOpacity={0.92}
          endpoints={{r: L.endpointR, color: C.endpoint, leftScale: epL, rightScale: epR}}
        />
        <SliderThumb
          x={thumbX}
          y={L.stageY}
          r={L.thumbR}
          color={C.thumb}
          scaleX={thumbPop * (1 + q)}
          scaleY={thumbPop * (1 - q * 0.7)}
          glow={glow}
        />
        <ParamLabel
          x={clamp(thumbX, 400, 680)}
          y={L.stageY + 52}
          text="Opacity"
          value={`${Math.round(v)}%`}
          opacity={labelOp}
        />
      </Stage>
    </AbsoluteFill>
  );
};
