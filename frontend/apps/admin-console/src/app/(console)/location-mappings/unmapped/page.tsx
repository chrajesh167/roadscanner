import type { Metadata } from 'next';
import { UnmappedLocationsPage } from '@/features/provider-mappings/pages/unmapped-locations-page';

export const metadata: Metadata = { title: 'Unmapped locations' };

export default function UnmappedLocationsRoute() {
  return <UnmappedLocationsPage />;
}
