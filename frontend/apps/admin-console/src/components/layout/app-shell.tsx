'use client';

import * as React from 'react';
import Link from 'next/link';
import { Sidebar, SidebarNav } from './sidebar';
import { TopHeader } from './top-header';
import { Logo } from './logo';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';

/**
 * The console frame: fixed sidebar beside a scrolling content column, with the sidebar collapsing
 * into a drawer below `lg`.
 *
 * <p>The drawer is a real modal dialog rather than a CSS-only slide-out, so focus is trapped and
 * Escape closes it — navigation is the first thing a keyboard user reaches for.
 */
export function AppShell({ children }: { children: React.ReactNode }) {
  const [navOpen, setNavOpen] = React.useState(false);

  return (
    <div className="flex min-h-dvh">
      <Sidebar />

      <div className="flex min-w-0 flex-1 flex-col">
        <TopHeader onOpenNav={() => setNavOpen(true)} />
        <main id="main" className="flex-1">
          {children}
        </main>
      </div>

      <Dialog open={navOpen} onOpenChange={setNavOpen}>
        <DialogContent className="left-0 top-0 h-dvh w-72 max-w-[85vw] translate-x-0 translate-y-0 rounded-none rounded-r-xl border-l-0 p-0">
          <DialogTitle className="sr-only">Navigation</DialogTitle>
          <div className="flex h-16 items-center border-b border-line px-5">
            <Link href="/dashboard" onClick={() => setNavOpen(false)}>
              <Logo />
            </Link>
          </div>
          <SidebarNav onNavigate={() => setNavOpen(false)} />
        </DialogContent>
      </Dialog>
    </div>
  );
}

/** The standard page frame inside the shell: constrained width, consistent gutters. */
export function PageContainer({
  title,
  description,
  actions,
  children,
}: {
  title: string;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div className="mx-auto w-full max-w-6xl px-4 pb-20 pt-8 sm:px-6 sm:pt-10">
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-2">
          <h1 className="text-h1 text-balance">{title}</h1>
          {description && (
            <p className="max-w-2xl text-body text-content-secondary">{description}</p>
          )}
        </div>
        {actions && <div className="flex shrink-0 flex-wrap items-center gap-3">{actions}</div>}
      </div>
      {children}
    </div>
  );
}
