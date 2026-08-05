'use client';

import * as React from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Eye, EyeOff, LogIn } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Field, Input } from '@/components/ui/input';
import { PageLoader } from '@/components/ui/feedback';
import { Logo } from '@/components/layout/logo';
import { ApiError } from '@/lib/api/client';
import { useLogin, useSession } from '@/lib/hooks/use-auth';

const loginSchema = z.object({
  identifier: z.string().trim().min(1, 'Enter your email or phone number'),
  password: z.string().min(1, 'Enter your password'),
});

type LoginValues = z.infer<typeof loginSchema>;

export function LoginView() {
  return (
    <React.Suspense fallback={<PageLoader label="Loading" />}>
      <LoginForm />
    </React.Suspense>
  );
}

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const login = useLogin();
  const { session, hydrated } = useSession();
  const [revealed, setRevealed] = React.useState(false);

  const next = searchParams.get('next');
  const destination = next && next.startsWith('/') ? next : '/dashboard';

  // An already-signed-in admin who lands here is sent on rather than made to sign in twice.
  React.useEffect(() => {
    if (hydrated && session !== null) router.replace(destination);
  }, [hydrated, session, destination, router]);

  const form = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { identifier: '', password: '' },
  });

  const onSubmit = form.handleSubmit((values) => {
    login.mutate(values, {
      onSuccess: () => router.replace(destination),
    });
  });

  const failure = login.error instanceof ApiError ? login.error : null;

  return (
    <div className="grid min-h-dvh place-items-center px-5 py-12">
      <div className="aurora pointer-events-none fixed inset-0 -z-10" aria-hidden />

      <div className="w-full max-w-md">
        <div className="mb-8 flex justify-center">
          <Logo />
        </div>

        <Card variant="elevated" padding="lg">
          <div className="mb-6 flex flex-col gap-2">
            <h1 className="text-h2">Sign in</h1>
            <p className="text-caption text-content-secondary">
              The provider registry requires an administrator account.
            </p>
          </div>

          <form onSubmit={onSubmit} className="flex flex-col gap-5" noValidate>
            <Field
              label="Email or phone"
              htmlFor="identifier"
              error={form.formState.errors.identifier?.message}
            >
              <Input
                id="identifier"
                autoComplete="username"
                autoFocus
                invalid={Boolean(form.formState.errors.identifier)}
                {...form.register('identifier')}
              />
            </Field>

            <Field
              label="Password"
              htmlFor="password"
              error={form.formState.errors.password?.message}
            >
              <div className="relative">
                <Input
                  id="password"
                  type={revealed ? 'text' : 'password'}
                  autoComplete="current-password"
                  className="pr-11"
                  invalid={Boolean(form.formState.errors.password)}
                  {...form.register('password')}
                />
                <button
                  type="button"
                  onClick={() => setRevealed((current) => !current)}
                  aria-label={revealed ? 'Hide password' : 'Show password'}
                  className="absolute right-3 top-1/2 grid size-7 -translate-y-1/2 place-items-center rounded-sm text-content-muted transition-colors hover:text-content"
                >
                  {revealed ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
            </Field>

            {failure && (
              <div
                role="alert"
                className="rounded-md border border-danger/25 bg-danger-soft px-4 py-3 text-caption text-danger"
              >
                {failure.message}
                {failure.correlationId && (
                  <span className="mt-1 block font-mono text-micro opacity-80">
                    Reference {failure.correlationId}
                  </span>
                )}
              </div>
            )}

            <Button type="submit" full loading={login.isPending} loadingText="Signing in…">
              <LogIn />
              Sign in
            </Button>
          </form>
        </Card>

        <p className="mt-6 text-center text-caption text-content-muted">
          Administrator access is granted through auth-service by another administrator.
        </p>
      </div>
    </div>
  );
}
