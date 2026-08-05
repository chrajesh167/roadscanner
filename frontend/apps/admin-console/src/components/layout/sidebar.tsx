'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Logo } from './logo';
import { NAV_ITEMS, activeNavHref } from './nav-items';
import { cn } from '@/lib/utils/cn';

/**
 * Primary navigation.
 *
 * <p>Fixed on desktop, rendered inside the mobile drawer by `AppShell` on small screens — one
 * component, two placements, so a route added to `NAV_ITEMS` appears in both.
 */
export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const active = activeNavHref(pathname);

  return (
    <nav aria-label="Primary" className="flex flex-1 flex-col gap-1 p-3">
      {NAV_ITEMS.map((item) => {
        const isActive = item.href === active;
        const Icon = item.icon;

        return (
          <Link
            key={item.href}
            href={item.href}
            onClick={onNavigate}
            aria-current={isActive ? 'page' : undefined}
            className={cn(
              'group flex items-start gap-3 rounded-md px-3 py-2.5 transition-colors duration-150',
              isActive
                ? 'bg-accent-soft text-content'
                : 'text-content-secondary hover:bg-white/[0.05] hover:text-content',
            )}
          >
            <Icon
              className={cn(
                'mt-0.5 size-4 shrink-0',
                isActive ? 'text-accent-text' : 'text-content-muted group-hover:text-content-secondary',
              )}
              aria-hidden
            />
            <span className="flex flex-col gap-0.5">
              <span className="text-body font-medium leading-tight">{item.label}</span>
              <span className="text-caption leading-snug text-content-muted">
                {item.description}
              </span>
            </span>
          </Link>
        );
      })}
    </nav>
  );
}

export function Sidebar() {
  return (
    <aside className="hidden w-72 shrink-0 border-r border-line bg-surface lg:flex lg:flex-col">
      <div className="flex h-16 items-center border-b border-line px-5">
        <Link href="/dashboard" className="rounded-sm">
          <Logo />
        </Link>
      </div>
      <SidebarNav />
      <div className="border-t border-line p-4">
        <p className="text-micro uppercase tracking-[0.08em] text-content-muted">
          provider-integration-service
        </p>
        <p className="mt-1 text-caption text-content-muted">
          The platform&apos;s only provider registry.
        </p>
      </div>
    </aside>
  );
}
