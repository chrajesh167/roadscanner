import { z } from 'zod';

/**
 * Client-side schemas. Each mirrors the Jakarta Validation constraints on the matching Java
 * record so the user is told about a problem before a round-trip — the backend remains the
 * authority and its `fieldErrors` are still surfaced if anything slips through.
 */

// auth-service: identifier @NotBlank @Size(max = 255), password @NotBlank @Size(max = 128)
export const identifierSchema = z
  .string()
  .trim()
  .min(1, 'Enter your email or phone number')
  .max(255, 'That is too long');

export const loginSchema = z.object({
  identifier: identifierSchema,
  password: z.string().min(1, 'Enter your password').max(128, 'That is too long'),
});
export type LoginValues = z.infer<typeof loginSchema>;

export const registerSchema = z
  .object({
    identifier: identifierSchema,
    // Mirrors auth-service's PasswordComplexityPolicy exactly: at least 12 characters, and at
    // least one letter and one digit. It deliberately does NOT require an uppercase letter —
    // the backend doesn't, and demanding more than the server accepts only invents failures.
    password: z
      .string()
      .min(12, 'Use at least 12 characters')
      .max(128, 'That is too long')
      .regex(/[a-zA-Z]/, 'Include at least one letter')
      .regex(/[0-9]/, 'Include at least one number'),
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });
export type RegisterValues = z.infer<typeof registerSchema>;

// search-service: origin/destination @NotBlank, date ISO_DATE
export const searchSchema = z.object({
  origin: z.string().trim().min(1, 'Where are you starting from?'),
  destination: z.string().trim().min(1, 'Where are you going?'),
  date: z.string().min(1, 'Pick a travel date'),
});
export type SearchValues = z.infer<typeof searchSchema>;

// booking-service PassengerRequest: fullName @NotBlank, age @Min(1) @Max(120),
// gender @NotBlank, seatNumber @NotBlank
export const passengerSchema = z.object({
  fullName: z
    .string()
    .trim()
    .min(2, 'Enter the full name as printed on their ID')
    .max(120, 'That is too long'),
  // The field registers with `valueAsNumber`, so this stays a plain number schema — `z.coerce`
  // would split the resolver's input/output types and an empty input arrives as NaN either way.
  age: z
    .number({ message: 'Enter an age' })
    .int('Enter a whole number')
    .min(1, 'Age must be at least 1')
    .max(120, 'Age must be 120 or below'),
  gender: z.string().min(1, 'Select a gender'),
  seatNumber: z.string().min(1),
});

export const passengersFormSchema = z.object({
  passengers: z.array(passengerSchema).min(1),
});
export type PassengersFormValues = z.infer<typeof passengersFormSchema>;

// payment-service InitiatePaymentRequest: method is a PaymentMethod enum
export const paymentSchema = z.object({
  method: z.enum(['CARD', 'UPI', 'WALLET', 'NETBANKING']),
});
export type PaymentValues = z.infer<typeof paymentSchema>;
