import type {FC} from 'react';
import {Composition} from 'remotion';
import {SCENES, VIDEO} from './constants';
import {Video} from './Video';
import {Scene1} from './scenes/Scene1Blocks';
import {Scene2} from './scenes/Scene2LiquidGlass';
import {Scene3} from './scenes/Scene3Thinking';

/**
 * The main composition plus one composition per scene, so every scene can be
 * previewed and still-rendered independently:
 *   npx remotion still Scene1 keyframes/s1.png --frame=120
 */
export const RemotionRoot: FC = () => (
  <>
    <Composition
      id="ThinkingSliderDay"
      component={Video}
      width={VIDEO.width}
      height={VIDEO.height}
      fps={VIDEO.fps}
      durationInFrames={VIDEO.durationInFrames}
    />
    <Composition
      id="Scene1"
      component={Scene1}
      width={VIDEO.width}
      height={VIDEO.height}
      fps={VIDEO.fps}
      durationInFrames={SCENES.s1}
    />
    <Composition
      id="Scene2"
      component={Scene2}
      width={VIDEO.width}
      height={VIDEO.height}
      fps={VIDEO.fps}
      durationInFrames={SCENES.s2}
    />
    <Composition
      id="Scene3"
      component={Scene3}
      width={VIDEO.width}
      height={VIDEO.height}
      fps={VIDEO.fps}
      durationInFrames={SCENES.s3}
    />
  </>
);
