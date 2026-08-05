import { Badge } from '@/components/ui/badge';
import { Tooltip } from '@/components/ui/misc';
import { humanizeCapability } from '@/lib/utils/format';
import { PROVIDER_CAPABILITIES } from '@/lib/api/types';
import type { ProviderCapability } from '@/lib/api/types';

/**
 * A provider's declared capabilities.
 *
 * <p>With `showMissing`, the ones it does *not* declare are rendered too, dimmed. That absence is
 * operationally load-bearing: a provider without `TICKET_DOWNLOAD` cannot issue an e-ticket, and
 * callers route work by this list, so "not declared" is a fact worth seeing rather than inferring
 * from a gap.
 */
export function CapabilityList({
  capabilities,
  showMissing = false,
  max,
}: {
  capabilities: ProviderCapability[];
  showMissing?: boolean;
  max?: number;
}) {
  const declared = new Set(capabilities);
  const visible = max ? capabilities.slice(0, max) : capabilities;
  const overflow = max ? capabilities.length - visible.length : 0;

  return (
    <div className="flex flex-wrap gap-1.5">
      {visible.map((capability) => (
        <Badge key={capability} tone="accent">
          {humanizeCapability(capability)}
        </Badge>
      ))}

      {overflow > 0 && (
        <Tooltip content={capabilities.slice(max).map(humanizeCapability).join(', ')}>
          <Badge tone="neutral">+{overflow}</Badge>
        </Tooltip>
      )}

      {showMissing &&
        PROVIDER_CAPABILITIES.filter((capability) => !declared.has(capability)).map((capability) => (
          <Tooltip key={capability} content="Not declared — this provider cannot do this">
            <Badge tone="neutral" className="opacity-45 line-through decoration-1">
              {humanizeCapability(capability)}
            </Badge>
          </Tooltip>
        ))}
    </div>
  );
}
