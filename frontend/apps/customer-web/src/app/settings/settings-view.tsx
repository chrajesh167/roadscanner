'use client';

import * as React from 'react';
import { toast } from 'sonner';
import { KeyRound, LogOut, MonitorSmartphone, RotateCcw, SlidersHorizontal } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Field, Input, Label } from '@/components/ui/input';
import { Separator, Switch } from '@/components/ui/misc';
import { FadeIn } from '@/components/ui/motion';
import { PageShell } from '@/components/layout/page-shell';
import { authEndpoints } from '@/lib/api/endpoints/auth';
import { useLogout, useSession } from '@/lib/hooks/use-auth';
import { usePreferencesStore } from '@/lib/store/preferences-store';
import { formatDateTime } from '@/lib/utils/format';

function SettingRow({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-6 py-4">
      <div className="min-w-0">
        <p className="text-body text-content">{title}</p>
        <p className="mt-1 text-caption text-content-secondary">{description}</p>
      </div>
      <div className="shrink-0 pt-0.5">{children}</div>
    </div>
  );
}

export function SettingsView() {
  const { session } = useSession();
  const logout = useLogout();
  const preferences = usePreferencesStore();
  const [signOutAllOpen, setSignOutAllOpen] = React.useState(false);
  const [resetSent, setResetSent] = React.useState(false);

  async function requestPasswordReset() {
    if (!session?.identifier) {
      toast.error('No identifier on this session', {
        description: 'Sign in again so we know which account to send the reset to.',
      });
      return;
    }
    try {
      await authEndpoints.requestPasswordReset({ identifier: session.identifier });
      setResetSent(true);
      // The endpoint deliberately never reveals whether an account exists, so the copy can't
      // promise an email will arrive — only that the request was accepted.
      toast.success('Reset requested', {
        description: 'If that account exists, reset instructions are on the way.',
      });
    } catch {
      toast.error('Could not request a reset', { description: 'Please try again shortly.' });
    }
  }

  return (
    <PageShell title="Settings" description="Preferences, security and sessions.">
      <div className="flex flex-col gap-6">
        {/* --- Preferences (local only) --- */}
        <FadeIn>
          <Card padding="lg">
            <h2 className="flex items-center gap-2 text-h3">
              <SlidersHorizontal className="size-4 text-content-muted" aria-hidden />
              Preferences
            </h2>
            <p className="mt-1.5 text-caption text-content-muted">
              Stored on this device. They travel with your browser, not your account.
            </p>

            <Separator className="my-5" />

            <Field
              label="Default origin"
              htmlFor="defaultOrigin"
              hint="Prefilled as the 'From' city when you start a new search."
            >
              <Input
                id="defaultOrigin"
                placeholder="e.g. Bengaluru"
                value={preferences.defaultOrigin}
                onChange={(event) => preferences.set('defaultOrigin', event.target.value)}
              />
            </Field>

            <div className="mt-2 divide-y divide-[--color-line]">
              <SettingRow
                title="Precise fares"
                description="Always show two decimal places, even on whole amounts."
              >
                <Switch
                  checked={preferences.preciseFares}
                  onCheckedChange={(checked) => preferences.set('preciseFares', checked)}
                  aria-label="Precise fares"
                />
              </SettingRow>

              <SettingRow
                title="Reduce motion"
                description="Turn off non-essential animation regardless of your system setting."
              >
                <Switch
                  checked={preferences.reduceMotion}
                  onCheckedChange={(checked) => preferences.set('reduceMotion', checked)}
                  aria-label="Reduce motion"
                />
              </SettingRow>

              <SettingRow
                title="Quiet timers"
                description="Keep the seat-hold countdown calm instead of highlighting the final minute."
              >
                <Switch
                  checked={preferences.quietTimers}
                  onCheckedChange={(checked) => preferences.set('quietTimers', checked)}
                  aria-label="Quiet timers"
                />
              </SettingRow>
            </div>

            <Button variant="ghost" size="sm" className="mt-4" onClick={() => preferences.reset()}>
              <RotateCcw />
              Reset to defaults
            </Button>
          </Card>
        </FadeIn>

        {/* --- Security --- */}
        <FadeIn delay={0.08}>
          <Card padding="lg">
            <h2 className="flex items-center gap-2 text-h3">
              <KeyRound className="size-4 text-content-muted" aria-hidden />
              Security
            </h2>

            <Separator className="my-5" />

            <SettingRow
              title="Password"
              description="We'll send reset instructions to your registered identifier."
            >
              <Button
                variant="secondary"
                size="sm"
                disabled={resetSent}
                onClick={() => void requestPasswordReset()}
              >
                {resetSent ? 'Reset requested' : 'Reset password'}
              </Button>
            </SettingRow>
          </Card>
        </FadeIn>

        {/* --- Sessions --- */}
        <FadeIn delay={0.14}>
          <Card padding="lg">
            <h2 className="flex items-center gap-2 text-h3">
              <MonitorSmartphone className="size-4 text-content-muted" aria-hidden />
              Sessions
            </h2>

            <Separator className="my-5" />

            <div className="flex flex-col gap-2">
              <Label>This session</Label>
              <p className="text-caption text-content-secondary">
                Signed in as{' '}
                <span className="text-content">{session?.identifier ?? 'this account'}</span>
                {session?.accessTokenExpiresAt && (
                  <>
                    {' · '}
                    access expires {formatDateTime(session.accessTokenExpiresAt)}
                  </>
                )}
              </p>
            </div>

            <div className="mt-6 flex flex-wrap gap-3">
              <Button
                variant="secondary"
                loading={logout.isPending && !signOutAllOpen}
                onClick={() => logout.mutate(undefined)}
              >
                <LogOut />
                Sign out
              </Button>
              <Button variant="danger" onClick={() => setSignOutAllOpen(true)}>
                Sign out everywhere
              </Button>
            </div>
          </Card>
        </FadeIn>
      </div>

      <Dialog open={signOutAllOpen} onOpenChange={setSignOutAllOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Sign out of every device?</DialogTitle>
            <DialogDescription>
              This revokes every active session on your account, including this one. You&apos;ll
              need to sign in again everywhere.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="secondary">Stay signed in</Button>
            </DialogClose>
            <Button
              variant="danger"
              loading={logout.isPending}
              loadingText="Signing out"
              onClick={() => logout.mutate({ allDevices: true })}
            >
              Sign out everywhere
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageShell>
  );
}
