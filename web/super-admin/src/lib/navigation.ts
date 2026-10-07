import { hasAny, Permission } from '@banking/api';
import { Building2, KeyRound, ScrollText, type LucideIcon } from 'lucide-react';

interface NavEntry {
  href: string;
  label: string;
  icon: LucideIcon;
  requires?: string[];
}

const ENTRIES: NavEntry[] = [
  { href: '/', label: 'Institutions', icon: Building2, requires: [Permission.platformTenantView] },
  { href: '/audit', label: 'Platform audit', icon: ScrollText, requires: [Permission.platformAuditView] },
  { href: '/account', label: 'Account security', icon: KeyRound },
];

export function visibleNavigation(permissions: readonly string[], pathname: string) {
  return [
    {
      items: ENTRIES.filter((entry) => hasAny(permissions, entry.requires)).map((entry) => ({
        href: entry.href,
        label: entry.label,
        icon: entry.icon,
        active: entry.href === '/' ? pathname === '/' || pathname.startsWith('/tenants') : pathname.startsWith(entry.href),
      })),
    },
  ];
}
