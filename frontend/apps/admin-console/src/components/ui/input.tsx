'use client';

import * as React from 'react';
import * as LabelPrimitive from '@radix-ui/react-label';
import { cn } from '@/lib/utils/cn';

export const Label = React.forwardRef<
  React.ComponentRef<typeof LabelPrimitive.Root>,
  React.ComponentPropsWithoutRef<typeof LabelPrimitive.Root>
>(function Label({ className, ...props }, ref) {
  return (
    <LabelPrimitive.Root
      ref={ref}
      className={cn(
        'text-caption font-medium text-content-secondary',
        'peer-disabled:cursor-not-allowed peer-disabled:opacity-60',
        className,
      )}
      {...props}
    />
  );
});

const inputBase = [
  'w-full rounded-md bg-elevated border border-line px-3.5 text-body text-content',
  'placeholder:text-content-muted',
  'transition-[border-color,box-shadow,background-color] duration-200',
  'hover:border-line-strong',
  'focus:outline-none focus:border-accent focus:ring-4 focus:ring-accent-ring/25',
  'disabled:opacity-50 disabled:cursor-not-allowed',
].join(' ');

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
  /** Rendered inside the field, left of the text. */
  icon?: React.ReactNode;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(function Input(
  { className, invalid, icon, type = 'text', ...props },
  ref,
) {
  const field = (
    <input
      ref={ref}
      type={type}
      aria-invalid={invalid || undefined}
      className={cn(
        inputBase,
        'h-11',
        icon && 'pl-10',
        invalid && 'border-danger/60 focus:border-danger focus:ring-danger/20',
        className,
      )}
      {...props}
    />
  );

  if (!icon) return field;

  return (
    <div className="relative">
      <span
        className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-content-muted [&_svg]:size-4"
        aria-hidden
      >
        {icon}
      </span>
      {field}
    </div>
  );
});

export const Textarea = React.forwardRef<
  HTMLTextAreaElement,
  React.TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }
>(function Textarea({ className, invalid, ...props }, ref) {
  return (
    <textarea
      ref={ref}
      aria-invalid={invalid || undefined}
      className={cn(
        inputBase,
        'min-h-24 py-3 resize-y',
        invalid && 'border-danger/60 focus:border-danger focus:ring-danger/20',
        className,
      )}
      {...props}
    />
  );
});

/** Label + control + error, with the wiring (`id`, `aria-describedby`) done once. */
export function Field({
  label,
  htmlFor,
  error,
  hint,
  children,
  className,
}: {
  label: string;
  htmlFor: string;
  error?: string;
  hint?: string;
  children: React.ReactNode;
  className?: string;
}) {
  const describedBy = error ? `${htmlFor}-error` : hint ? `${htmlFor}-hint` : undefined;

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <Label htmlFor={htmlFor}>{label}</Label>
      <div aria-describedby={describedBy}>{children}</div>
      {error ? (
        <p id={`${htmlFor}-error`} role="alert" className="text-caption text-danger">
          {error}
        </p>
      ) : hint ? (
        <p id={`${htmlFor}-hint`} className="text-caption text-content-muted">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
