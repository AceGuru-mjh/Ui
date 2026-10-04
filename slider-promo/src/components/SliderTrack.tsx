import type {FC} from 'react';
import {C} from '../constants';

export type EndpointsSpec = {
  r: number;
  color: string;
  leftScale: number;
  rightScale: number;
  opacity?: number;
};

export type SliderTrackProps = {
  /** left end x (logical stage px) */
  x: number;
  /** track center y */
  y: number;
  width: number;
  height: number;
  color: string;
  opacity?: number;
  /** 0..1 horizontal grow-in from the center */
  growScaleX?: number;
  fillWidth?: number;
  fillColor?: string;
  fillOpacity?: number;
  endpoints?: EndpointsSpec;
};

/**
 * Flat slider track: line + optional filled portion + optional round endpoints.
 * Everything is frame-driven; there are no CSS transitions.
 */
export const SliderTrack: FC<SliderTrackProps> = ({
  x,
  y,
  width,
  height,
  color,
  opacity = 1,
  growScaleX = 1,
  fillWidth = 0,
  fillColor = C.fill,
  fillOpacity = 1,
  endpoints,
}) => {
  const fill = Math.max(fillWidth, 0);
  return (
    <>
      <div
        style={{
          position: 'absolute',
          left: x,
          top: y - height / 2,
          width,
          height,
          backgroundColor: color,
          opacity,
          scale: `${growScaleX} 1`,
        }}
      />
      {fill > 0.5 ? (
        <div
          style={{
            position: 'absolute',
            left: x,
            top: y - height / 2,
            width: fill,
            height,
            backgroundColor: fillColor,
            opacity: fillOpacity * opacity,
          }}
        />
      ) : null}
      {endpoints ? (
        <>
          <div
            style={{
              position: 'absolute',
              left: x - endpoints.r,
              top: y - endpoints.r,
              width: endpoints.r * 2,
              height: endpoints.r * 2,
              borderRadius: '50%',
              backgroundColor: endpoints.color,
              opacity: endpoints.opacity ?? 1,
              scale: `${endpoints.leftScale} ${endpoints.leftScale}`,
            }}
          />
          <div
            style={{
              position: 'absolute',
              left: x + width - endpoints.r,
              top: y - endpoints.r,
              width: endpoints.r * 2,
              height: endpoints.r * 2,
              borderRadius: '50%',
              backgroundColor: endpoints.color,
              opacity: endpoints.opacity ?? 1,
              scale: `${endpoints.rightScale} ${endpoints.rightScale}`,
            }}
          />
        </>
      ) : null}
    </>
  );
};
