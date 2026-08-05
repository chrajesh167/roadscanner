'use client';

import * as React from 'react';
import { AlertTriangle } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from './dialog';
import { Button } from './button';
import { cn } from '@/lib/utils/cn';

/**
 * The confirmation step for an action that changes the platform's behaviour for real travellers.
 *
 * <p>Used for disabling a provider (removes live supply from search and booking) and for
 * replacing credentials (the previous secret is not recoverable). Enabling is not gated: putting
 * a provider *into* service is reversible with one click, and a confirm on every toggle trains
 * people to click through them.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  destructive = false,
  loading = false,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: React.ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
  loading?: boolean;
  onConfirm: () => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <div className="flex items-center gap-3">
            <span
              className={cn(
                'grid size-9 shrink-0 place-items-center rounded-full border [&_svg]:size-4',
                destructive
                  ? 'border-danger/25 bg-danger-soft text-danger'
                  : 'border-line-strong bg-white/[0.04] text-content-muted',
              )}
              aria-hidden
            >
              <AlertTriangle />
            </span>
            <DialogTitle>{title}</DialogTitle>
          </div>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={loading}>
            {cancelLabel}
          </Button>
          <Button
            variant={destructive ? 'danger' : 'primary'}
            onClick={onConfirm}
            loading={loading}
            loadingText="Working…"
          >
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/**
 * Owns the open/close and pending state for a confirmed action so callers do not each re-declare
 * the same three `useState`s.
 */
export function useConfirm() {
  const [open, setOpen] = React.useState(false);
  return {
    open,
    setOpen,
    ask: () => setOpen(true),
    close: () => setOpen(false),
  };
}
