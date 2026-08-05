import type { Metadata } from 'next';
import { CredentialsView } from '@/features/credentials/components/credentials-view';

export const metadata: Metadata = { title: 'Credentials' };

export default function CredentialsPage() {
  return <CredentialsView />;
}
