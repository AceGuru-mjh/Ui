import type {FC} from 'react';
import {AbsoluteFill, Audio, staticFile} from 'remotion';
import {TransitionSeries, linearTiming} from '@remotion/transitions';
import {fade} from '@remotion/transitions/fade';
import {M3, SCENES, TRANSITIONS} from './constants';
import {dissolveFromTop} from './transitions/dissolveFromTop';
import {Scene1} from './scenes/Scene1M3Basic';
import {Scene2} from './scenes/Scene2Hierarchy';
import {Scene3} from './scenes/Scene3LiquidGlass';
import {Scene4} from './scenes/Scene4Finale';

/**
 * Main timeline (900 frames @30fps):
 *   Scene1 (180) --fade 15--> Scene2 (255) --dissolve-from-top 20--> Scene3 (320)
 *   Scene3 --hard cut, seamless continuation--> Scene4 (180)
 * Total = 180 + 255 + 320 + 180 - 15 - 20 = 900.
 * The synthesized ambient BGM lives in public/bgm.mp3.
 */
export const Video: FC = () => (
  <AbsoluteFill style={{backgroundColor: M3.bg}}>
    <Audio src={staticFile('bgm.mp3')} />
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
      <TransitionSeries.Sequence durationInFrames={SCENES.s4}>
        <Scene4 />
      </TransitionSeries.Sequence>
    </TransitionSeries>
  </AbsoluteFill>
);
