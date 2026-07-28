import type { Metadata } from 'next';
import { Clock, TrendingUp } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { FadeIn } from '@/components/ui/motion';
import { PageShell } from '@/components/layout/page-shell';
import { SearchForm } from '@/components/search/search-form';

export const metadata: Metadata = {
  title: 'Search trips',
  description: 'Search intercity trips by route and date.',
};

const TIPS = [
  {
    icon: Clock,
    title: 'Times are local to the stop',
    body: 'Departure and arrival are shown in the local time of each end of the route.',
  },
  {
    icon: TrendingUp,
    title: 'Sort before you filter',
    body: 'Sorting by departure or duration usually narrows a busy route faster than a price filter.',
  },
];

export default function SearchPage() {
  return (
    <PageShell
      title="Where are you going?"
      description="Pick a route and a date. You can refine the results once they load."
    >
      <FadeIn delay={0.08}>
        <SearchForm variant="compact" />
      </FadeIn>

      <div className="mt-10 grid gap-4 sm:grid-cols-2">
        {TIPS.map((tip, index) => (
          <FadeIn key={tip.title} delay={0.16 + index * 0.06}>
            <Card variant="ghost" padding="md" className="h-full">
              <tip.icon className="mb-3 size-4 text-content-muted" aria-hidden />
              <p className="text-[0.9375rem] font-medium text-content">{tip.title}</p>
              <p className="mt-1.5 text-caption text-content-secondary">{tip.body}</p>
            </Card>
          </FadeIn>
        ))}
      </div>
    </PageShell>
  );
}
