import type { Metadata } from 'next';
import { ProviderDetailView } from '@/features/providers/components/provider-detail-view';

export const metadata: Metadata = { title: 'Provider' };

/** Next 15 hands route params in as a promise. */
export default async function ProviderDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <ProviderDetailView providerId={id} />;
}
