import type { Metadata } from 'next';
import { TripDetailView } from './trip-detail-view';

export const metadata: Metadata = { title: 'Trip details' };

export default async function TripDetailPage({
  params,
}: {
  params: Promise<{ tripId: string }>;
}) {
  const { tripId } = await params;
  return <TripDetailView tripId={tripId} />;
}
