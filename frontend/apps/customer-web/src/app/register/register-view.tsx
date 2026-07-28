'use client';

import * as React from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { AtSign, Check, Eye, EyeOff, Lock } from 'lucide-react';
import { AuthLayout } from '@/components/auth/auth-layout';
import { Button } from '@/components/ui/button';
import { Field, Input } from '@/components/ui/input';
import { ApiError } from '@/lib/api/client';
import { useRegister, useSession } from '@/lib/hooks/use-auth';
import { registerSchema, type RegisterValues } from '@/lib/validation/schemas';
import { cn } from '@/lib/utils/cn';

const HIGHLIGHTS = [
  'Registration signs you straight in — no email round-trip.',
  'Seats you hold stay yours for the length of the hold window.',
  'Your bookings, tickets and cancellations live in one history.',
];

// Kept in step with auth-service's PasswordComplexityPolicy — see the schema for why there is
// no uppercase rule here.
const RULES = [
  { label: 'At least 12 characters', test: (value: string) => value.length >= 12 },
  { label: 'A letter', test: (value: string) => /[a-zA-Z]/.test(value) },
  { label: 'A number', test: (value: string) => /[0-9]/.test(value) },
];

export function RegisterView() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const registerUser = useRegister();
  const { session, hydrated } = useSession();
  const [showPassword, setShowPassword] = React.useState(false);

  const next = searchParams.get('next');
  const destination = next ? decodeURIComponent(next) : '/search';

  React.useEffect(() => {
    if (hydrated && session) router.replace(destination);
  }, [hydrated, session, destination, router]);

  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    mode: 'onChange',
    defaultValues: { identifier: '', password: '', confirmPassword: '' },
  });

  const password = watch('password') ?? '';

  function onSubmit(values: RegisterValues) {
    registerUser.mutate(
      { identifier: values.identifier, password: values.password },
      {
        onSuccess: () => {
          toast.success('Account created', { description: "You're signed in and ready to book." });
          router.replace(destination);
        },
        onError: (error) => {
          if (error instanceof ApiError && error.fieldErrors.length > 0) {
            for (const fieldError of error.fieldErrors) {
              if (fieldError.field === 'identifier' || fieldError.field === 'password') {
                setError(fieldError.field, { message: fieldError.message });
              }
            }
            return;
          }
          // 409 is the backend's "identifier already registered".
          if (error instanceof ApiError && error.status === 409) {
            setError('identifier', { message: 'That account already exists. Try signing in.' });
            return;
          }
          toast.error('Could not create your account', {
            description: error instanceof Error ? error.message : 'Something went wrong.',
          });
        },
      },
    );
  }

  return (
    <AuthLayout
      title="Create your account"
      subtitle="One account for searching, holding seats and every booking after."
      highlights={HIGHLIGHTS}
      footer={
        <>
          Already have an account?{' '}
          <Link href="/login" className="text-accent hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5" noValidate>
        <Field label="Email or phone" htmlFor="identifier" error={errors.identifier?.message}>
          <Input
            id="identifier"
            autoComplete="username"
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
              autoComplete="new-password"
              placeholder="Choose a strong password"
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

        {/* Live requirement checklist — the rules are visible before they are broken. */}
        <ul className="-mt-1 grid grid-cols-2 gap-x-4 gap-y-2">
          {RULES.map((rule) => {
            const met = rule.test(password);
            return (
              <li
                key={rule.label}
                className={cn(
                  'flex items-center gap-1.5 text-caption transition-colors duration-200',
                  met ? 'text-success' : 'text-content-muted',
                )}
              >
                <Check className={cn('size-3.5 shrink-0', !met && 'opacity-35')} aria-hidden />
                {rule.label}
              </li>
            );
          })}
        </ul>

        <Field
          label="Confirm password"
          htmlFor="confirmPassword"
          error={errors.confirmPassword?.message}
        >
          <Input
            id="confirmPassword"
            type={showPassword ? 'text' : 'password'}
            autoComplete="new-password"
            placeholder="Repeat your password"
            icon={<Lock />}
            invalid={Boolean(errors.confirmPassword)}
            {...register('confirmPassword')}
          />
        </Field>

        <Button
          type="submit"
          size="lg"
          full
          loading={registerUser.isPending}
          loadingText="Creating your account"
        >
          Create account
        </Button>
      </form>
    </AuthLayout>
  );
}
