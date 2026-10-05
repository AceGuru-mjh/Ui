import type {FC} from 'react';
import {AbsoluteFill, Easing, useCurrentFrame, useVideoConfig} from 'remotion';
import {DAY} from '../constants';
import {AmbientBlobs} from '../components/AmbientBlobs';
import {GlassSliderDay} from '../components/GlassSliderDay';
import {M3BlockSlider} from '../components/M3BlockSlider';
import {Stage} from '../components/Stage';
import {seg, velocity} from '../util';

/**
 * Scene 2 (390 frames): the material transformation.
 *  0-46    the flat M3 slider dissolves (blur + lift + fade)
 * 18-86    the liquid-glass capsule materializes (backdrop blur, refraction,
 *          chromatic dispersion, sheen) while ambient tonal light fades in
 * 30-70    labels crossfade OPACITY -> LIQUID GLASS
 * 90-330   viscous glass demo with jelly arrivals (92 -> 24 -> 60)
 * 356-390  labels fade for the dissolve into scene 3
 */
export const s2Value = (f: number): number => {
  if (f < 96) return 50;
  if (f < 190) return seg(f, 96, 190, 50, 92, Easing.bezier(0.5, 0.05, 0.25, 1));
  if (f < 216) return 92;
  if (f < 264) return seg(f, 216, 264, 92, 24, Easing.bezier(0.5, 0.05, 0.25, 1));
  if (f < 288) return 24;
  if (f < 330) return seg(f, 288, 330, 24, 60, Easing.bezier(0.35, 0, 0.25, 1));
  return 60;
};

const ARRIVALS = [190, 264, 330];

export const Scene2: FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();

  // flat M3 slider frozen at 50%, melting away
  const meltOp = seg(f, 0, 46, 1, 0, Easing.in(Easing.cubic));
  const meltLift = seg(f, 0, 46, 0, -26, Easing.in(Easing.cubic));
  const meltBlur = seg(f, 0, 46, 0, 10, Easing.in(Easing.cubic));

  // glass materialization
  const mat = seg(f, 18, 86, 0, 1, Easing.inOut(Easing.cubic));
  const ambient = seg(f, 20, 90, 0, 1, Easing.out(Easing.cubic));
  const glassScale = seg(f, 14, 60, 0.96, 1, Easing.out(Easing.cubic));

  // value & agitation
  const value = s2Value(f);
  const vel = Math.abs(velocity(s2Value, f, 2));

  // labels fade near the scene-3 dissolve
  const labelsOut = seg(f, 356, 384, 1, 0, Easing.in(Easing.cubic));

  return (
    <AbsoluteFill style={{backgroundColor: DAY.bg}}>
      <AmbientBlobs master={ambient * 0.9} phase={f} />
      <Stage>
        {meltOp > 0.005 ? (
          <div
            style={{
              position: 'absolute',
              left: 0,
              top: 0,
              width: 1080,
              height: 1920,
              opacity: meltOp,
              translateY: meltLift,
              filter: `blur(${meltBlur.toFixed(2)}px)`,
            }}
          >
            <M3BlockSlider value={50} frame={999} fps={fps} />
          </div>
        ) : null}
        <GlassSliderDay
          value={value}
          vel={vel}
          phase={f}
          materialization={mat}
          color="#2E7CF6"
          scale={glassScale}
          labelsOpacity={mat * labelsOut}
          arrivals={ARRIVALS}
        />
      </Stage>
    </AbsoluteFill>
  );
};
