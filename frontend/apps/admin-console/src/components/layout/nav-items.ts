import {
  Activity,
  LayoutDashboard,
  ScrollText,
  Server,
  ShieldCheck,
  Waypoints,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

export interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  description: string;
}

/** One list, rendered by both the sidebar and the mobile drawer so they cannot drift. */
export const NAV_ITEMS: NavItem[] = [
  {
    href: '/dashboard',
    label: 'Dashboard',
    icon: LayoutDashboard,
    description: 'Registry, capability and health overview',
  },
  {
    href: '/providers',
    label: 'Providers',
    icon: Server,
    description: 'Register, configure and put providers in service',
  },
  {
    href: '/location-mappings',
    label: 'Location Mappings',
    icon: Waypoints,
    description: 'Translate canonical locations into provider identifiers',
  },
  {
    href: '/credentials',
    label: 'Credentials',
    icon: ShieldCheck,
    description: 'Partner secret presence and rotation',
  },
  {
    href: '/health',
    label: 'Health',
    icon: Activity,
    description: 'Probe providers on demand',
  },
  {
    href: '/audit',
    label: 'Audit',
    icon: ScrollText,
    description: 'Provider audit history',
  },
];

/** Longest-prefix match, so `/providers/{id}` still highlights Providers. */
export function activeNavHref(pathname: string): string | null {
  const matches = NAV_ITEMS.filter(
    (item) => pathname === item.href || pathname.startsWith(`${item.href}/`),
  );
  return matches.sort((a, b) => b.href.length - a.href.length)[0]?.href ?? null;
}
