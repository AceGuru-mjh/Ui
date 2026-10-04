import type {FC} from 'react';
import {C, L} from '../constants';
import {clamp} from '../util';
import {SliderTrack} from './SliderTrack';
import {SliderThumb} from './SliderThumb';

export type LayerSpec = {
  /** track center y of this layer */
  y: number;
  /** construction scale (0.95 -> 1 spring-in) */
  scale: number;
  opacity: number;
  /** 0..100 */
  value: number;
  trackColor: string;
  endpointColor: string;
  fillOpacity: number;
  thumbOpacity: number;
  glow?: number;
};

export type ThreeLayerStackProps = {
  /** ordered back -> front (L3, L2, L1) */
  layers: LayerSpec[];
  connectors: {opacity: number; scaleY: number};
};

/**
 * The flat three-layer slider stack (scene 2's depth illusion).
 * Thin vertical connector lines at the track ends hint spatial depth.
 */
export const ThreeLayerStack: FC<ThreeLayerStackProps> = ({layers, connectors}) => {
  const ys = layers.map((l) => l.y);
  const topY = Math.min(...ys);
  const botY = Math.max(...ys);
  return (
    <>
      {connectors.opacity > 0.005
        ? [L.trackLeft, L.trackLeft + L.trackW].map((cx, i) => (
            <div
              key={`connector-${i}`}
              style={{
                position: 'absolute',
                left: cx - 1,
                top: topY,
                width: 2,
                height: botY - topY,
                backgroundColor: C.connector,
                opacity: connectors.opacity,
                scale: `1 ${connectors.scaleY}`,
                transformOrigin: 'top',
              }}
            />
          ))
        : null}
      {layers.map((layer, i) => {
        const v = clamp(layer.value, 0, 100);
        const thumbX = L.trackLeft + (v / 100) * L.trackW;
        const fillW = (v / 100) * L.trackW;
        return (
          <div key={`layer-${i}`} style={{opacity: layer.opacity}}>
            <SliderTrack
              x={L.trackLeft}
              y={layer.y}
              width={L.trackW}
              height={L.trackH}
              color={layer.trackColor}
              growScaleX={layer.scale}
              fillWidth={fillW}
              fillOpacity={layer.fillOpacity}
              endpoints={{
                r: L.endpointR,
                color: layer.endpointColor,
                leftScale: layer.scale,
                rightScale: layer.scale,
              }}
            />
            <SliderThumb
              x={thumbX}
              y={layer.y}
              r={L.thumbR}
              color={C.thumb}
              opacity={layer.thumbOpacity}
              scaleX={layer.scale}
              scaleY={layer.scale}
              glow={layer.glow ?? 0}
            />
          </div>
        );
      })}
    </>
  );
};
