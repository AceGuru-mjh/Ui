import type {FC} from 'react';
import {spring} from 'remotion';
import {L, M3, FONT} from '../constants';
import {clamp, seg, wobble} from '../util';

/** Assembly beats (scene-1 local frames @60fps). */
const B = {
  track: 8, // track block starts dropping
  fill: 44, // active fill block snaps in
  thumb: 78, // thumb block drops
  dot: 116, // stop-indicator dot scales in
  label: 100, // OPACITY label fades up
  tooltip: 132, // value indicator pops
  thumbLand: 100, // first touch of the thumb (squash + jiggle)
} as const;

export type M3BlockSliderProps = {
  /** 0..100 */
  value: number;
  frame: number;
  fps: number;
};

/**
 * Material 3 baseline-light slider assembled from snap-in building blocks:
 *  inactive track block -> primary fill block -> thumb block (with stop
 *  indicator dot) -> OPACITY label -> M3 value-indicator tooltip.
 * Every arrival uses spring drop + squash, and the thumb's landing makes
 * the already-placed pieces jiggle - a "block snap" feel, all frame-driven.
 */
export const M3BlockSlider: FC<M3BlockSliderProps> = ({value, frame: f, fps}) => {
  const clamped = clamp(value, 0, 100);
  const thumbX = L.trackLeft + (clamped / 100) * L.trackW;
  const fillW = thumbX - L.trackLeft;

  // --- track block: drop from above with overshoot ---
  const trackDrop = spring({
    frame: Math.max(0, f - B.track),
    fps,
    from: -170,
    to: 0,
    config: {damping: 11, stiffness: 150, mass: 1},
  });
  const trackLand = B.track + 24;
  const trackSquash = 1 + wobble(f, trackLand, -0.07, 7, 1.1);

  // --- fill block: snaps in from the left ---
  const fillP = seg(f, B.fill, B.fill + 26, 0, 1);
  const fillJiggle = wobble(f, B.thumbLand, 2.2, 6, 1.2);

  // --- thumb block: drop with bounce + squash on landing ---
  const thumbDrop = spring({
    frame: Math.max(0, f - B.thumb),
    fps,
    from: -190,
    to: 0,
    config: {damping: 10, stiffness: 140, mass: 1},
  });
  const thumbSquashY = 1 + wobble(f, B.thumbLand, -0.16, 7, 1.15);
  const thumbSquashX = 1 + wobble(f, B.thumbLand, 0.11, 7, 1.15);
  const trackJiggle = wobble(f, B.thumbLand, 2.6, 6, 1.2);

  const dotP = seg(f, B.dot, B.dot + 14, 0, 1);
  const labelOp = seg(f, B.label, B.label + 22, 0, 1);
  const labelRise = seg(f, B.label, B.label + 22, 14, 0);
  const tipOp = seg(f, B.tooltip, B.tooltip + 16, 0, 1);

  return (
    <>
      {/* inactive track block */}
      <div
        style={{
          position: 'absolute',
          left: L.trackLeft,
          top: L.stageY - L.trackH / 2 + trackJiggle,
          width: L.trackW,
          height: L.trackH,
          borderRadius: L.trackR,
          backgroundColor: M3.surfaceContainerHighest,
          opacity: f >= B.track ? 1 : 0,
          translateY: trackDrop,
          scaleY: `${trackSquash}`,
          transformOrigin: '50% 100%',
          boxShadow: '0 1px 0 rgba(255,255,255,0.9) inset',
        }}
      />
      {/* active fill block (primary) */}
      <div
        style={{
          position: 'absolute',
          left: L.trackLeft,
          top: L.stageY - L.trackH / 2 + trackJiggle + fillJiggle,
          width: Math.max(0, fillW * fillP),
          height: L.trackH,
          borderRadius: L.trackR,
          backgroundColor: M3.primary,
          opacity: f >= B.fill ? 1 : 0,
        }}
      />
      {/* OPACITY label */}
      <div
        style={{
          position: 'absolute',
          left: L.stageX,
          top: L.stageY - 92 + labelRise,
          translate: '-50% -50%',
          fontFamily: FONT,
          fontSize: L.labelFont,
          letterSpacing: 9,
          fontWeight: 600,
          color: '#49454F',
          opacity: labelOp,
          whiteSpace: 'nowrap',
        }}
      >
        OPACITY
      </div>
      {/* thumb block */}
      <div
        style={{
          position: 'absolute',
          left: thumbX - L.thumbR,
          top: L.stageY - L.thumbR,
          width: L.thumbR * 2,
          height: L.thumbR * 2,
          opacity: f >= B.thumb ? 1 : 0,
          translateY: thumbDrop,
          scaleX: `${thumbSquashX}`,
          scaleY: `${thumbSquashY}`,
        }}
      >
        <div
          style={{
            position: 'absolute',
            inset: 0,
            borderRadius: '50%',
            backgroundColor: M3.primary,
            boxShadow: `0 ${6}px ${14}px rgba(103, 80, 164, 0.35)`,
          }}
        />
        {/* M3 stop-indicator dot */}
        <div
          style={{
            position: 'absolute',
            left: '50%',
            top: '50%',
            width: L.stopDotR * 2 * dotP,
            height: L.stopDotR * 2 * dotP,
            translate: '-50% -50%',
            borderRadius: '50%',
            backgroundColor: M3.onPrimary,
            opacity: dotP,
          }}
        />
      </div>
      {/* M3 value-indicator tooltip */}
      <div
        style={{
          position: 'absolute',
          left: thumbX,
          top: L.stageY - L.thumbR - L.tooltipGap,
          translate: '-50% -100%',
          fontFamily: FONT,
          fontSize: L.tooltipFont,
          fontWeight: 600,
          color: M3.inverseOnSurface,
          backgroundColor: M3.inverseSurface,
          padding: '8px 18px',
          borderRadius: 14,
          opacity: tipOp,
          whiteSpace: 'nowrap',
        }}
      >
        {`${Math.round(clamped)}%`}
      </div>
    </>
  );
};
