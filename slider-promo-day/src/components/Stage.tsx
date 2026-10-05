import type {FC, ReactNode} from 'react';
import {STAGE_SCALE} from '../constants';

/**
 * The 1080x1920 logical stage, uniformly scaled for presentation.
 * Children position elements with logical coordinates; the scale is a single
 * tunable constant for overall visual size on the vertical canvas.
 */
export const Stage: FC<{children: ReactNode; scale?: number}> = ({
  children,
  scale = STAGE_SCALE,
}) => (
  <div
    style={{
      position: 'absolute',
      left: 0,
      top: 0,
      width: 1080,
      height: 1920,
      scale: `${scale}`,
      transformOrigin: '540px 960px',
    }}
  >
    {children}
  </div>
);
