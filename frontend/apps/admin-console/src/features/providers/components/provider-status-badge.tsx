import { Badge, enabledTone } from '@/components/ui/badge';

/**
 * "In service" rather than "Enabled": the registry flag decides whether search and booking route
 * work to this provider, and the operational meaning is what an admin is reasoning about.
 */
export function ProviderStatusBadge({ enabled }: { enabled: boolean }) {
  return (
    <Badge tone={enabledTone(enabled)} className="shrink-0">
      {enabled ? 'In service' : 'Out of service'}
    </Badge>
  );
}
