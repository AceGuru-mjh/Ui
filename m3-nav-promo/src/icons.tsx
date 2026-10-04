import type {FC} from 'react';

/**
 * Canonical Material Symbols-style icon paths (24x24 viewBox).
 * A same-color hairline stroke with round joins softens the vertices,
 * approximating the Material Symbols "rounded" contour.
 */
export type IconName =
  | 'home'
  | 'search'
  | 'plus'
  | 'heart'
  | 'person'
  | 'star'
  | 'mail'
  | 'play'
  | 'chat';

const PATHS: Record<IconName, string> = {
  home: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z',
  search:
    'M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  plus: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  heart:
    'M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z',
  person:
    'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z',
  star:
    'M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z',
  mail: 'M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z',
  play: 'M8 5v14l11-7z',
  chat:
    'M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z',
};

export const Icon: FC<{
  name: IconName;
  size: number;
  color: string;
  opacity?: number;
}> = ({name, size, color, opacity = 1}) => (
  <svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    style={{
      position: 'absolute',
      left: '50%',
      top: '50%',
      translate: '-50% -50%',
      fill: color,
      stroke: color,
      strokeWidth: 0.8,
      strokeLinejoin: 'round',
      strokeLinecap: 'round',
      opacity,
    }}
  >
    <path d={PATHS[name]} />
  </svg>
);
