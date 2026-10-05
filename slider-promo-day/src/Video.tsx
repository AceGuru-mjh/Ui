import type {FC} from 'react';
import {AbsoluteFill} from 'remotion';
import {TransitionSeries, linearTiming} from '@remotion/transitions';
import {fade} from '@remotion/transitions/fade';
import {C, SCENES, TRANSITIONS} from './constants';
import {dissolveRise} from './transitions/dissolveRise';
import {Scene1} from './scenes/Scene1Blocks';
import {Scene2} from './scenes/Scene2Glass';
import {Scene3} from './scenes/Scene3Levels';

/**
 * Main timeline (1320 frames @60fps = 22.0s, silent):
 *   Scene1 (390) --fade 24--> Scene2 (330) --dissolve-rise 30--> Scene3 (654)
 * Total = 390 + 330 + 654 - 24 - 30 = 1320.
 * No audio track: the creator adds Douyin BGM in-app.
 */
export const Video: FC = () => (
  <AbsoluteFill style={{backgroundColor: C.bg}}>
    <TransitionSeries>
      <TransitionSeries.Sequence durationInFrames={SCENES.s1}>
        <Scene1 />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition
        presentation={fade()}
        timing={linearTiming({durationInFrames: TRANSITIONS.t12})}
      />
      <TransitionSeries.Sequence durationInFrames={SCENES.s2}>
        <Scene2 />
      </TransitionSeries.Sequence>
      <TransitionSeries.Transition
        presentation={dissolveRise()}
        timing={linearTiming({durationInFrames: TRANSITIONS.t23})}
      />
      <TransitionSeries.Sequence durationInFrames={SCENES.s3}>
        <Scene3 />
      </TransitionSeries.Sequence>
    </TransitionSeries>
  </AbsoluteFill>
);
