import type {FC} from 'react';
import {AbsoluteFill, Easing, useCurrentFrame, useVideoConfig} from 'remotion';
import {DAY, L} from '../constants';
import {M3BlockSlider} from '../components/M3BlockSlider';
import {Stage} from '../components/Stage';
import {seg, wobble} from '../util';

/**
 * Scene 1 (324 frames): the Material 3 slider assembles from snap-in
 * building blocks (track -> fill -> thumb -> label -> value indicator),
 * then plays a short drag demo. Pure M3 baseline-light, nothing else.
 */
export const s1Value = (f: number): number => {
  if (f < 150) return 50;
  if (f < 200) return seg(f, 150, 200, 50, 85, Easing.bezier(0.5, 0.05, 0.25, 1));
  if (f < 222) return 85;
  if (f < 262) return seg(f, 222, 262, 85, 30, Easing.bezier(0.5, 0.05, 0.25, 1));
  if (f < 274) return 30;
  if (f < 302) return seg(f, 274, 302, 30, 50, Easing.bezier(0.35, 0, 0.25, 1));
  return 50;
};

/** Springy overshoot on each demo arrival. */
export const s1ValueWobble = (f: number): number =>
  wobble(f, 200, 2.2, 6, 1.1) + wobble(f, 262, -1.8, 6, 1.1) + wobble(f, 302, 1.4, 6, 1.1);

export const Scene1: FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  const value = s1Value(f) + s1ValueWobble(f);

  return (
    <AbsoluteFill style={{backgroundColor: DAY.bg}}>
      <Stage>
        <M3BlockSlider value={value} frame={f} fps={fps} />
      </Stage>
    </AbsoluteFill>
  );
};
