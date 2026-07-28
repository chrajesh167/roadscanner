import Link from 'next/link';
import { Logo } from './logo';

const LINKS = [
  { href: '/search', label: 'Search' },
  { href: '/bookings', label: 'My bookings' },
  { href: '/profile', label: 'Profile' },
  { href: '/settings', label: 'Settings' },
];

export function SiteFooter() {
  return (
    <footer className="mt-24 border-t border-line">
      <div className="mx-auto flex max-w-6xl flex-col gap-8 px-5 py-12 sm:px-8 md:flex-row md:items-start md:justify-between">
        <div className="flex flex-col gap-3">
          <Logo />
          <p className="max-w-xs text-caption text-content-muted">
            A calmer way to search, compare and book intercity travel.
          </p>
        </div>

        <nav className="flex flex-wrap gap-x-8 gap-y-3" aria-label="Footer">
          {LINKS.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="text-caption text-content-secondary transition-colors hover:text-content"
            >
              {link.label}
            </Link>
          ))}
        </nav>
      </div>

      <div className="mx-auto max-w-6xl px-5 pb-10 sm:px-8">
        <div className="rule-fade mb-5" />
        <p className="text-micro uppercase text-content-muted">
          © {new Date().getFullYear()} RoadScanner
        </p>
      </div>
    </footer>
  );
}
