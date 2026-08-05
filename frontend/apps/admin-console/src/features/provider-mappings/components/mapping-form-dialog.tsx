'use client';

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { MappingForm } from './mapping-form';
import { useCreateMapping, useUpdateMapping } from '../hooks/use-provider-mappings';
import type { LocationSummary, ProviderMapping } from '../types';

/**
 * Create and edit in one dialog, mirroring the registry's own register/edit flow.
 *
 * <p>The dialog owns the mutation so the form stays a controlled shape with no knowledge of the
 * cache — the same split `ProviderForm` uses. It closes only after the server has confirmed, so a
 * validation failure is corrected in the form the operator already filled in rather than in a
 * re-opened empty one.
 */
export function MappingFormDialog({
  open,
  onOpenChange,
  mapping,
  initialLocation,
  initialProvider,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Present when editing; absent when creating. */
  mapping?: ProviderMapping;
  initialLocation?: LocationSummary;
  initialProvider?: string;
}) {
  const create = useCreateMapping();
  const update = useUpdateMapping();

  const editing = mapping !== undefined;
  const mutation = editing ? update : create;

  function close(next: boolean) {
    onOpenChange(next);
    if (!next) {
      create.reset();
      update.reset();
    }
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-h-[90dvh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{editing ? 'Edit mapping' : 'Create mapping'}</DialogTitle>
          <DialogDescription>
            {editing
              ? 'A full replace of the editable fields. The canonical location and provider are fixed — re-pointing a mapping is a delete plus a create.'
              : 'Translates a location the catalogue already holds into one provider’s vocabulary. It never creates a location.'}
          </DialogDescription>
        </DialogHeader>

        <MappingForm
          // Remounts per target so an edit dialog never opens holding the previous row's values.
          key={mapping?.id ?? initialLocation?.id ?? 'create'}
          mapping={mapping}
          initialLocation={initialLocation}
          initialProvider={initialProvider}
          submitting={mutation.isPending}
          error={mutation.error}
          onCancel={() => close(false)}
          onSubmit={(body) => {
            if (editing) {
              update.mutate({ id: mapping.id, body }, { onSuccess: () => close(false) });
            } else {
              create.mutate(body, { onSuccess: () => close(false) });
            }
          }}
        />
      </DialogContent>
    </Dialog>
  );
}
