import Link from 'next/link';
import { Armchair, ArrowRight, Gauge, ShieldCheck, Sparkles, Wallet } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { FadeIn, Reveal } from '@/components/ui/motion';
import { SearchForm } from '@/components/search/search-form';

const PILLARS = [
  {
    icon: Armchair,
    title: 'Seats you actually chose',
    body: 'A live deck map, not a guess. Pick your seat, hold it, and the countdown tells you exactly how long it is yours.',
  },
  {
    icon: Wallet,
    title: 'One fare, start to finish',
    body: 'The price on the results page is the price you pay. No fees discovered at the last step.',
  },
  {
    icon: ShieldCheck,
    title: 'Payments that never double',
    body: 'Every payment carries an idempotency key, so a flaky connection or an impatient tap can never charge you twice.',
  },
  {
    icon: Gauge,
    title: 'Fast, then out of the way',
    body: 'Search to confirmation in four screens. No interstitials, no upsells, no countdown theatre.',
  },
];

export default function LandingPage() {
  return (
    <>
      {/* ---- Hero ---- */}
      <section className="relative overflow-hidden">
        <div className="aurora pointer-events-none absolute inset-0 -top-32 h-[130%]" aria-hidden />
        <div
          className="pointer-events-none absolute inset-x-0 bottom-0 h-40 bg-gradient-to-t from-canvas to-transparent"
          aria-hidden
        />

        <div className="relative mx-auto max-w-5xl px-5 pb-16 pt-16 sm:px-8 sm:pt-24">
          <FadeIn>
            <Badge tone="accent" size="md" className="mb-7">
              <Sparkles />
              Phase 1 · Intercity bus travel
            </Badge>
          </FadeIn>

          <FadeIn delay={0.08}>
            <h1 className="max-w-3xl text-[2.75rem] font-semibold leading-[1.05] tracking-[-0.04em] sm:text-display">
              <span className="text-gradient">Travel, considered.</span>
            </h1>
          </FadeIn>

          <FadeIn delay={0.16}>
            <p className="mt-6 max-w-xl text-[1.0625rem] leading-relaxed text-content-secondary">
              Search every operator on your route, compare on what matters, and hold the seat you
              want — in a booking flow that respects your attention.
            </p>
          </FadeIn>

          <FadeIn delay={0.26} className="mt-10">
            <SearchForm />
          </FadeIn>

          <FadeIn delay={0.36}>
            <p className="mt-5 text-caption text-content-muted">
              Live availability from every connected operator. No account needed to search.
            </p>
          </FadeIn>
        </div>
      </section>

      {/* ---- Pillars ---- */}
      <section className="mx-auto max-w-6xl px-5 py-20 sm:px-8 sm:py-28">
        <Reveal>
          <h2 className="max-w-2xl text-h2">
            Built like infrastructure.
            <span className="text-content-muted"> Finished like a product.</span>
          </h2>
        </Reveal>

        <div className="mt-12 grid gap-4 sm:grid-cols-2">
          {PILLARS.map((pillar, index) => (
            <Reveal key={pillar.title} delay={index * 0.07}>
              <Card variant="solid" padding="lg" className="h-full">
                <div className="mb-5 grid size-11 place-items-center rounded-md border border-accent/20 bg-accent-soft">
                  <pillar.icon className="size-5 text-[#b9aaff]" aria-hidden />
                </div>
                <h3 className="text-h3">{pillar.title}</h3>
                <p className="mt-2.5 text-body text-content-secondary">{pillar.body}</p>
              </Card>
            </Reveal>
          ))}
        </div>
      </section>

      {/* ---- How it works ---- */}
      <section className="mx-auto max-w-6xl px-5 pb-20 sm:px-8 sm:pb-28">
        <Reveal>
          <Card variant="elevated" padding="none" className="overflow-hidden">
            <div className="aurora relative px-6 py-14 sm:px-12 sm:py-16">
              <div className="relative">
                <h2 className="max-w-lg text-h2">Four screens. That&apos;s the whole journey.</h2>

                <ol className="mt-10 grid gap-8 sm:grid-cols-4 sm:gap-6">
                  {[
                    ['Search', 'Route and date. Filter by price, time or rating.'],
                    ['Choose', 'Pick seats from a live deck map and hold them.'],
                    ['Details', 'Who is travelling, one passenger per seat.'],
                    ['Pay', 'Confirm and go. Your ticket lands in My bookings.'],
                  ].map(([title, body], index) => (
                    <li key={title} className="flex flex-col gap-2">
                      <span className="text-micro uppercase text-accent">
                        {String(index + 1).padStart(2, '0')}
                      </span>
                      <span className="text-[1rem] font-medium text-content">{title}</span>
                      <span className="text-caption text-content-secondary">{body}</span>
                    </li>
                  ))}
                </ol>

                <div className="mt-12 flex flex-wrap gap-3">
                  <Button size="lg" asChild>
                    <Link href="/search">
                      Start searching
                      <ArrowRight />
                    </Link>
                  </Button>
                  <Button size="lg" variant="secondary" asChild>
                    <Link href="/register">Create an account</Link>
                  </Button>
                </div>
              </div>
            </div>
          </Card>
        </Reveal>
      </section>
    </>
  );
}
