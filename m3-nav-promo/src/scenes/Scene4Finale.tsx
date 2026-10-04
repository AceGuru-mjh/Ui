import type {FC} from 'react';
import {AbsoluteFill, Easing, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {M3, S4_PHASE_OFFSET} from '../constants';
import {GlassNavRing} from '../components/GlassNavRing';
import {FilterDefs} from '../components/GlassCircle';
import {Stage} from '../components/Stage';
import {seg, wobble} from '../util';

/**
 * Scene 4 (frames 720-899 absolute): converge, settle & breathe.
 * Continues scene 3's animation phase seamlessly (phase = 320 + local):
 *   10-43   glass indicator makes its final hop Person -> Home (p330-363)
 *   0-150   particles slowly fade out, ambient dims - the void returns
 *   60-90   whole-assembly spring pulse 1.0 -> 1.03 -> 1.0
 *   60-150  a specular highlight sweeps a full lap around the glass rim
 *   90-180  calm breathing; final 30 frames opacity 1 -> 0.95 -> 1
 */
export const Scene4: FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();
  const p = S4_PHASE_OFFSET + f;

  // finale pulse (spring overshoot around 1.03, settles back)
  const pulse =
    f < 60
      ? 1
      : 1 +
        0.03 *
          spring({
            frame: f - 60,
            fps,
            from: 0,
            to: 1,
            config: {damping: 16, stiffness: 200, mass: 1},
          }) -
        0.015 * seg(f, 75, 120, 0, 2, Easing.inOut(Easing.cubic));
  // gentle residual jelly after the pulse
  const jelly = Math.abs(wobble(f, 86, 0.008, 6, 0.9));

  const groupScale = pulse * (1 + jelly);

  // atmosphere recedes
  const ambientMaster = seg(f, 0, 150, 1, 0.55, Easing.inOut(Easing.cubic));
  const particleMaster = seg(f, 60, 150, 1, 0.12, Easing.inOut(Easing.cubic));

  // specular rim sweep, one full lap
  const finaleSweep = seg(f, 60, 150, 0, 1, Easing.inOut(Easing.cubic));

  // breathing: slow 1 -> 0.97 -> 1 over 90..180, final 30f 1 -> 0.95 -> 1
  const breath = f < 90 ? 1 : 1 - 0.03 * Math.sin(((f - 90) / 90) * Math.PI);
  const finalBreath =
    f >= 150 ? 1 - 0.05 * Math.sin(((f - 150) / 30) * Math.PI) : 1;
  const groupOpacity = Math.min(breath, finalBreath);

  return (
    <AbsoluteFill style={{backgroundColor: M3.bg}}>
      <FilterDefs />
      <Stage>
        <GlassNavRing
          phase={p}
          glassiness={1}
          groupScale={groupScale}
          groupOpacity={groupOpacity}
          ambientMaster={ambientMaster}
          particleMaster={particleMaster}
          finaleSweep={finaleSweep}
        />
      </Stage>
    </AbsoluteFill>
  );
};
