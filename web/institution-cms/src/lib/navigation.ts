import { hasAny, Permission } from '@banking/api';
import {
  Building2,
  ClipboardCheck,
  FileCheck,
  KeyRound,
  LayoutDashboard,
  Package,
  ScrollText,
  Settings,
  ShieldCheck,
  UserCog,
  Users,
  Wallet,
  type LucideIcon,
} from 'lucide-react';

export interface NavEntry {
  href: string;
  label: string;
  icon: LucideIcon;
  /** Shown when the user holds any of these (none = everyone). */
  requires?: string[];
}

export interface NavGroup {
  title?: string;
  entries: NavEntry[];
}

export const NAVIGATION: NavGroup[] = [
  { entries: [{ href: '/', label: 'Dashboard', icon: LayoutDashboard }] },
  {
    title: 'Customers',
    entries: [
      { href: '/customers', label: 'Customers', icon: Users, requires: [Permission.customerView] },
      { href: '/kyc', label: 'KYC reviews', icon: FileCheck, requires: [Permission.kycView] },
    ],
  },
  {
    title: 'Banking',
    entries: [
      { href: '/accounts', label: 'Accounts', icon: Wallet, requires: [Permission.accountView] },
      { href: '/approvals', label: 'Approvals', icon: ClipboardCheck, requires: [Permission.approvalView] },
      { href: '/products', label: 'Products', icon: Package, requires: [Permission.productView, Permission.productManage] },
    ],
  },
  {
    title: 'Organisation',
    entries: [
      { href: '/branches', label: 'Branches', icon: Building2, requires: [Permission.branchView] },
      { href: '/staff', label: 'Staff', icon: UserCog, requires: [Permission.staffView] },
      { href: '/roles', label: 'Roles & permissions', icon: ShieldCheck, requires: [Permission.roleView] },
    ],
  },
  {
    title: 'Governance',
    entries: [
      { href: '/audit', label: 'Audit log', icon: ScrollText, requires: [Permission.auditView] },
      { href: '/settings', label: 'Settings', icon: Settings, requires: [Permission.institutionView, Permission.settingsManage] },
    ],
  },
  { title: 'You', entries: [{ href: '/account', label: 'Account security', icon: KeyRound }] },
];

/** Navigation filtered by the user's permissions, with the current section marked active. */
export function visibleNavigation(permissions: readonly string[], pathname: string) {
  return NAVIGATION.map((group) => ({
    title: group.title,
    items: group.entries
      .filter((entry) => hasAny(permissions, entry.requires))
      .map((entry) => ({
        href: entry.href,
        label: entry.label,
        icon: entry.icon,
        active: entry.href === '/' ? pathname === '/' : pathname === entry.href || pathname.startsWith(`${entry.href}/`),
      })),
  })).filter((group) => group.items.length > 0);
}
