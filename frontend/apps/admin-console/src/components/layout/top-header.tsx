'use client';

import * as React from 'react';
import { usePathname } from 'next/navigation';
import { LogOut, Menu } from 'lucide-react';
import { Logo } from './logo';
import { NAV_ITEMS, activeNavHref } from './nav-items';
import { Button } from '@/components/ui/button';
import { Avatar } from '@/components/ui/misc';
import { ConfirmDialog, useConfirm } from '@/components/ui/confirm-dialog';
import { useLogout, useSession } from '@/lib/hooks/use-auth';

/**
 * Top bar: current section, the signed-in admin, and sign-out.
 *
 * <p>The identity is shown prominently because this console acts on production supply — knowing
 * which account is about to disable a provider matters more here than it does on a booking
 * screen.
 */
export function TopHeader({ onOpenNav }: { onOpenNav: () => void }) {
  const pathname = usePathname();
  const { session } = useSession();
  const logout = useLogout();
  const confirm = useConfirm();

  const active = activeNavHref(pathname);
  const current = NAV_ITEMS.find((item) => item.href === active);

  return (
    <header className="sticky top-0 z-40 flex h-16 shrink-0 items-center gap-3 border-b border-line glass px-4 sm:px-6">
      <Button
        variant="ghost"
        size="icon"
        className="lg:hidden"
        onClick={onOpenNav}
        aria-label="Open navigation"
      >
        <Menu />
      </Button>

      <div className="lg:hidden">
        <Logo compact />
      </div>

      <div className="hidden min-w-0 flex-col lg:flex">
        <h2 className="truncate text-body font-medium text-content">{current?.label ?? 'Admin'}</h2>
        {current && (
          <p className="truncate text-caption text-content-muted">{current.description}</p>
        )}
      </div>

      <div className="ml-auto flex items-center gap-3">
        {session && (
          <div className="hidden items-center gap-2.5 sm:flex">
            <Avatar name={session.identifier ?? session.userId} />
            <div className="flex flex-col leading-tight">
              <span className="text-caption text-content">
                {session.identifier ?? 'Administrator'}
              </span>
              <span className="text-micro uppercase tracking-[0.08em] text-content-muted">
                {session.role}
              </span>
            </div>
          </div>
        )}

        <Button
          variant="ghost"
          size="sm"
          onClick={confirm.ask}
          aria-label="Sign out"
        >
          <LogOut />
          <span className="hidden sm:inline">Sign out</span>
        </Button>
      </div>

      <ConfirmDialog
        open={confirm.open}
        onOpenChange={confirm.setOpen}
        title="Sign out of the console?"
        description="You'll need to sign in again with an administrator account to manage providers."
        confirmLabel="Sign out"
        loading={logout.isPending}
        onConfirm={() => logout.mutate(undefined)}
      />
    </header>
  );
}
