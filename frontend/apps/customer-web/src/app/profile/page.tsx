import type { Metadata } from 'next';
import { AuthGuard } from '@/components/layout/auth-guard';
import { ProfileView } from './profile-view';

export const metadata: Metadata = { title: 'Profile' };

export default function ProfilePage() {
  return (
    <AuthGuard>
      <ProfileView />
    </AuthGuard>
  );
}
