import * as React from 'react';
import Link from 'next/link';
import { ChevronLeft } from 'lucide-react';
import { FadeIn } from '@/components/ui/motion';
import { cn } from '@/lib/utils/cn';

/** The standard page frame: constrained width, generous top space, optional title block. */
export function PageShell({
  title,
  description,
  backHref,
  backLabel = 'Back',
  actions,
  children,
  width = 'default',
  className,
}: {
  title?: string;
  description?: React.ReactNode;
  backHref?: string;
  backLabel?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
  width?: 'default' | 'narrow' | 'wide';
  className?: string;
}) {
  const maxWidth =
    width === 'narrow' ? 'max-w-xl' : width === 'wide' ? 'max-w-6xl' : 'max-w-4xl';

  return (
    <div className={cn('mx-auto px-5 pb-20 pt-10 sm:px-8 sm:pt-14', maxWidth, className)}>
      {(title || backHref) && (
        <FadeIn className="mb-8 flex flex-col gap-4">
          {backHref && (
            <Link
              href={backHref}
              className="inline-flex w-fit items-center gap-1 text-caption text-content-secondary transition-colors hover:text-content"
            >
              <ChevronLeft className="size-4" />
              {backLabel}
            </Link>
          )}
          {title && (
            <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
              <div className="flex flex-col gap-2">
                <h1 className="text-h1">{title}</h1>
                {description && (
                  <p className="max-w-2xl text-body text-content-secondary">{description}</p>
                )}
              </div>
              {actions && <div className="flex shrink-0 items-center gap-3">{actions}</div>}
            </div>
          )}
        </FadeIn>
      )}
      {children}
    </div>
  );
}
