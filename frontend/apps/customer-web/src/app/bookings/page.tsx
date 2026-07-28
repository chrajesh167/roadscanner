import type { Metadata } from 'next';
import { AuthGuard } from '@/components/layout/auth-guard';
import { BookingsView } from './bookings-view';

export const metadata: Metadata = { title: 'My bookings' };

export default function BookingsPage() {
  return (
    <AuthGuard>
      <BookingsView />
    </AuthGuard>
  );
}
