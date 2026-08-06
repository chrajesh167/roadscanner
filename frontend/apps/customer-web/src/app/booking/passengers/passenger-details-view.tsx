'use client';

import * as React from 'react';
import { useRouter } from 'next/navigation';
import { Controller, useFieldArray, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { toast } from 'sonner';
import { ArrowRight, Mail, Phone, TicketX, UserRound } from 'lucide-react';
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
import { useCreateBooking, useHoldSeats } from '@/lib/hooks/use-bookings';
import { isHoldExpired, useBookingFlowStore } from '@/lib/store/booking-flow-store';
import { ApiError } from '@/lib/api/client';
import { passengersFormSchema, type PassengersFormValues } from '@/lib/validation/schemas';
import { formatMoney } from '@/lib/utils/format';

/**
 * The provider accepts exactly these two and expresses seat restrictions ("ladies seat") in the
 * same terms. A third option would be accepted here and then rejected at confirmation — after the
 * traveller had paid — so it is not offered.
 */
const GENDERS = [
  { value: 'male', label: 'Male' },
  { value: 'female', label: 'Female' },
] as const;

export function PassengerDetailsView() {
  const router = useRouter();
  const trip = useBookingFlowStore((state) => state.trip);
  const selectedSeats = useBookingFlowStore((state) => state.selectedSeats);
  const hold = useBookingFlowStore((state) => state.hold);
  const setHold = useBookingFlowStore((state) => state.setHold);
  const setPassengers = useBookingFlowStore((state) => state.setPassengers);
  const setBooking = useBookingFlowStore((state) => state.setBooking);
  const resetFlow = useBookingFlowStore((state) => state.reset);

  const holdSeats = useHoldSeats();
  const createBooking = useCreateBooking();
  const submitting = holdSeats.isPending || createBooking.isPending;

  const { control, register, handleSubmit, setError, formState } = useForm<PassengersFormValues>({
    resolver: zodResolver(passengersFormSchema),
    defaultValues: {
      passengers: selectedSeats.map((seatNumber) => ({
        firstName: '',
        lastName: '',
        birthDate: '',
        gender: undefined,
        seatNumber,
      })),
      contact: { phone: '', email: '', communicationPreference: 'email' },
    },
  });

  const { fields } = useFieldArray({ control, name: 'passengers' });

  // Backend field paths are `passengers[0].firstName` and `contact.email`; react-hook-form wants
  // them dotted. Anything unrecognised falls through to a toast rather than being swallowed.
  function applyFieldErrors(error: ApiError): boolean {
    let applied = false;
    for (const fieldError of error.fieldErrors) {
      const passengerMatch = /passengers\[(\d+)\]\.(\w+)/.exec(fieldError.field);
      if (passengerMatch) {
        const [, index, key] = passengerMatch;
        setError(
          `passengers.${Number(index)}.${key}` as `passengers.${number}.firstName`,
          { message: fieldError.message },
        );
        applied = true;
        continue;
      }
      const contactMatch = /^contact\.(\w+)$/.exec(fieldError.field);
      if (contactMatch) {
        setError(`contact.${contactMatch[1]}` as 'contact.email', {
          message: fieldError.message,
        });
        applied = true;
      }
    }
    return applied;
  }

  function seatsGone() {
    toast.error('Those seats just went', {
      description: 'Someone else booked one of them while you were filling this in.',
    });
    const tripId = trip?.tripId;
    resetFlow();
    router.push(tripId ? `/trips/${tripId}/seats` : '/search');
  }

  /**
   * Two calls, in this order: the hold binds each traveller to their seat, and only then can a
   * booking consume it. A hold left over from a failed booking attempt is reused rather than
   * duplicated — re-holding would ask for seats this session is already holding and be refused.
   */
  async function onSubmit(values: PassengersFormValues) {
    if (!trip) return;

    try {
      let seatHoldId = hold && !isHoldExpired(hold) ? hold.seatHoldId : null;

      if (!seatHoldId) {
        const placed = await holdSeats.mutateAsync({
          tripId: trip.tripId,
          passengers: values.passengers,
        });
        setHold({
          seatHoldId: placed.seatHoldId,
          seatNumbers: placed.seatNumbers,
          expiresAt: placed.expiresAt,
        });
        seatHoldId = placed.seatHoldId;
      }

      const booking = await createBooking.mutateAsync({
        seatHoldId,
        contact: values.contact,
      });

      setPassengers(values.passengers);
      setBooking(booking.bookingId);
      router.push(`/booking/${booking.bookingId}/payment`);
    } catch (error) {
      if (error instanceof ApiError) {
        if (applyFieldErrors(error)) return;

        // 409 from the hold means a seat was taken while this form was open; 410 means a hold we
        // were reusing has lapsed. Either way the seats are no longer ours to book.
        if (error.status === 409 || error.status === 410) {
          seatsGone();
          return;
        }
      }

      toast.error('Could not confirm those seats', {
        description: error instanceof Error ? error.message : 'Please try again.',
      });
    }
  }

  if (!trip || selectedSeats.length === 0) {
    return (
      <PageShell title="Passenger details">
        <EmptyState
          icon={<TicketX />}
          title="No seats chosen"
          description="Start by choosing a trip and picking the seats you want."
          action={<Button onClick={() => router.push('/search')}>Search trips</Button>}
        />
      </PageShell>
    );
  }

  const seatCount = selectedSeats.length;
  const fareEach = Number.parseFloat(trip.fareAmount || '0');
  const contactErrors = formState.errors.contact;

  return (
    <PageShell
      width="wide"
      backHref={`/trips/${trip.tripId}/seats`}
      backLabel="Change seats"
      title="Who's travelling?"
      description="Enter each passenger's details as they appear on their ID."
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

                    <div className="grid gap-5 sm:grid-cols-2">
                      <Field
                        label="Given name"
                        htmlFor={`passenger-${index}-first-name`}
                        error={errors?.firstName?.message}
                      >
                        <Input
                          id={`passenger-${index}-first-name`}
                          autoComplete="off"
                          placeholder="As printed on their ID"
                          invalid={Boolean(errors?.firstName)}
                          {...register(`passengers.${index}.firstName`)}
                        />
                      </Field>

                      <Field
                        label="Family name"
                        htmlFor={`passenger-${index}-last-name`}
                        error={errors?.lastName?.message}
                      >
                        <Input
                          id={`passenger-${index}-last-name`}
                          autoComplete="off"
                          placeholder="As printed on their ID"
                          invalid={Boolean(errors?.lastName)}
                          {...register(`passengers.${index}.lastName`)}
                        />
                      </Field>

                      <Field
                        label="Date of birth"
                        htmlFor={`passenger-${index}-birth-date`}
                        error={errors?.birthDate?.message}
                      >
                        <Input
                          id={`passenger-${index}-birth-date`}
                          type="date"
                          max={new Date().toISOString().slice(0, 10)}
                          invalid={Boolean(errors?.birthDate)}
                          {...register(`passengers.${index}.birthDate`)}
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
                                  <SelectItem key={gender.value} value={gender.value}>
                                    {gender.label}
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

            {/* One contact for the whole booking, not one per passenger — the shape the operator
                delivers a ticket to. */}
            <StaggerItem>
              <Card padding="lg">
                <div className="mb-1.5 flex items-center gap-2.5">
                  <Mail className="size-4 text-content-muted" aria-hidden />
                  <h2 className="text-h3">Where should we send the ticket?</h2>
                </div>
                <p className="mb-5 text-caption text-content-muted">
                  The operator sends the ticket and any travel updates here.
                </p>

                <div className="grid gap-5 sm:grid-cols-2">
                  <Field label="Email" htmlFor="contact-email" error={contactErrors?.email?.message}>
                    <Input
                      id="contact-email"
                      type="email"
                      autoComplete="email"
                      icon={<Mail className="size-4" aria-hidden />}
                      placeholder="you@example.com"
                      invalid={Boolean(contactErrors?.email)}
                      {...register('contact.email')}
                    />
                  </Field>

                  <Field
                    label="Phone"
                    htmlFor="contact-phone"
                    error={contactErrors?.phone?.message}
                  >
                    <Input
                      id="contact-phone"
                      type="tel"
                      autoComplete="tel"
                      icon={<Phone className="size-4" aria-hidden />}
                      placeholder="+91 98765 43210"
                      invalid={Boolean(contactErrors?.phone)}
                      {...register('contact.phone')}
                    />
                  </Field>

                  <Field
                    label="Send updates by"
                    htmlFor="contact-preference"
                    error={contactErrors?.communicationPreference?.message}
                  >
                    <Controller
                      control={control}
                      name="contact.communicationPreference"
                      render={({ field: preferenceField }) => (
                        <Select
                          value={preferenceField.value}
                          onValueChange={preferenceField.onChange}
                        >
                          <SelectTrigger id="contact-preference">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="email">Email</SelectItem>
                            <SelectItem value="sms">SMS</SelectItem>
                          </SelectContent>
                        </Select>
                      )}
                    />
                  </Field>
                </div>
              </Card>
            </StaggerItem>
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
                  Seats {selectedSeats.join(', ')}
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
                loading={submitting}
                loadingText={holdSeats.isPending ? 'Holding your seats' : 'Creating booking'}
              >
                Continue to payment
                <ArrowRight />
              </Button>

              <p className="text-caption text-content-muted">
                Your seats are reserved once you continue. You won&apos;t be charged until you
                confirm payment on the next screen.
              </p>
            </Card>
          </FadeIn>
        </div>
      </form>
    </PageShell>
  );
}
