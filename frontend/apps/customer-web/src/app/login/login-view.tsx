'use client';

import * as React from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { AtSign, Eye, EyeOff, Lock } from 'lucide-react';
import { AuthLayout } from '@/components/auth/auth-layout';
import { Button } from '@/components/ui/button';
import { Field, Input } from '@/components/ui/input';
import { ApiError } from '@/lib/api/client';
import { useLogin, useSession } from '@/lib/hooks/use-auth';
import { loginSchema, type LoginValues } from '@/lib/validation/schemas';

const HIGHLIGHTS = [
  'Hold seats while you decide who is travelling.',
  'Every booking and ticket in one place.',
  'Cancel from your booking history without a phone call.',
];

export function LoginView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const login = useLogin();
  const { session, hydrated } = useSession();
  const [showPassword, setShowPassword] = React.useState(false);

  const next = searchParams.get('next');
  const destination = next ? decodeURIComponent(next) : '/search';

  // Already signed in — don't show a login form the user can't meaningfully use.
  React.useEffect(() => {
    if (hydrated && session) router.replace(destination);
  }, [hydrated, session, destination, router]);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { identifier: '', password: '' },
  });

  function onSubmit(values: LoginValues) {
    login.mutate(values, {
      onSuccess: () => {
        toast.success('Welcome back');
        router.replace(destination);
      },
      onError: (error) => {
        // Field-level errors from the backend take precedence over a generic toast.
        if (error instanceof ApiError && error.fieldErrors.length > 0) {
          for (const fieldError of error.fieldErrors) {
            if (fieldError.field === 'identifier' || fieldError.field === 'password') {
              setError(fieldError.field, { message: fieldError.message });
            }
          }
          return;
        }
        const message =
          error instanceof ApiError && error.status === 401
            ? 'That email or password is not right.'
            : error instanceof Error
              ? error.message
              : 'Something went wrong.';
        toast.error('Could not sign in', { description: message });
      },
    });
  }

  return (
    <AuthLayout
      title="Welcome back"
      subtitle="Sign in to hold seats, review bookings and manage your trips."
      highlights={HIGHLIGHTS}
      footer={
        <>
          New to RoadScanner?{' '}
          <Link href="/register" className="text-accent hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5" noValidate>
        <Field label="Email or phone" htmlFor="identifier" error={errors.identifier?.message}>
          <Input
            id="identifier"
            autoComplete="username"
            inputMode="email"
            placeholder="you@example.com"
            icon={<AtSign />}
            invalid={Boolean(errors.identifier)}
            {...register('identifier')}
          />
        </Field>

        <Field label="Password" htmlFor="password" error={errors.password?.message}>
          <div className="relative">
            <Input
              id="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="••••••••"
              icon={<Lock />}
              invalid={Boolean(errors.password)}
              className="pr-11"
              {...register('password')}
            />
            <button
              type="button"
              onClick={() => setShowPassword((visible) => !visible)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              className="absolute right-3 top-1/2 -translate-y-1/2 rounded-xs p-1 text-content-muted transition-colors hover:text-content"
            >
              {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
            </button>
          </div>
        </Field>

        <Button type="submit" size="lg" full loading={login.isPending} loadingText="Signing in">
          Sign in
        </Button>
      </form>
    </AuthLayout>
  );
}
