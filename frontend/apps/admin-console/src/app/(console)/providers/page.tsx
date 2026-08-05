import type { Metadata } from 'next';
import { ProvidersView } from '@/features/providers/components/providers-view';

export const metadata: Metadata = { title: 'Providers' };

export default function ProvidersPage() {
  return <ProvidersView />;
}
