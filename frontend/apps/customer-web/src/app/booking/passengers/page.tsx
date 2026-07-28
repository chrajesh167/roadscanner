import type { Metadata } from 'next';
import { AuthGuard } from '@/components/layout/auth-guard';
import { PassengerDetailsView } from './passenger-details-view';

export const metadata: Metadata = { title: 'Passenger details' };

export default function PassengerDetailsPage() {
  return (
    <AuthGuard>
      <PassengerDetailsView />
    </AuthGuard>
  );
}
