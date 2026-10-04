import type {FC} from 'react';
import {AbsoluteFill, Easing, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, L} from '../constants';
import {GlassSlider} from '../components/GlassSlider';
import {ParamLabel} from '../components/ParamLabel';
import {ParticleField} from '../components/ParticleField';
import {Stage} from '../components/Stage';
import {ThreeLayerStack, type LayerSpec} from '../components/ThreeLayerStack';
import {clamp, seg} from '../util';

/**
 * Scene 3 (frames 400-719 absolute): the material transformation.
 * The three flat layers melt together into one liquid glass slider:
 *  0-22   layers converge vertically and dissolve
 *  12-78  the glass capsule materializes (backdrop blur, refraction, dispersion)
 *  20-80  particle field fades in behind the stage
 *  80-280 viscous liquid slides with squash & stretch + jello arrivals
 *  280-320 arrival and rest at 50%
 */
export const Scene3: FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();

  // flat stack frozen at scene 2's final state, merging & dissolving
  const stackOp = seg(f, 4, 30, 1, 0, Easing.inOut(Easing.cubic));
  const merge2 = seg(f, 0, 22, 0, 40, Easing.inOut(Easing.cubic));
  const merge3 = seg(f, 0, 22, 0, 80, Easing.inOut(Easing.cubic));
  const flatLabelOp = seg(f, 0, 12, 1, 0, Easing.linear);

  // glass materialization
  const mat = seg(f, 12, 78, 0, 1, Easing.inOut(Easing.cubic));
  const glassScale =
    f < 8
      ? 0.94
      : spring({
          frame: f - 8,
          fps,
          from: 0.94,
          to: 1,
          config: {damping: 14, stiffness: 120, mass: 1},
        });

  const particleMaster = seg(f, 20, 80, 0, 1, Easing.out(Easing.cubic));
  const labelsOp = seg(f, 40, 78, 0, 1, Easing.out(Easing.cubic));

  const flatLayers: LayerSpec[] = [
    {
      y: 820 + merge3,
      scale: 1,
      opacity: 1,
      value: 90,
      trackColor: C.trackL3,
      endpointColor: C.endpointL3,
      fillOpacity: 0.45,
      thumbOpacity: 0.5,
    },
    {
      y: 860 + merge2,
      scale: 1,
      opacity: 1,
      value: 45,
      trackColor: C.trackL2,
      endpointColor: C.endpointL2,
      fillOpacity: 0.7,
      thumbOpacity: 0.72,
    },
    {
      y: 900,
      scale: 1,
      opacity: 1,
      value: 70,
      trackColor: C.track,
      endpointColor: C.endpoint,
      fillOpacity: 0.9,
      thumbOpacity: 1,
      glow: 0.5,
    },
  ];

  const flatThumbX0 = L.trackLeft + 0.7 * L.trackW;

  return (
    <AbsoluteFill style={{backgroundColor: C.bg}}>
      <ParticleField phase={f} master={particleMaster} />
      <Stage>
        {stackOp > 0.005 ? (
          <>
            <ThreeLayerStack
              layers={flatLayers}
              connectors={{opacity: 0.85 * stackOp, scaleY: 1}}
            />
            <ParamLabel
              x={clamp(flatThumbX0, 400, 680)}
              y={952}
              text="Opacity"
              value="70%"
              opacity={flatLabelOp * stackOp}
            />
            <ParamLabel
              x={L.trackLeft + L.trackW + 30}
              y={860 + merge2}
              text="Brightness"
              value="45%"
              align="left"
              opacity={flatLabelOp * stackOp}
            />
            <ParamLabel
              x={L.trackLeft + L.trackW + 30}
              y={820 + merge3}
              text="Blur"
              value="90%"
              align="left"
              opacity={flatLabelOp * stackOp}
            />
          </>
        ) : null}
        <GlassSlider
          phase={f}
          materialization={mat}
          scale={glassScale}
          labelsOpacity={labelsOp}
        />
      </Stage>
    </AbsoluteFill>
  );
};
