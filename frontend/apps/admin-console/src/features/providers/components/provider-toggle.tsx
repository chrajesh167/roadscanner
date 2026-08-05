'use client';

import { Switch } from '@/components/ui/misc';
import { ConfirmDialog, useConfirm } from '@/components/ui/confirm-dialog';
import { useSetProviderEnabled } from '../hooks';
import type { ProviderResponse } from '@/lib/api/types';

/**
 * The in-service toggle.
 *
 * <p>Asymmetric on purpose. Enabling applies immediately: it is additive, idempotent and undone
 * by the same switch. Disabling asks first, because it withdraws live supply — search stops
 * federating to that provider and no new booking can be held against it.
 *
 * <p>The optimistic flip lives in `useSetProviderEnabled`, so the switch reflects the intent the
 * instant it is pressed and rolls back if the server disagrees.
 */
export function ProviderToggle({ provider }: { provider: ProviderResponse }) {
  const setEnabled = useSetProviderEnabled();
  const confirm = useConfirm();

  const apply = (enabled: boolean) =>
    setEnabled.mutate(
      { id: provider.id, enabled, displayName: provider.displayName },
      { onSettled: () => confirm.close() },
    );

  return (
    <>
      <Switch
        checked={provider.enabled}
        disabled={setEnabled.isPending}
        aria-label={
          provider.enabled
            ? `Take ${provider.displayName} out of service`
            : `Put ${provider.displayName} into service`
        }
        onCheckedChange={(next) => (next ? apply(true) : confirm.ask())}
      />

      <ConfirmDialog
        open={confirm.open}
        onOpenChange={confirm.setOpen}
        destructive
        title={`Take ${provider.displayName} out of service?`}
        description={
          <>
            Search stops federating to this provider and no new seats can be held against it.
            Existing bookings are unaffected, and nothing is deleted — sessions, health records and
            audit history stay resolvable. You can put it back into service at any time.
          </>
        }
        confirmLabel="Take out of service"
        loading={setEnabled.isPending}
        onConfirm={() => apply(false)}
      />
    </>
  );
}
