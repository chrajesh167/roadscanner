'use client';

import { motion } from 'framer-motion';
import { Check } from 'lucide-react';
import { cn } from '@/lib/utils/cn';

const STEPS = ['Seats', 'Passengers', 'Payment', 'Confirmed'] as const;
export type FlowStep = (typeof STEPS)[number];

/** Progress rail across the four booking screens. Purely indicative — never navigable. */
export function FlowSteps({ current }: { current: FlowStep }) {
  const currentIndex = STEPS.indexOf(current);

  return (
    <nav aria-label="Booking progress" className="mb-8">
      <ol className="flex items-center gap-2 sm:gap-3">
        {STEPS.map((step, index) => {
          const done = index < currentIndex;
          const active = index === currentIndex;

          return (
            <li key={step} className="flex flex-1 items-center gap-2 sm:gap-3">
              <div className="flex items-center gap-2">
                <span
                  className={cn(
                    'grid size-6 shrink-0 place-items-center rounded-full border text-micro transition-colors duration-300',
                    done && 'border-accent bg-accent text-white',
                    active && 'border-accent bg-accent-soft text-accent-text',
                    !done && !active && 'border-line-strong text-content-muted',
                  )}
                >
                  {done ? <Check className="size-3" /> : index + 1}
                </span>
                <span
                  className={cn(
                    'hidden text-caption transition-colors duration-300 sm:inline',
                    active ? 'text-content' : 'text-content-muted',
                  )}
                >
                  {step}
                </span>
              </div>

              {index < STEPS.length - 1 && (
                <div className="h-px flex-1 overflow-hidden bg-line">
                  <motion.div
                    className="h-full bg-accent"
                    initial={{ scaleX: 0 }}
                    animate={{ scaleX: index < currentIndex ? 1 : 0 }}
                    style={{ originX: 0 }}
                    transition={{ duration: 0.45, ease: [0.22, 1, 0.36, 1] }}
                  />
                </div>
              )}
            </li>
          );
        })}
      </ol>
      <p className="sr-only">
        Step {currentIndex + 1} of {STEPS.length}: {current}
      </p>
    </nav>
  );
}
