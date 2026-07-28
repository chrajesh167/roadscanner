import { Suspense } from 'react';
import type { Metadata } from 'next';
import { PageLoader } from '@/components/ui/feedback';
import { ResultsView } from './results-view';

export const metadata: Metadata = { title: 'Search results' };

export default function SearchResultsPage() {
  return (
    <Suspense fallback={<PageLoader label="Loading results" />}>
      <ResultsView />
    </Suspense>
  );
}
