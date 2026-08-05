import type { Metadata } from 'next';
import { ProviderMappingsPage } from '@/features/provider-mappings/pages/provider-mappings-page';

export const metadata: Metadata = { title: 'Location mappings' };

export default function LocationMappingsRoute() {
  return <ProviderMappingsPage />;
}
