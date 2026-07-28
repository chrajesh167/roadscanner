import Link from 'next/link';
import { Check } from 'lucide-react';
import { FadeIn } from '@/components/ui/motion';

/**
 * Split layout for sign-in and registration: the form on the left, a quiet reassurance panel on
 * the right that collapses away entirely below `lg` so the mobile view is form-only.
 */
export function AuthLayout({
  title,
  subtitle,
  children,
  footer,
  highlights,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer: React.ReactNode;
  highlights: string[];
}) {
  return (
    <div className="relative overflow-hidden">
      <div className="aurora pointer-events-none absolute inset-0 opacity-60" aria-hidden />

      <div className="relative mx-auto grid max-w-6xl gap-16 px-5 py-14 sm:px-8 sm:py-20 lg:grid-cols-[minmax(0,1fr)_minmax(0,0.85fr)] lg:items-center">
        <FadeIn className="mx-auto w-full max-w-md lg:mx-0">
          <h1 className="text-h1">{title}</h1>
          <p className="mt-3 text-body text-content-secondary">{subtitle}</p>

          <div className="mt-9">{children}</div>

          <div className="mt-8 text-caption text-content-secondary">{footer}</div>
        </FadeIn>

        <FadeIn delay={0.12} className="hidden lg:block">
          <div className="rounded-xl glass p-9">
            <p className="text-micro uppercase text-content-muted">Why an account</p>
            <ul className="mt-6 flex flex-col gap-5">
              {highlights.map((highlight) => (
                <li key={highlight} className="flex gap-3.5">
                  <span className="mt-0.5 grid size-5 shrink-0 place-items-center rounded-full bg-accent-soft border border-accent/25">
                    <Check className="size-3 text-[#b9aaff]" aria-hidden />
                  </span>
                  <span className="text-body text-content-secondary">{highlight}</span>
                </li>
              ))}
            </ul>

            <div className="rule-fade my-8" />

            <p className="text-caption text-content-muted">
              Searching is open to everyone. An account is only needed once you want to hold a seat.{' '}
              <Link href="/search" className="text-accent hover:underline">
                Browse trips first
              </Link>
              .
            </p>
          </div>
        </FadeIn>
      </div>
    </div>
  );
}
