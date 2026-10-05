import type {FC} from 'react';
import {AbsoluteFill, interpolate} from 'remotion';
import type {
  TransitionPresentation,
  TransitionPresentationComponentProps,
} from '@remotion/transitions';

type Props = Record<string, unknown>;

/**
 * Custom presentation: "dissolve rising from below" (day edition).
 * The entering scene rises softly while un-blurring and fading in; the
 * exiting scene dissolves upward and blurs out - like warm air lifting the
 * old material away.
 */
const DissolveRisePresentation: FC<
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
              translate: `0px ${interpolate(p, [0, 1], [110, 0]).toFixed(1)}px`,
              filter: `blur(${interpolate(p, [0, 1], [14, 0]).toFixed(2)}px)`,
            }
          : {
              opacity: 1 - p,
              translate: `0px ${interpolate(p, [0, 1], [0, -60]).toFixed(1)}px`,
              filter: `blur(${interpolate(p, [0, 1], [0, 8]).toFixed(2)}px)`,
            }
      }
    >
      {children}
    </AbsoluteFill>
  );
};

export const dissolveRise = (): TransitionPresentation<Props> => ({
  component: DissolveRisePresentation,
  props: {},
});
