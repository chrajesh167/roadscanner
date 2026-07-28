import { Suspense } from 'react';
import type { Metadata } from 'next';
import { PageLoader } from '@/components/ui/feedback';
import { RegisterView } from './register-view';

export const metadata: Metadata = { title: 'Create an account' };

export default function RegisterPage() {
  return (
    <Suspense fallback={<PageLoader />}>
      <RegisterView />
    </Suspense>
  );
}
