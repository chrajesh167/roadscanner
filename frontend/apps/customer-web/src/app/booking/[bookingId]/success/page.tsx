import type { Metadata } from 'next';
import { AuthGuard } from '@/components/layout/auth-guard';
import { BookingSuccessView } from './booking-success-view';

export const metadata: Metadata = { title: 'Booking confirmed' };

export default async function BookingSuccessPage({
  params,
}: {
  params: Promise<{ bookingId: string }>;
}) {
  const { bookingId } = await params;
  return (
    <AuthGuard>
      <BookingSuccessView bookingId={bookingId} />
    </AuthGuard>
  );
}
