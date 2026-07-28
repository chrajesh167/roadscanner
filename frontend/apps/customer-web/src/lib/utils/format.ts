import { format, formatDistanceToNowStrict, isToday, isTomorrow, parseISO } from 'date-fns';

/**
 * Every money value from the backend arrives as a decimal string plus an ISO currency code
 * (Java `BigDecimal` + `Currency.getCurrencyCode()`), never a float. Formatting stays string-in,
 * string-out so no precision is lost on the way to the screen.
 */
/**
 * Fare precision is a display-wide preference (see `preferences-store`), so it lives here as a
 * module-level setting rather than threading an option through all ~16 call sites — the same way
 * a locale or timezone default is normally handled. Changing it takes effect on the next render
 * of any screen showing money.
 */
let alwaysShowDecimals = false;

export function setMoneyPrecision(precise: boolean): void {
  alwaysShowDecimals = precise;
}

export function formatMoney(amount: string | number, currency: string): string {
  const numeric = typeof amount === 'string' ? Number.parseFloat(amount) : amount;
  if (!Number.isFinite(numeric)) return `${currency} —`;

  const fractionDigits = alwaysShowDecimals || numeric % 1 !== 0 ? 2 : 0;

  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency,
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits,
    }).format(numeric);
  } catch {
    return `${currency} ${numeric.toFixed(fractionDigits)}`;
  }
}

export function toDate(value: string | Date): Date {
  return value instanceof Date ? value : parseISO(value);
}

/** 14:05 — the departure/arrival clock time. */
export function formatTime(value: string | Date): string {
  return format(toDate(value), 'HH:mm');
}

/** Mon, 4 Aug */
export function formatDayMonth(value: string | Date): string {
  return format(toDate(value), 'EEE, d MMM');
}

/** 4 August 2026 */
export function formatFullDate(value: string | Date): string {
  return format(toDate(value), 'd MMMM yyyy');
}

/** 4 Aug 2026, 14:05 */
export function formatDateTime(value: string | Date): string {
  return format(toDate(value), "d MMM yyyy, HH:mm");
}

/** Today / Tomorrow / Mon, 4 Aug — used on date pickers and result headers. */
export function formatRelativeDay(value: string | Date): string {
  const date = toDate(value);
  if (isToday(date)) return 'Today';
  if (isTomorrow(date)) return 'Tomorrow';
  return formatDayMonth(date);
}

/** 8h 45m — the backend sends `durationMinutes` as a whole number. */
export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const mins = Math.round(minutes % 60);
  if (hours === 0) return `${mins}m`;
  if (mins === 0) return `${hours}h`;
  return `${hours}h ${mins}m`;
}

/** "in 4 minutes" — used for seat-hold countdown copy. */
export function formatTimeUntil(value: string | Date): string {
  return formatDistanceToNowStrict(toDate(value), { addSuffix: true });
}

/** mm:ss, for the live seat-hold timer. */
export function formatCountdown(msRemaining: number): string {
  const total = Math.max(0, Math.floor(msRemaining / 1000));
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

/** The API date param format — `@DateTimeFormat(iso = DATE)` on the search endpoint. */
export function toApiDate(value: Date): string {
  return format(value, 'yyyy-MM-dd');
}

/** PENDING_PAYMENT -> Pending payment */
export function humanizeEnum(value: string): string {
  const lower = value.replace(/_/g, ' ').toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/** A booking id is a UUID; surface a short, readable handle in lists. */
export function shortId(id: string): string {
  return id.split('-')[0]?.toUpperCase() ?? id.slice(0, 8).toUpperCase();
}
