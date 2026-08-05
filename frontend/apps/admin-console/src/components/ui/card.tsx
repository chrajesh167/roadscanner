import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils/cn';

const cardVariants = cva(
  'rounded-lg transition-[background-color,border-color,box-shadow,transform] duration-200 ease-[cubic-bezier(0.22,1,0.36,1)]',
  {
  variants: {
    variant: {
      /** Default surface — the workhorse container. */
      solid: 'bg-surface border border-line',
      /** One step up, for cards that sit on top of another surface. */
      elevated: 'bg-elevated border border-line shadow-md',
      /** Frosted; reserve for overlays and floating bars where content shows through. */
      glass: 'glass',
      /** No fill — grouping only. */
      ghost: 'border border-line',
    },
    interactive: {
      // Lifts slightly and warms its border on hover — enough to read as clickable without the
      // whole list jittering as the pointer crosses it.
      true: 'cursor-pointer hover:border-accent/35 hover:bg-elevated hover:shadow-md focus-within:border-accent/50 hover:-translate-y-0.5',
      false: '',
    },
    padding: {
      none: '',
      sm: 'p-4 sm:p-5',
      md: 'p-5 sm:p-6',
      lg: 'p-6 sm:p-8',
    },
  },
    defaultVariants: { variant: 'solid', interactive: false, padding: 'md' },
  },
);

export interface CardProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof cardVariants> {}

export const Card = React.forwardRef<HTMLDivElement, CardProps>(function Card(
  { className, variant, interactive, padding, ...props },
  ref,
) {
  return (
    <div ref={ref} className={cn(cardVariants({ variant, interactive, padding }), className)} {...props} />
  );
});

export function CardHeader({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex flex-col gap-1.5', className)} {...props} />;
}

export function CardTitle({ className, ...props }: React.HTMLAttributes<HTMLHeadingElement>) {
  return <h3 className={cn('text-h3 text-content', className)} {...props} />;
}

export function CardDescription({ className, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn('text-caption text-content-secondary', className)} {...props} />;
}

export function CardContent({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('', className)} {...props} />;
}

export function CardFooter({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex items-center gap-3', className)} {...props} />;
}
