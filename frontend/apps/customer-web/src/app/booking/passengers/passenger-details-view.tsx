'use client';

import * as React from 'react';
import { useRouter } from 'next/navigation';
import { Controller, useFieldArray, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { ArrowRight, TicketX, UserRound } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { EmptyState } from '@/components/ui/feedback';
import { Field, Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Separator } from '@/components/ui/misc';
import { FadeIn, Stagger, StaggerItem } from '@/components/ui/motion';
import { PageShell } from '@/components/layout/page-shell';
import { FlowSteps } from '@/components/booking/flow-steps';
import { HoldTimer } from '@/components/booking/hold-timer';
import { useCreateBooking } from '@/lib/hooks/use-bookings';
import { isHoldExpired, useBookingFlowStore } from '@/lib/store/booking-flow-store';
import { ApiError } from '@/lib/api/client';
import { passengersFormSchema, type PassengersFormValues } from '@/lib/validation/schemas';
import { formatMoney } from '@/lib/utils/format';

const GENDERS = ['MALE', 'FEMALE', 'OTHER'] as const;

export function PassengerDetailsView() {
  const router = useRouter();
  const trip = useBookingFlowStore((state) => state.trip);
  const hold = useBookingFlowStore((state) => state.hold);
  const setPassengers = useBookingFlowStore((state) => state.setPassengers);
  const setBooking = useBookingFlowStore((state) => state.setBooking);
  const resetFlow = useBookingFlowStore((state) => state.reset);

  const createBooking = useCreateBooking();
  const [expired, setExpired] = React.useState(() => isHoldExpired(hold));

  const { control, register, handleSubmit, setError, formState } = useForm<PassengersFormValues>({
    resolver: zodResolver(passengersFormSchema),
    defaultValues: {
      passengers: (hold?.seatNumbers ?? []).map((seatNumber) => ({
        fullName: '',
        age: 18,
        gender: '',
        seatNumber,
      })),
    },
  });

  const { fields } = useFieldArray({ control, name: 'passengers' });

  function onSubmit(values: PassengersFormValues) {
    if (!hold) return;

    createBooking.mutate(
      { seatHoldId: hold.seatHoldId, passengers: values.passengers },
      {
        onSuccess: (result) => {
          setPassengers(values.passengers);
          setBooking(result.bookingId);
          router.push(`/booking/${result.bookingId}/payment`);
        },
        onError: (error) => {
          if (error instanceof ApiError && error.fieldErrors.length > 0) {
            // Backend field paths look like `passengers[0].fullName` — map them back onto the form.
            for (const fieldError of error.fieldErrors) {
              const match = /passengers\[(\d+)\]\.(\w+)/.exec(fieldError.field);
              if (match) {
                const [, index, key] = match;
                setError(
                  `passengers.${Number(index)}.${key}` as `passengers.${number}.fullName`,
                  { message: fieldError.message },
                );
              }
            }
            return;
          }

          // A hold that has lapsed server-side cannot be recovered — send the user back to reselect.
          if (error instanceof ApiError && (error.status === 409 || error.status === 410)) {
            setExpired(true);
            toast.error('Your seat hold expired', {
              description: 'Those seats have been released. Please choose again.',
            });
            return;
          }

          toast.error('Could not create your booking', {
            description: error instanceof Error ? error.message : 'Please try again.',
          });
        },
      },
    );
  }

  if (!hold || !trip) {
    return (
      <PageShell title="Passenger details">
        <EmptyState
          icon={<TicketX />}
          title="No seats held"
          description="Start by choosing a trip and holding the seats you want."
          action={
            <Button onClick={() => router.push('/search')}>Search trips</Button>
          }
        />
      </PageShell>
    );
  }

  if (expired) {
    return (
      <PageShell title="Passenger details">
        <EmptyState
          icon={<TicketX />}
          title="Your seat hold expired"
          description="Seats are only held for a limited window. Nothing was charged — pick your seats again to continue."
          action={
            <Button
              onClick={() => {
                const tripId = trip.tripId;
                resetFlow();
                router.push(`/trips/${tripId}/seats`);
              }}
            >
              Choose seats again
            </Button>
          }
        />
      </PageShell>
    );
  }

  const seatCount = hold.seatNumbers.length;
  const fareEach = Number.parseFloat(trip.fareAmount || '0');

  return (
    <PageShell
      width="wide"
      backHref={`/trips/${trip.tripId}/seats`}
      backLabel="Change seats"
      title="Who's travelling?"
      description="Enter each passenger's details as they appear on their ID."
      actions={<HoldTimer expiresAt={hold.expiresAt} onExpire={() => setExpired(true)} />}
    >
      <FlowSteps current="Passengers" />

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px] lg:items-start">
          <Stagger className="flex flex-col gap-4">
            {fields.map((field, index) => {
              const errors = formState.errors.passengers?.[index];
              return (
                <StaggerItem key={field.id}>
                  <Card padding="lg">
                    <div className="mb-5 flex items-center justify-between gap-3">
                      <h2 className="flex items-center gap-2.5 text-h3">
                        <UserRound className="size-4 text-content-muted" aria-hidden />
                        Passenger {index + 1}
                      </h2>
                      <Badge tone="accent" size="md">
                        Seat {field.seatNumber}
                      </Badge>
                    </div>

                    <div className="grid gap-5 sm:grid-cols-[minmax(0,1fr)_100px_140px]">
                      <Field
                        label="Full name"
                        htmlFor={`passenger-${index}-name`}
                        error={errors?.fullName?.message}
                      >
                        <Input
                          id={`passenger-${index}-name`}
                          autoComplete="off"
                          placeholder="As printed on their ID"
                          invalid={Boolean(errors?.fullName)}
                          {...register(`passengers.${index}.fullName`)}
                        />
                      </Field>

                      <Field
                        label="Age"
                        htmlFor={`passenger-${index}-age`}
                        error={errors?.age?.message}
                      >
                        <Input
                          id={`passenger-${index}-age`}
                          type="number"
                          min={1}
                          max={120}
                          inputMode="numeric"
                          invalid={Boolean(errors?.age)}
                          {...register(`passengers.${index}.age`, { valueAsNumber: true })}
                        />
                      </Field>

                      <Field
                        label="Gender"
                        htmlFor={`passenger-${index}-gender`}
                        error={errors?.gender?.message}
                      >
                        <Controller
                          control={control}
                          name={`passengers.${index}.gender`}
                          render={({ field: genderField }) => (
                            <Select value={genderField.value} onValueChange={genderField.onChange}>
                              <SelectTrigger id={`passenger-${index}-gender`}>
                                <SelectValue placeholder="Select" />
                              </SelectTrigger>
                              <SelectContent>
                                {GENDERS.map((gender) => (
                                  <SelectItem key={gender} value={gender}>
                                    {gender.charAt(0) + gender.slice(1).toLowerCase()}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          )}
                        />
                      </Field>
                    </div>

                    <input type="hidden" {...register(`passengers.${index}.seatNumber`)} />
                  </Card>
                </StaggerItem>
              );
            })}
          </Stagger>

          {/* Fare summary */}
          <FadeIn delay={0.1} className="lg:sticky lg:top-24">
            <Card variant="elevated" padding="lg" className="flex flex-col gap-5">
              <h2 className="text-h3">Fare summary</h2>

              <div className="flex flex-col gap-3 text-body">
                <div className="flex items-baseline justify-between">
                  <span className="text-content-secondary">
                    {formatMoney(trip.fareAmount, trip.fareCurrency)} × {seatCount}
                  </span>
                  <span className="tabular-nums text-content">
                    {formatMoney(fareEach * seatCount, trip.fareCurrency)}
                  </span>
                </div>
                <p className="text-caption text-content-muted">
                  Seats {hold.seatNumbers.join(', ')}
                </p>
              </div>

              <Separator />

              <div className="flex items-baseline justify-between">
                <span className="text-body text-content-secondary">Total</span>
                <span className="text-h3 tabular-nums">
                  {formatMoney(fareEach * seatCount, trip.fareCurrency)}
                </span>
              </div>

              <Button
                type="submit"
                size="lg"
                full
                loading={createBooking.isPending}
                loadingText="Creating booking"
              >
                Continue to payment
                <ArrowRight />
              </Button>

              <p className="text-caption text-content-muted">
                You won&apos;t be charged until you confirm payment on the next screen.
              </p>
            </Card>
          </FadeIn>
        </div>
      </form>
    </PageShell>
  );
}
