'use client';

import * as React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { AnimatePresence, motion } from 'framer-motion';
import { LogOut, Menu, Settings, Ticket, User, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Avatar, Separator } from '@/components/ui/misc';
import { useLogout, useSession } from '@/lib/hooks/use-auth';
import { cn } from '@/lib/utils/cn';
import { Logo } from './logo';

const NAV = [
  { href: '/search', label: 'Search' },
  { href: '/bookings', label: 'My bookings' },
] as const;

export function SiteHeader() {
  const pathname = usePathname();
  const { session, hydrated } = useSession();
  const logout = useLogout();
  const [menuOpen, setMenuOpen] = React.useState(false);
  const [scrolled, setScrolled] = React.useState(false);

  React.useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  // Any navigation closes the mobile sheet — otherwise it survives the route change.
  React.useEffect(() => setMenuOpen(false), [pathname]);

  return (
    <header
      className={cn(
        'sticky top-0 z-40 transition-all duration-300',
        scrolled ? 'glass border-b border-line' : 'border-b border-transparent',
      )}
    >
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-5 sm:px-8">
        <Link href="/" className="flex items-center gap-2.5" aria-label="RoadScanner home">
          <Logo />
        </Link>

        <nav className="hidden items-center gap-1 md:flex" aria-label="Main">
          {NAV.map((item) => {
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  'relative rounded-sm px-3.5 py-2 text-caption transition-colors',
                  active ? 'text-content' : 'text-content-secondary hover:text-content',
                )}
              >
                {item.label}
                {active && (
                  <motion.span
                    layoutId="nav-active"
                    className="absolute inset-x-3 -bottom-px h-px bg-accent"
                    transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
                  />
                )}
              </Link>
            );
          })}
        </nav>

        <div className="flex items-center gap-2">
          {/* `hydrated` gates this so a signed-in user never sees a flash of "Sign in". */}
          {!hydrated ? (
            <div className="h-9 w-24" aria-hidden />
          ) : session ? (
            <div className="hidden items-center gap-2 md:flex">
              <Link href="/profile" aria-label="Profile">
                <Avatar name={session.identifier ?? 'Traveller'} />
              </Link>
              <Button
                variant="ghost"
                size="icon"
                aria-label="Sign out"
                loading={logout.isPending}
                onClick={() => logout.mutate(undefined)}
              >
                <LogOut />
              </Button>
            </div>
          ) : (
            <div className="hidden items-center gap-2 md:flex">
              <Button variant="ghost" size="sm" asChild>
                <Link href="/login">Sign in</Link>
              </Button>
              <Button size="sm" asChild>
                <Link href="/register">Get started</Link>
              </Button>
            </div>
          )}

          <Button
            variant="ghost"
            size="icon"
            className="md:hidden"
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            {menuOpen ? <X /> : <Menu />}
          </Button>
        </div>
      </div>

      <AnimatePresence>
        {menuOpen && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
            className="overflow-hidden border-t border-line glass md:hidden"
          >
            <nav className="flex flex-col gap-1 px-5 py-4" aria-label="Mobile">
              {NAV.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className="rounded-sm px-3 py-2.5 text-body text-content-secondary hover:bg-white/[0.05] hover:text-content"
                >
                  {item.label}
                </Link>
              ))}

              <Separator className="my-2" />

              {session ? (
                <>
                  <Link
                    href="/profile"
                    className="flex items-center gap-2.5 rounded-sm px-3 py-2.5 text-body text-content-secondary hover:bg-white/[0.05] hover:text-content"
                  >
                    <User className="size-4" /> Profile
                  </Link>
                  <Link
                    href="/settings"
                    className="flex items-center gap-2.5 rounded-sm px-3 py-2.5 text-body text-content-secondary hover:bg-white/[0.05] hover:text-content"
                  >
                    <Settings className="size-4" /> Settings
                  </Link>
                  <button
                    type="button"
                    onClick={() => logout.mutate(undefined)}
                    className="flex items-center gap-2.5 rounded-sm px-3 py-2.5 text-left text-body text-danger hover:bg-danger-soft"
                  >
                    <LogOut className="size-4" /> Sign out
                  </button>
                </>
              ) : (
                <div className="flex flex-col gap-2 px-1 py-1">
                  <Button variant="secondary" full asChild>
                    <Link href="/login">Sign in</Link>
                  </Button>
                  <Button full asChild>
                    <Link href="/register">
                      <Ticket /> Get started
                    </Link>
                  </Button>
                </div>
              )}
            </nav>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  );
}
