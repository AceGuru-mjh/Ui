import type {FC} from 'react';
import {AbsoluteFill, Easing, useCurrentFrame, useVideoConfig} from 'remotion';
import {DAY} from '../constants';
import {AmbientBlobs} from '../components/AmbientBlobs';
import {LightningGrid} from '../components/LightningGrid';
import {Stage} from '../components/Stage';
import {ThinkingSlider} from '../components/ThinkingSlider';
import {seg} from '../util';

/**
 * Scene 3 (660 frames): the advanced five-level THINKING slider.
 *   0-42   the glass capsule morphs into the wide thinking track,
 *          the glass ball returns to OFF and becomes the pill thumb,
 *          notch dots + OFF/LOW/MID/HIGH/ULTRA ticks stagger in
 *  70-316  the sweep: LOW -> MID -> HIGH -> ULTRA, spring snaps + jelly,
 *          fill color ramping gradually through the palette
 *  300+    ULTRA: rainbow fills, then the neon lightning grid erupts
 *  318-596 irregular lightning strikes (bolt sweeps left->right)
 *  616-660 the storm calms; everything breathes and settles cleanly
 */
export const Scene3: FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();

  const morph = seg(f, 2, 42, 0, 1, Easing.inOut(Easing.cubic));
  const ambient = seg(f, 0, 30, 0.9, 0.75, Easing.out(Easing.cubic));

  // finale: gentle breathing settle in the last stretch
  const breathe = 1 + 0.01 * Math.sin((2 * Math.PI * (f - 500)) / 120);

  return (
    <AbsoluteFill style={{backgroundColor: DAY.bg}}>
      <AmbientBlobs master={ambient} phase={f + 390} />
      <Stage>
        <ThinkingSlider frame={f} fps={fps} morph={morph} scale={breathe} />
        <LightningGrid frame={f} fps={fps} />
      </Stage>
    </AbsoluteFill>
  );
};
