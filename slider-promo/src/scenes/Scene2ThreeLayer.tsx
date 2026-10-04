import type {FC} from 'react';
import {AbsoluteFill, Easing, spring, useCurrentFrame, useVideoConfig} from 'remotion';
import {C, L} from '../constants';
import {ParamLabel} from '../components/ParamLabel';
import {Stage} from '../components/Stage';
import {ThreeLayerStack, type LayerSpec} from '../components/ThreeLayerStack';
import {clamp, clamp01, seg} from '../util';

const LAYER_INIT = [50, 28, 62];
const LAYER_TARGET = [70, 45, 90];
const LAYER_START = [75, 90, 105];

/**
 * Scene 2 layer value timeline (local frames 0-254):
 * layers hold their initial values, then cascade with a 15-frame stagger.
 */
export const scene2Value = (f: number, layer: 0 | 1 | 2): number => {
  const init = LAYER_INIT[layer];
  const start = LAYER_START[layer];
  if (f < start) {
    return init;
  }
  return spring({
    frame: f - start,
    fps: 30,
    from: init,
    to: LAYER_TARGET[layer],
    config: {damping: 13, stiffness: 90, mass: 1.05},
  });
};

/**
 * Scene 2 (frames 165-419 absolute): the slider evolves into a three-layer
 * stacked structure. Two extra tracks spring in behind (40px apart), thin
 * connector lines suggest depth, and the three thumbs cascade with a
 * 15-frame stagger while the whole group gently floats upward.
 */
export const Scene2: FC = () => {
  const f = useCurrentFrame();
  const {fps} = useVideoConfig();

  const v0 = scene2Value(f, 0);
  const v1 = scene2Value(f, 1);
  const v2 = scene2Value(f, 2);
  const vc = (x: number) => Math.round(clamp(x, 0, 100));

  // construction (local 15-75, i.e. absolute 180-240)
  const l2Scale =
    0.95 +
    0.05 *
      (f < 15 ? 0 : spring({frame: f - 15, fps, config: {damping: 12, stiffness: 110, mass: 1}}));
  const l2Op = seg(f, 15, 26, 0, 1, Easing.out(Easing.cubic));
  const l3Scale =
    0.95 +
    0.05 *
      (f < 35 ? 0 : spring({frame: f - 35, fps, config: {damping: 12, stiffness: 110, mass: 1}}));
  const l3Op = seg(f, 35, 46, 0, 1, Easing.out(Easing.cubic));
  const connOp = seg(f, 30, 55, 0, 0.85, Easing.out(Easing.cubic));
  const connGrow = seg(f, 30, 58, 0, 1, Easing.out(Easing.cubic));

  // gentle upward float during the cascade ("upgrade" cue)
  const floatY =
    f < 75
      ? 0
      : spring({frame: f - 75, fps, from: 0, to: -10, config: {damping: 15, stiffness: 60, mass: 1}});

  // fills brighten as each layer slides to its target
  const t1 = clamp01((v1 - LAYER_INIT[1]) / (LAYER_TARGET[1] - LAYER_INIT[1]));
  const t2 = clamp01((v2 - LAYER_INIT[2]) / (LAYER_TARGET[2] - LAYER_INIT[2]));

  const layers: LayerSpec[] = [
    {
      y: 820 + floatY,
      scale: l3Scale,
      opacity: l3Op,
      value: v2,
      trackColor: C.trackL3,
      endpointColor: C.endpointL3,
      fillOpacity: 0.15 + 0.3 * t2,
      thumbOpacity: 0.5,
    },
    {
      y: 860 + floatY,
      scale: l2Scale,
      opacity: l2Op,
      value: v1,
      trackColor: C.trackL2,
      endpointColor: C.endpointL2,
      fillOpacity: 0.25 + 0.45 * t1,
      thumbOpacity: 0.72,
    },
    {
      y: 900 + floatY,
      scale: 1,
      opacity: 1,
      value: v0,
      trackColor: C.track,
      endpointColor: C.endpoint,
      fillOpacity: 0.9,
      thumbOpacity: 1,
      glow: 0.5,
    },
  ];

  const thumbX0 = L.trackLeft + (clamp(v0, 0, 100) / 100) * L.trackW;
  const label2Op = seg(f, 26, 45, 0, 1, Easing.out(Easing.cubic));
  const label3Op = seg(f, 46, 65, 0, 1, Easing.out(Easing.cubic));

  return (
    <AbsoluteFill style={{backgroundColor: C.bg}}>
      <Stage>
        <ThreeLayerStack layers={layers} connectors={{opacity: connOp, scaleY: connGrow}} />
        <ParamLabel
          x={clamp(thumbX0, 400, 680)}
          y={900 + floatY + 52}
          text="Opacity"
          value={`${vc(v0)}%`}
        />
        <ParamLabel
          x={L.trackLeft + L.trackW + 30}
          y={860 + floatY}
          text="Brightness"
          value={`${vc(v1)}%`}
          align="left"
          opacity={label2Op}
        />
        <ParamLabel
          x={L.trackLeft + L.trackW + 30}
          y={820 + floatY}
          text="Blur"
          value={`${vc(v2)}%`}
          align="left"
          opacity={label3Op}
        />
      </Stage>
    </AbsoluteFill>
  );
};
