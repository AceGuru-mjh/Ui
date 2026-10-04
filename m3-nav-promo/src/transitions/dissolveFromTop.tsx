import type {FC} from 'react';
import {AbsoluteFill, interpolate} from 'remotion';
import type {
  TransitionPresentation,
  TransitionPresentationComponentProps,
} from '@remotion/transitions';

type Props = Record<string, unknown>;

/**
 * Custom presentation: "dissolve sliding in from above".
 * The entering scene floats down from the top while un-blurring and fading
 * in; the exiting scene softly fades, drifts down and blurs out.
 */
const DissolveFromTopPresentation: FC<
  TransitionPresentationComponentProps<Props>
> = ({children, presentationProgress, presentationDirection}) => {
  const p = presentationProgress;
  const entering = presentationDirection === 'entering';
  return (
    <AbsoluteFill
      style={
        entering
          ? {
              opacity: p,
              translate: `0px ${interpolate(p, [0, 1], [-90, 0]).toFixed(1)}px`,
              filter: `blur(${interpolate(p, [0, 1], [12, 0]).toFixed(2)}px)`,
            }
          : {
              opacity: 1 - p,
              translate: `0px ${interpolate(p, [0, 1], [0, 24]).toFixed(1)}px`,
              filter: `blur(${interpolate(p, [0, 1], [0, 6]).toFixed(2)}px)`,
            }
      }
    >
      {children}
    </AbsoluteFill>
  );
};

export const dissolveFromTop = (): TransitionPresentation<Props> => ({
  component: DissolveFromTopPresentation,
  props: {},
});
