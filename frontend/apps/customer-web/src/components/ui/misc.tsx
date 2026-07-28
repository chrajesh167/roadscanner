'use client';

import * as React from 'react';
import * as SeparatorPrimitive from '@radix-ui/react-separator';
import * as SwitchPrimitive from '@radix-ui/react-switch';
import * as TooltipPrimitive from '@radix-ui/react-tooltip';
import * as AvatarPrimitive from '@radix-ui/react-avatar';
import { cn } from '@/lib/utils/cn';

// --- Separator ---------------------------------------------------------------

export const Separator = React.forwardRef<
  React.ComponentRef<typeof SeparatorPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof SeparatorPrimitive.Root>
>(function Separator({ className, orientation = 'horizontal', decorative = true, ...props }, ref) {
  return (
    <SeparatorPrimitive.Root
      ref={ref}
      decorative={decorative}
      orientation={orientation}
      className={cn(
        'shrink-0 bg-line',
        orientation === 'horizontal' ? 'h-px w-full' : 'h-full w-px',
        className,
      )}
      {...props}
    />
  );
});

// --- Switch ------------------------------------------------------------------

export const Switch = React.forwardRef<
  React.ComponentRef<typeof SwitchPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof SwitchPrimitive.Root>
>(function Switch({ className, ...props }, ref) {
  return (
    <SwitchPrimitive.Root
      ref={ref}
      className={cn(
        'peer inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full',
        'border border-line-strong transition-colors duration-200',
        'data-[state=checked]:bg-accent data-[state=checked]:border-accent',
        'data-[state=unchecked]:bg-white/[0.06]',
        'focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-accent-ring/25',
        'disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb
        className={cn(
          'pointer-events-none block size-4.5 rounded-full bg-white shadow-sm',
          'transition-transform duration-200 ease-[cubic-bezier(0.22,1,0.36,1)]',
          'data-[state=checked]:translate-x-[22px] data-[state=unchecked]:translate-x-[3px]',
        )}
      />
    </SwitchPrimitive.Root>
  );
});

// --- Tooltip -----------------------------------------------------------------

export const TooltipProvider = TooltipPrimitive.Provider;

export function Tooltip({
  children,
  content,
  side = 'top',
}: {
  children: React.ReactNode;
  content: React.ReactNode;
  side?: 'top' | 'right' | 'bottom' | 'left';
}) {
  return (
    <TooltipPrimitive.Root>
      <TooltipPrimitive.Trigger asChild>{children}</TooltipPrimitive.Trigger>
      <TooltipPrimitive.Portal>
        <TooltipPrimitive.Content
          side={side}
          sideOffset={8}
          className={cn(
            'pop-panel z-50 max-w-56 rounded-sm panel px-2.5 py-1.5',
            'text-caption text-content shadow-md',
          )}
        >
          {content}
        </TooltipPrimitive.Content>
      </TooltipPrimitive.Portal>
    </TooltipPrimitive.Root>
  );
}

// --- Avatar ------------------------------------------------------------------

export function Avatar({ name, className }: { name: string; className?: string }) {
  const initials = name
    .split(/[\s@._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('');

  return (
    <AvatarPrimitive.Root
      className={cn(
        'grid size-9 shrink-0 place-items-center overflow-hidden rounded-full',
        'bg-accent-soft border border-accent/25 text-caption font-semibold text-accent-text',
        className,
      )}
    >
      <AvatarPrimitive.Fallback>{initials || '?'}</AvatarPrimitive.Fallback>
    </AvatarPrimitive.Root>
  );
}

// --- Table -------------------------------------------------------------------

/**
 * A minimal table. Horizontal overflow is owned by the wrapper so a wide table scrolls inside its
 * own box instead of pushing the page sideways on mobile.
 */
export function Table({ className, ...props }: React.TableHTMLAttributes<HTMLTableElement>) {
  return (
    <div className="w-full overflow-x-auto">
      <table className={cn('w-full border-collapse text-body', className)} {...props} />
    </div>
  );
}

export function THead({ className, ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={cn('', className)} {...props} />;
}

export function TBody({ className, ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={cn('', className)} {...props} />;
}

export function TR({ className, ...props }: React.HTMLAttributes<HTMLTableRowElement>) {
  return <tr className={cn('border-b border-line last:border-0', className)} {...props} />;
}

export function TH({ className, ...props }: React.ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      className={cn(
        'px-4 py-3 text-left text-micro uppercase text-content-muted font-medium',
        className,
      )}
      {...props}
    />
  );
}

export function TD({ className, ...props }: React.TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn('px-4 py-3.5 align-middle text-content-secondary', className)} {...props} />;
}
