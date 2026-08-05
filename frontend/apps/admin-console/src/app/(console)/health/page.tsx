import type { Metadata } from 'next';
import { HealthView } from '@/features/health/components/health-view';

export const metadata: Metadata = { title: 'Health' };

export default function HealthPage() {
  return <HealthView />;
}
