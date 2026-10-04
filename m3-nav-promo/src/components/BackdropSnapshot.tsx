import type {FC} from 'react';
import {AmbientField} from './AmbientField';
import {ParticleField} from './ParticleField';

/**
 * The full-stage "world behind the glass" (ambient tonal fields + particles).
 * Rendered once as the real backdrop and re-rendered, magnified, inside every
 * glass circle as its refraction source. Pure function of `phase`.
 */
export const BackdropSnapshot: FC<{phase: number; master: number}> = ({phase, master}) => (
  <div style={{position: 'absolute', left: 0, top: 0, width: 1080, height: 1920}}>
    <AmbientField phase={phase} master={master} />
    <ParticleField phase={phase} master={1} />
  </div>
);
