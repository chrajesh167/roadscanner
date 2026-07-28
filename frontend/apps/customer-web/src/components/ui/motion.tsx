'use client';

import * as React from 'react';
import { motion, type Variants } from 'framer-motion';
import { usePrefersReducedMotion } from '@/lib/hooks/use-reduced-motion';
import { cn } from '@/lib/utils/cn';

/**
 * Shared motion vocabulary. Three gestures, used everywhere:
 *
 *   Reveal  — content arriving (page sections, cards)
 *   Stagger — a list arriving as a sequence rather than a block
 *   Press   — tactile feedback on a tappable surface
 *
 * Every one collapses to a plain fade (or nothing) under `prefers-reduced-motion`.
 */

const EASE = [0.22, 1, 0.36, 1] as const;

export function Reveal({
  children,
  delay = 0,
  y = 14,
  className,
  once = true,
}: {
  children: React.ReactNode;
  delay?: number;
  y?: number;
  className?: string;
  once?: boolean;
}) {
  const reduced = usePrefersReducedMotion();
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: reduced ? 0 : y }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once, margin: '-80px' }}
      transition={{ duration: 0.55, delay, ease: EASE }}
    >
      {children}
    </motion.div>
  );
}

/** Same as Reveal but fires on mount instead of on scroll — for above-the-fold content. */
export function FadeIn({
  children,
  delay = 0,
  y = 10,
  className,
}: {
  children: React.ReactNode;
  delay?: number;
  y?: number;
  className?: string;
}) {
  const reduced = usePrefersReducedMotion();
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: reduced ? 0 : y }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay, ease: EASE }}
    >
      {children}
    </motion.div>
  );
}

export const staggerContainer: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.055, delayChildren: 0.04 } },
};

export const staggerItem: Variants = {
  hidden: { opacity: 0, y: 12 },
  show: { opacity: 1, y: 0, transition: { duration: 0.45, ease: EASE } },
};

export function Stagger({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <motion.div variants={staggerContainer} initial="hidden" animate="show" className={className}>
      {children}
    </motion.div>
  );
}

export function StaggerItem({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <motion.div variants={staggerItem} className={className}>
      {children}
    </motion.div>
  );
}

/** A tappable surface with a subtle press. Used for seats, result rows, method tiles. */
export function Pressable({
  children,
  className,
  onClick,
  disabled,
  ariaLabel,
  ariaPressed,
}: {
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
  disabled?: boolean;
  ariaLabel?: string;
  ariaPressed?: boolean;
}) {
  const reduced = usePrefersReducedMotion();
  return (
    <motion.button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={ariaLabel}
      aria-pressed={ariaPressed}
      whileTap={reduced || disabled ? undefined : { scale: 0.97 }}
      transition={{ duration: 0.12 }}
      className={cn('disabled:cursor-not-allowed', className)}
    >
      {children}
    </motion.button>
  );
}
