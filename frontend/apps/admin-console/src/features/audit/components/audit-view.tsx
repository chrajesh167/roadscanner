'use client';

import Link from 'next/link';
import { Database, Radio, ScrollText } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { TBody, TD, TH, THead, TR, Table } from '@/components/ui/misc';
import { PageContainer } from '@/components/layout/app-shell';
import { AUDIT_UNAVAILABLE } from '../api';

/**
 * Audit history — placeholder integration.
 *
 * <p>The screen exists, its table shape is settled, and the data it wants is already being written
 * by the backend. What does not exist is a REST route to read it, so this renders an explicit
 * "not available yet" state instead of a fake table.
 *
 * <p>The disabled preview below is deliberate: it shows the shape the screen will take, with the
 * columns mapped to real `audit_records` fields, so wiring it up later is a change of data source
 * rather than a redesign.
 */
export function AuditView() {
  return (
    <PageContainer
      title="Audit"
      description="Every provider operation the platform performs, with its outcome and timing."
    >
      <Card padding="lg" className="mb-6 border-warning/25 bg-warning-soft">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
          <span
            className="grid size-11 shrink-0 place-items-center rounded-full border border-warning/25 bg-warning/10 text-warning [&_svg]:size-5"
            aria-hidden
          >
            <ScrollText />
          </span>
          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <h2 className="text-h3 text-content">No audit endpoint exists yet</h2>
              <p className="text-body text-content-secondary">
                This is a gap in the backend, not in the console. The audit data is being recorded —
                there is simply no HTTP route to read it, so nothing can display it.
              </p>
            </div>

            <div className="flex flex-col gap-2.5">
              <SourceLine
                icon={<Database />}
                title="audit_records"
                detail="Written for every provider operation, indexed on (provider_type, occurred_at DESC) — the exact read this screen needs."
              />
              <SourceLine
                icon={<Radio />}
                title="provider-integration-events"
                detail="The same records published to Kafka. Consumed by nothing that serves a UI today."
              />
            </div>

            <p className="text-caption text-content-muted">
              The client contract is already written against those columns in{' '}
              <code className="font-mono text-micro">features/audit/api.ts</code>. When a read route
              lands, this screen becomes a data-source change rather than new work.
            </p>
          </div>
        </div>
      </Card>

      <Card padding="md">
        <CardHeader className="mb-5">
          <CardTitle>Planned view</CardTitle>
          <CardDescription>
            The shape this screen will take, shown inert. No request is made.
          </CardDescription>
        </CardHeader>

        <div
          className="pointer-events-none select-none opacity-40"
          aria-hidden={AUDIT_UNAVAILABLE}
        >
          <Table>
            <THead>
              <TR>
                <TH scope="col">Occurred</TH>
                <TH scope="col">Provider</TH>
                <TH scope="col">Operation</TH>
                <TH scope="col">Outcome</TH>
                <TH scope="col">Duration</TH>
              </TR>
            </THead>
            <TBody>
              <TR>
                <TD>—</TD>
                <TD>—</TD>
                <TD>—</TD>
                <TD>
                  <Badge tone="neutral">—</Badge>
                </TD>
                <TD>—</TD>
              </TR>
            </TBody>
          </Table>
        </div>

        <div className="mt-6 flex flex-wrap gap-3 border-t border-line pt-5">
          <Button asChild variant="secondary" size="sm">
            <Link href="/health">Check provider health instead</Link>
          </Button>
          <Button asChild variant="ghost" size="sm">
            <Link href="/providers">Back to providers</Link>
          </Button>
        </div>
      </Card>
    </PageContainer>
  );
}

function SourceLine({
  icon,
  title,
  detail,
}: {
  icon: React.ReactNode;
  title: string;
  detail: string;
}) {
  return (
    <div className="flex items-start gap-2.5">
      <span className="mt-0.5 text-content-muted [&_svg]:size-4" aria-hidden>
        {icon}
      </span>
      <p className="text-caption text-content-secondary">
        <code className="font-mono text-content">{title}</code> — {detail}
      </p>
    </div>
  );
}
