import type {FC} from 'react';
import {M3} from '../constants';
import {Icon, type IconName} from '../icons';
import {clamp01} from '../util';

/**
 * Flat Material 3 circular nav item: tonal surface fill + hairline outline +
 * Material Symbol icon. Selection cross-fades the fill to a container color
 * (primary for the outer ring, secondary for the inner ring).
 */
export type NavItemProps = {
  x: number;
  y: number;
  r: number;
  icon: IconName;
  iconSize: number;
  /** 0 = idle, 1 = selected (fill/icon cross-fade) */
  selected: number;
  /** 0..1 pop-in progress (spring) */
  appear: number;
  /** radial breathing / ripple extra scale */
  ripple?: number;
  /** tonal level: high (outer ring) or container (inner ring) */
  level?: 'high' | 'container';
  opacity?: number;
};

export const NavItem: FC<NavItemProps> = ({
  x,
  y,
  r,
  icon,
  iconSize,
  selected,
  appear,
  ripple = 0,
  level = 'high',
  opacity = 1,
}) => {
  const w = clamp01(selected);
  const baseFill =
    level === 'high' ? M3.surfaceContainerHigh : M3.surfaceContainer;
  const container =
    level === 'high' ? M3.primaryContainer : M3.secondaryContainer;
  const onContainer =
    level === 'high' ? M3.onPrimaryContainer : M3.onSecondaryContainer;

  // blend the tonal fill toward the container color when selected
  const fill = w > 0 ? mix(baseFill, container, w) : baseFill;
  const iconColor = w > 0 ? mix(M3.onSurfaceVariant, onContainer, w) : M3.onSurfaceVariant;
  const iconScale = 0.94 + 0.18 * w;

  if (appear <= 0.001) return null;
  const s = appear * (1 + ripple);

  return (
    <div
      style={{
        position: 'absolute',
        left: x - r,
        top: y - r,
        width: r * 2,
        height: r * 2,
        scale: `${s}`,
        opacity: opacity * clamp01(appear * 1.6),
      }}
    >
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: '50%',
          backgroundColor: fill,
          border: `1.5px solid ${M3.outlineVariant}`,
        }}
      />
      <div
        style={{
          position: 'absolute',
          inset: 0,
          scale: `${iconScale}`,
        }}
      >
        <Icon name={icon} size={iconSize} color={iconColor} />
      </div>
    </div>
  );
};

/** Hex color blend helper (a=0 -> c1, a=1 -> c2). */
export const mix = (c1: string, c2: string, a: number): string => {
  const p = clamp01(a);
  const h = (c: string) => [
    parseInt(c.slice(1, 3), 16),
    parseInt(c.slice(3, 5), 16),
    parseInt(c.slice(5, 7), 16),
  ];
  const [r1, g1, b1] = h(c1);
  const [r2, g2, b2] = h(c2);
  const ch = (v: number) =>
    Math.round(v)
      .toString(16)
      .padStart(2, '0');
  return `#${ch(r1 + (r2 - r1) * p)}${ch(g1 + (g2 - g1) * p)}${ch(b1 + (b2 - b1) * p)}`;
};
