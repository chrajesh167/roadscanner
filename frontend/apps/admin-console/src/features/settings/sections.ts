import {
  Bell,
  Cloud,
  Flag,
  Gauge,
  MapPinned,
  Repeat,
  Server,
  Timer,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

/**
 * What Settings will eventually hold.
 *
 * <p>Every entry is grounded in something that exists in the backend today — a configuration key,
 * a scheduler, a column — and says where that setting currently lives. Nothing here is a guess at
 * a feature: `where` describes the present, `future` describes what an API would have to expose
 * before a control could appear on this screen.
 *
 * <p>Kept as data rather than markup so the screen renders one shape per section and cannot end up
 * with a control attached to one of them by accident.
 */
export interface SettingsSection {
  id: string;
  title: string;
  icon: LucideIcon;
  /** What the section will govern, in one line. */
  description: string;
  /** Where the same setting is controlled today. Never "nowhere" without saying so. */
  where: string;
  /** What has to exist in the backend before this becomes editable here. */
  blocker: string;
}

export const SETTINGS_SECTIONS: SettingsSection[] = [
  {
    id: 'provider-execution',
    title: 'Provider execution policies',
    icon: Server,
    description:
      'Platform-wide rules for how providers are called: which capabilities may be routed, and what a provider must satisfy before it can be put into service.',
    where:
      'Per provider today, on the Providers screen — capabilities and in-service state are already editable there.',
    blocker:
      'There is no platform-level policy in `provider_configurations`; every rule is currently per-provider, so there is nothing global to read or write.',
  },
  {
    id: 'retry-timeout',
    title: 'Retry & timeout configuration',
    icon: Timer,
    description:
      'Default request timeout and retry count applied to a newly registered provider, instead of every provider being configured from scratch.',
    where:
      'Per provider today: `timeout_ms` and `retry_count` on the provider form, seeded from the migration’s column defaults.',
    blocker:
      'The defaults live in a Flyway migration, not in a settings table. Changing them for future registrations means a schema change, which no API exposes.',
  },
  {
    id: 'rate-limiting',
    title: 'Rate limiting',
    icon: Gauge,
    description:
      'Request ceilings on metered and interactive paths — the place-autocomplete limiter is the one in force today.',
    where:
      '`search-service`’s `application.yml`: 600 requests per minute with a burst of 60, fixed at deploy time.',
    blocker:
      'The limiter is built from configuration at startup. Nothing reads it back over HTTP, and nothing can change it without a restart.',
  },
  {
    id: 'scheduler',
    title: 'Scheduler & health monitor',
    icon: Repeat,
    description:
      'How often providers are probed in the background, and how aggressively expired sessions are swept.',
    where:
      '`provider-integration-service`’s `ProviderMaintenanceScheduler`, driven by `roadscanner.provider.health-check-interval`. The Health screen probes on demand; this is the unattended loop behind it.',
    blocker:
      'The interval is a Spring property bound at startup — there is no endpoint to read the current schedule or to change it.',
  },
  {
    id: 'feature-flags',
    title: 'Feature flags',
    icon: Flag,
    description:
      'Turning platform behaviour on and off per environment without a deployment.',
    where:
      'Nowhere. This is the one section with no backend counterpart at all — the platform has no flag store and no flag evaluation.',
    blocker:
      'A flag registry, an evaluation path and an admin API would all have to exist first. Until then a toggle here would control nothing.',
  },
  {
    id: 'notifications',
    title: 'Notification settings',
    icon: Bell,
    description:
      'Which operational events reach an operator, through which channel, and how loudly.',
    where:
      'Nowhere yet. `notification-service` is scaffolded in the repository but contains no implementation.',
    blocker:
      'The service has to exist before its preferences can be edited. Routing rules configured against a service that cannot send anything would be worse than no screen.',
  },
  {
    id: 'google-places',
    title: 'Google Places configuration',
    icon: MapPinned,
    description:
      'Whether place enrichment is enabled, and how long suggestions are cached against a metered API.',
    where:
      '`search-service`’s `application.yml`: enablement, a 10-minute cache TTL and a 5000-entry ceiling, with the API key supplied by environment variable.',
    blocker:
      'No read endpoint exists — and the key deliberately has no path out of the backend at all, so it will never appear here whatever else does.',
  },
  {
    id: 'deployment',
    title: 'AWS & deployment configuration',
    icon: Cloud,
    description:
      'Environment topology, scaling and infrastructure parameters.',
    where:
      'Terraform and Kubernetes manifests under `infrastructure/`, applied through CI.',
    blocker:
      'Placeholder only, and likely to stay one: infrastructure changes belong in version control with review and a plan, not behind a button in an admin console. Expect this section to become a link to the pipeline rather than a form.',
  },
];
