'use client';

import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils/cn';

const buttonVariants = cva(
  [
    'relative inline-flex items-center justify-center gap-2 whitespace-nowrap font-medium',
    'transition-[background-color,border-color,color,box-shadow,transform] duration-200',
    'ease-[cubic-bezier(0.22,1,0.36,1)] select-none',
    'disabled:pointer-events-none disabled:opacity-45',
    'active:scale-[0.985]',
    '[&_svg]:pointer-events-none [&_svg]:shrink-0',
  ].join(' '),
  {
    variants: {
      variant: {
        primary:
          'bg-accent text-white shadow-[0_1px_0_rgba(255,255,255,0.14)_inset] hover:bg-accent-hover active:bg-accent-press hover:shadow-glow',
        secondary:
          'bg-elevated text-content border border-line-strong hover:bg-overlay hover:border-white/24',
        ghost: 'text-content-secondary hover:bg-white/[0.07] hover:text-content',
        outline:
          'border border-line-strong text-content hover:bg-white/[0.05] hover:border-white/24',
        danger: 'bg-danger/15 text-danger border border-danger/30 hover:bg-danger/25',
        link: 'text-accent underline-offset-4 hover:underline p-0 h-auto',
      },
      size: {
        sm: 'h-9 rounded-sm px-3.5 text-caption [&_svg]:size-4',
        md: 'h-11 rounded-md px-5 text-body [&_svg]:size-[18px]',
        lg: 'h-13 rounded-md px-7 text-[1rem] [&_svg]:size-5',
        icon: 'size-10 rounded-md [&_svg]:size-[18px]',
      },
      full: { true: 'w-full', false: '' },
    },
    defaultVariants: { variant: 'primary', size: 'md', full: false },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
  loading?: boolean;
  /** Shown next to the spinner while `loading`, replacing the label. */
  loadingText?: string;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant, size, full, asChild = false, loading = false, loadingText, children, disabled, ...props },
  ref,
) {
  const Comp = asChild ? Slot : 'button';

  // `asChild` forwards to a single child element, so the spinner swap only applies to real buttons.
  if (asChild) {
    return (
      <Comp className={cn(buttonVariants({ variant, size, full }), className)} ref={ref} {...props}>
        {children}
      </Comp>
    );
  }

  return (
    <button
      className={cn(buttonVariants({ variant, size, full }), className)}
      ref={ref}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? (
        <>
          <Loader2 className="animate-spin" aria-hidden />
          {loadingText ?? children}
        </>
      ) : (
        children
      )}
    </button>
  );
});

export { buttonVariants };
