import { Suspense } from 'react';
import type { Metadata } from 'next';
import { PageLoader } from '@/components/ui/feedback';
import { LoginView } from './login-view';

export const metadata: Metadata = { title: 'Sign in' };

export default function LoginPage() {
  // `useSearchParams` (for the post-login `next` redirect) requires a Suspense boundary.
  return (
    <Suspense fallback={<PageLoader />}>
      <LoginView />
    </Suspense>
  );
}
