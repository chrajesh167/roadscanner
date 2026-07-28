'use client';

import * as React from 'react';
import { useRouter } from 'next/navigation';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeftRight, Calendar, Navigation, Search } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Field, Input } from '@/components/ui/input';
import { PlaceInput } from './place-input';
import { usePreferencesStore } from '@/lib/store/preferences-store';
import { searchSchema, type SearchValues } from '@/lib/validation/schemas';
import { toApiDate } from '@/lib/utils/format';
import { cn } from '@/lib/utils/cn';

/**
 * The one search entry point, shared by the landing hero and the dedicated search page. It only
 * ever navigates — the results page owns fetching, so the URL stays the single source of truth for
 * a query and is therefore shareable and back-button-correct.
 */
export function SearchForm({
  defaultValues,
  variant = 'hero',
}: {
  defaultValues?: Partial<SearchValues>;
  variant?: 'hero' | 'compact';
}) {
  const router = useRouter();
  const today = toApiDate(new Date());
  // An explicit value from the caller (e.g. the results page echoing the current query) always
  // beats the stored default.
  const defaultOrigin = usePreferencesStore((state) => state.defaultOrigin);

  const {
    control,
    handleSubmit,
    register,
    setValue,
    getValues,
    formState: { errors },
  } = useForm<SearchValues>({
    resolver: zodResolver(searchSchema),
    defaultValues: {
      origin: defaultValues?.origin ?? '',
      destination: defaultValues?.destination ?? '',
      date: defaultValues?.date ?? today,
    },
  });

  // Applied after mount, not as a defaultValue: the preference lives in localStorage, so seeding
  // it during render would make the client's first paint disagree with the server's HTML.
  React.useEffect(() => {
    if (!defaultValues?.origin && defaultOrigin) {
      setValue('origin', defaultOrigin);
    }
  }, [defaultOrigin, defaultValues?.origin, setValue]);

  function swap() {
    const { origin, destination } = getValues();
    setValue('origin', destination, { shouldValidate: true });
    setValue('destination', origin, { shouldValidate: true });
  }

  function onSubmit(values: SearchValues) {
    const params = new URLSearchParams({
      origin: values.origin.trim(),
      destination: values.destination.trim(),
      date: values.date,
    });
    router.push(`/search/results?${params.toString()}`);
  }

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className={cn(
        'rounded-xl p-4 sm:p-5',
        variant === 'hero' ? 'glass shadow-lg' : 'bg-surface border border-line',
      )}
    >
      <div className="grid gap-4 md:grid-cols-[1fr_auto_1fr_auto_auto] md:items-end md:gap-3">
        <Field label="From" htmlFor="origin" error={errors.origin?.message}>
          <Controller
            control={control}
            name="origin"
            render={({ field }) => (
              <PlaceInput
                id="origin"
                value={field.value}
                onChange={field.onChange}
                placeholder="Bengaluru"
                invalid={Boolean(errors.origin)}
              />
            )}
          />
        </Field>

        <Button
          type="button"
          variant="secondary"
          size="icon"
          onClick={swap}
          aria-label="Swap origin and destination"
          className="hidden md:inline-flex md:mb-0.5"
        >
          <ArrowLeftRight />
        </Button>

        <Field label="To" htmlFor="destination" error={errors.destination?.message}>
          <Controller
            control={control}
            name="destination"
            render={({ field }) => (
              <PlaceInput
                id="destination"
                value={field.value}
                onChange={field.onChange}
                placeholder="Chennai"
                invalid={Boolean(errors.destination)}
                icon={<Navigation />}
              />
            )}
          />
        </Field>

        <Field label="Date" htmlFor="date" error={errors.date?.message}>
          <Input
            id="date"
            type="date"
            min={today}
            icon={<Calendar />}
            invalid={Boolean(errors.date)}
            className="md:w-44 [&::-webkit-calendar-picker-indicator]:opacity-0 [&::-webkit-calendar-picker-indicator]:absolute [&::-webkit-calendar-picker-indicator]:inset-0 [&::-webkit-calendar-picker-indicator]:w-full [&::-webkit-calendar-picker-indicator]:cursor-pointer"
            {...register('date')}
          />
        </Field>

        <Button type="submit" size={variant === 'hero' ? 'lg' : 'md'} className="md:mb-0.5">
          <Search />
          <span className="md:hidden lg:inline">Search</span>
        </Button>
      </div>
    </form>
  );
}
