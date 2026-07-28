import type { Metadata } from 'next';
import { AuthGuard } from '@/components/layout/auth-guard';
import { PaymentView } from './payment-view';

export const metadata: Metadata = { title: 'Payment' };

export default async function PaymentPage({
  params,
}: {
  params: Promise<{ bookingId: string }>;
}) {
  const { bookingId } = await params;
  return (
    <AuthGuard>
      <PaymentView bookingId={bookingId} />
    </AuthGuard>
  );
}
