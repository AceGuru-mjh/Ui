import type {FC} from 'react';
import {AbsoluteFill} from 'remotion';
import {TransitionSeries, linearTiming} from '@remotion/transitions';
import {fade} from '@remotion/transitions/fade';
import {DAY, SCENES, TRANSITIONS} from './constants';
import {dissolveFromTop} from './transitions/dissolveFromTop';
import {Scene1} from './scenes/Scene1Blocks';
import {Scene2} from './scenes/Scene2LiquidGlass';
import {Scene3} from './scenes/Scene3Thinking';

/**
 * Main timeline (1320 frames @60fps = 22.0s, silent):
 *   Scene1 (324) --fade 24--> Scene2 (390) --dissolve-from-top 30--> Scene3 (660)
 * Total = 324 + 390 + 660 - 24 - 30 = 1320.
 * No audio track by design - music is added on the Douyin side.
 */
export const Video: FC = () => (
  <AbsoluteFill style={{backgroundColor: DAY.bg}}>
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
        presentation={dissolveFromTop()}
        timing={linearTiming({durationInFrames: TRANSITIONS.t23})}
      />
      <TransitionSeries.Sequence durationInFrames={SCENES.s3}>
        <Scene3 />
      </TransitionSeries.Sequence>
    </TransitionSeries>
  </AbsoluteFill>
);
