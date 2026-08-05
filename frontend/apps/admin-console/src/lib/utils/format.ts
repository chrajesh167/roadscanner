import { format, formatDistanceToNowStrict, parseISO } from 'date-fns';

export function toDate(value: string | Date): Date {
  return value instanceof Date ? value : parseISO(value);
}

/** 4 Aug 2026, 14:05 — the console's default timestamp. */
export function formatDateTime(value: string | Date): string {
  return format(toDate(value), 'd MMM yyyy, HH:mm');
}

/** "3 days ago" / "in 2 hours" — freshness, which is what credential and health screens are about. */
export function formatRelative(value: string | Date): string {
  return formatDistanceToNowStrict(toDate(value), { addSuffix: true });
}

/** PENDING_PAYMENT -> Pending payment */
export function humanizeEnum(value: string): string {
  const lower = value.replace(/_/g, ' ').toLowerCase();
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/** SEAT_MAP -> Seat map, but keeps recognised initialisms readable in a dense capability list. */
export function humanizeCapability(value: string): string {
  return humanizeEnum(value);
}

/** A provider id is a UUID; surface a short, readable handle in lists. */
export function shortId(id: string): string {
  return id.split('-')[0]?.toUpperCase() ?? id.slice(0, 8).toUpperCase();
}

/** 8000 -> "8.0s". Timeouts are configured in milliseconds but read better as seconds. */
export function formatTimeout(milliseconds: number): string {
  return `${(milliseconds / 1000).toFixed(1)}s`;
}
