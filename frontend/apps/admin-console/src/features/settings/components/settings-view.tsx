'use client';

import Link from 'next/link';
import { Settings2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PageContainer } from '@/components/layout/app-shell';
import { SETTINGS_SECTIONS, type SettingsSection } from '../sections';

/**
 * Settings — a declared surface, not a working one.
 *
 * <p>The same posture the Audit screen takes, for the same reason: the sections below describe
 * real configuration the platform already honours, and none of it is reachable over HTTP. Every
 * value here is bound at startup from `application.yml`, a Flyway migration or a Terraform
 * variable, so there is nothing to read back and nothing to write.
 *
 * <p><strong>There is deliberately not a single control on this page</strong> — no inputs, no
 * toggles, not even disabled ones. A greyed-out switch still says "this will work once you have
 * permission", which is a different and false claim: the endpoint does not exist. Each section
 * therefore states what it will govern, where that setting is controlled today, and what has to
 * land before it can move here.
 */
export function SettingsView() {
  return (
    <PageContainer
      title="Settings"
      description="Platform-wide configuration. Every section here is planned — none of it is editable yet."
      actions={<Badge tone="warning" size="md">Coming soon</Badge>}
    >
      <Card padding="lg" className="mb-6 border-warning/25 bg-warning-soft">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
          <span
            className="grid size-11 shrink-0 place-items-center rounded-full border border-warning/25 bg-warning/10 text-warning [&_svg]:size-5"
            aria-hidden
          >
            <Settings2 />
          </span>

          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <h2 className="text-h3 text-content">No settings API exists yet</h2>
              <p className="text-body text-content-secondary">
                These settings are unavailable because the backend exposes no endpoint for any of
                them — not because of your permissions, and not because the features are missing.
                The platform already honours most of this configuration; it is simply bound at
                startup from <code className="font-mono text-caption text-content">application.yml</code>,
                a Flyway migration or a Terraform variable, with no route to read it back or change
                it.
              </p>
            </div>

            <p className="text-caption text-content-muted">
              Nothing on this page is interactive on purpose. A disabled control would suggest the
              capability is one permission away, when what is missing is the API underneath it.
              Where a setting <em>is</em> editable today, the section says where.
            </p>
          </div>
        </div>
      </Card>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        {SETTINGS_SECTIONS.map((section) => (
          <SectionCard key={section.id} section={section} />
        ))}
      </div>

      <Card padding="md" className="mt-6">
        <CardHeader className="mb-4">
          <CardTitle>What you can change today</CardTitle>
          <CardDescription>
            The configuration that is already reachable from this console lives on its own screens.
          </CardDescription>
        </CardHeader>

        <div className="flex flex-wrap gap-3">
          <Button asChild variant="secondary" size="sm">
            <Link href="/providers">Provider timeouts, retries & capabilities</Link>
          </Button>
          <Button asChild variant="secondary" size="sm">
            <Link href="/credentials">Partner credentials</Link>
          </Button>
          <Button asChild variant="ghost" size="sm">
            <Link href="/location-mappings">Location mappings</Link>
          </Button>
        </div>
      </Card>
    </PageContainer>
  );
}

function SectionCard({ section }: { section: SettingsSection }) {
  const Icon = section.icon;

  return (
    <Card padding="md" className="flex h-full flex-col gap-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <span
            className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-full border border-line-strong bg-white/[0.04] text-content-muted [&_svg]:size-4"
            aria-hidden
          >
            <Icon />
          </span>
          <h3 className="text-body font-medium text-content">{section.title}</h3>
        </div>

        <Badge tone="neutral" className="shrink-0">
          Coming in a future sprint
        </Badge>
      </div>

      <p className="text-body text-content-secondary">{section.description}</p>

      <dl className="mt-auto flex flex-col gap-3 border-t border-line pt-4">
        <div className="flex flex-col gap-1">
          <dt className="text-micro uppercase tracking-[0.07em] text-content-muted">Today</dt>
          <dd className="text-caption text-content-secondary">{section.where}</dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="text-micro uppercase tracking-[0.07em] text-content-muted">
            Blocked on
          </dt>
          <dd className="text-caption text-content-muted">{section.blocker}</dd>
        </div>
      </dl>
    </Card>
  );
}
