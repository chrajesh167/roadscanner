import type { Metadata } from 'next';
import { AuthGuard } from '@/components/layout/auth-guard';
import { SettingsView } from './settings-view';

export const metadata: Metadata = { title: 'Settings' };

export default function SettingsPage() {
  return (
    <AuthGuard>
      <SettingsView />
    </AuthGuard>
  );
}
