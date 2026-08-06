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

// booking-service HoldPassengerRequest: firstName/lastName @NotBlank, birthDate @NotNull @Past,
// gender @NotBlank (male|female), seatNumber @NotBlank.
//
// The name is collected in two parts rather than one because that is what the provider prints on
// the travel document, and no split rule applied to a display name is right for every real name.
export const passengerSchema = z.object({
  firstName: z
    .string()
    .trim()
    .min(1, 'Enter their given name as printed on their ID')
    .max(120, 'That is too long'),
  lastName: z
    .string()
    .trim()
    .min(1, 'Enter their family name as printed on their ID')
    .max(120, 'That is too long'),
  // A date input yields `yyyy-MM-dd` or an empty string; the backend takes the same ISO form.
  birthDate: z
    .string()
    .min(1, 'Enter their date of birth')
    .refine((value) => !Number.isNaN(Date.parse(value)), 'Enter a valid date')
    .refine((value) => Date.parse(value) < Date.now(), 'Date of birth must be in the past'),
  // Only the two values the provider accepts. A third option here would be rejected downstream at
  // confirmation — after the traveller had already paid.
  gender: z.enum(['male', 'female'], { message: 'Select a gender' }),
  seatNumber: z.string().min(1),
});

// booking-service ContactRequest: phone @NotBlank, email @NotBlank @Email.
export const contactSchema = z.object({
  // Kept as entered, country code included: the provider wants an E.164-style number and this app
  // cannot guess which country a bare national number belongs to.
  phone: z
    .string()
    .trim()
    .min(1, 'Enter a phone number')
    .regex(/^\+?[0-9\s-]{8,20}$/, 'Include the country code, e.g. +91 98765 43210'),
  email: z.string().trim().min(1, 'Enter an email address').email('Enter a valid email address'),
  communicationPreference: z.enum(['email', 'sms']),
});

export const passengersFormSchema = z.object({
  passengers: z.array(passengerSchema).min(1),
  contact: contactSchema,
});
export type PassengersFormValues = z.infer<typeof passengersFormSchema>;

// payment-service InitiatePaymentRequest: method is a PaymentMethod enum
export const paymentSchema = z.object({
  method: z.enum(['CARD', 'UPI', 'WALLET', 'NETBANKING']),
});
export type PaymentValues = z.infer<typeof paymentSchema>;
