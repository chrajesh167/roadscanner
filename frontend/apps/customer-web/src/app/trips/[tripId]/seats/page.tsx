import type { Metadata } from 'next';
import { AuthGuard } from '@/components/layout/auth-guard';
import { SeatSelectionView } from './seat-selection-view';

export const metadata: Metadata = { title: 'Choose your seats' };

export default async function SeatSelectionPage({
  params,
}: {
  params: Promise<{ tripId: string }>;
}) {
  const { tripId } = await params;
  return (
    <AuthGuard>
      <SeatSelectionView tripId={tripId} />
    </AuthGuard>
  );
}
