import type {FC} from 'react';
import {AbsoluteFill, Easing, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, S4_PHASE_OFFSET} from '../constants';
import {GlassSlider} from '../components/GlassSlider';
import {ParticleField} from '../components/ParticleField';
import {Stage} from '../components/Stage';
import {seg} from '../util';

/**
 * Scene 4 (frames 720-899): settle and breathe.
 * Continues scene 3's animation phase (hard cut, seamless) with the thumb
 * resting at 50%. A soft spring pulse 1.0 -> 1.03 -> 1.0 signals completion,
 * the particles slowly fade out, and the final 30 frames perform a barely
 * visible global "breath" (opacity 1 -> 0.95 -> 1).
 */
export const Scene4: FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  const phase = S4_PHASE_OFFSET + f;

  // completion pulse via spring ringdown
  const pulse =
    f < 12
      ? seg(f, 0, 10, 1, 1.03, Easing.out(Easing.cubic))
      : spring({
          frame: f - 10,
          fps,
          from: 1.03,
          to: 1,
          config: {damping: 8, stiffness: 120, mass: 1},
        });

  // particles slowly fade out
  const particleMaster = seg(f, 0, 150, 1, 0, Easing.in(Easing.quad));

  // final breathing: opacity 1 -> 0.95 -> 1 over the last 30 frames
  const breath = f < 150 ? 1 : 1 - 0.05 * Math.sin((Math.PI * (f - 150)) / 30);

  return (
    <AbsoluteFill style={{backgroundColor: C.bg, opacity: breath}}>
      <ParticleField phase={phase} master={particleMaster} />
      <Stage>
        <GlassSlider phase={phase} scale={pulse} />
      </Stage>
    </AbsoluteFill>
  );
};
