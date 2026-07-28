import type { Metadata } from 'next';
import { AuthGuard } from '@/components/layout/auth-guard';
import { BookingDetailView } from './booking-detail-view';

export const metadata: Metadata = { title: 'Booking details' };

export default async function BookingDetailPage({
  params,
}: {
  params: Promise<{ bookingId: string }>;
}) {
  const { bookingId } = await params;
  return (
    <AuthGuard>
      <BookingDetailView bookingId={bookingId} />
    </AuthGuard>
  );
}
