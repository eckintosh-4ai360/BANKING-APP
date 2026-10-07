'use client';

import { LogOut, Menu } from 'lucide-react';
import { useState, type ComponentType, type ReactNode } from 'react';
import { cn } from '../lib/cn';
import { Button } from './button';

export interface ShellNavItem {
  href: string;
  label: string;
  icon?: ComponentType<{ className?: string; 'aria-hidden'?: boolean }>;
  active?: boolean;
}

export interface ShellNavSection {
  title?: string;
  items: ShellNavItem[];
}

export type LinkComponent = ComponentType<{ href: string; className?: string; children: ReactNode; onClick?: () => void; 'aria-current'?: 'page' }>;

export interface AppShellProps {
  brand: ReactNode;
  sections: ShellNavSection[];
  /** next/link in the apps; keeps this package framework-agnostic. */
  link: LinkComponent;
  userName: ReactNode;
  userDetail?: ReactNode;
  onSignOut: () => void;
  signingOut?: boolean;
  banner?: ReactNode;
  children: ReactNode;
}

export function AppShell({ brand, sections, link: Link, userName, userDetail, onSignOut, signingOut, banner, children }: AppShellProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const nav = (
    <nav aria-label="Main" className="grid gap-6 px-3 py-4">
      {sections
        .filter((section) => section.items.length > 0)
        .map((section, index) => (
          <div key={section.title ?? index} className="grid gap-1">
            {section.title ? (
              <p className="px-3 pb-1 text-[11px] font-semibold uppercase tracking-wider text-sidebar-muted">{section.title}</p>
            ) : null}
            {section.items.map((item) => {
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={() => setMenuOpen(false)}
                  aria-current={item.active ? 'page' : undefined}
                  className={cn(
                    'flex items-center gap-3 rounded-md px-3 py-2 text-sm text-sidebar-foreground/85 transition-colors hover:bg-sidebar-active hover:text-sidebar-foreground',
                    item.active && 'bg-sidebar-active font-medium text-sidebar-foreground',
                  )}
                >
                  {Icon ? <Icon className="size-4 shrink-0" aria-hidden /> : null}
                  {item.label}
                </Link>
              );
            })}
          </div>
        ))}
    </nav>
  );

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[16rem_1fr]">
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 w-64 overflow-y-auto bg-sidebar text-sidebar-foreground transition-transform lg:static lg:translate-x-0',
          menuOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex h-14 items-center border-b border-white/10 px-6 font-semibold">{brand}</div>
        {nav}
      </aside>
      {menuOpen ? <div className="fixed inset-0 z-30 bg-black/40 lg:hidden" aria-hidden="true" onClick={() => setMenuOpen(false)} /> : null}
      <div className="flex min-w-0 flex-col">
        <header className="sticky top-0 z-20 flex h-14 items-center justify-between gap-4 border-b bg-card/95 px-4 backdrop-blur lg:px-8">
          <Button variant="ghost" size="icon" className="lg:hidden" onClick={() => setMenuOpen(true)} aria-label="Open menu">
            <Menu aria-hidden="true" />
          </Button>
          <div className="ml-auto flex items-center gap-3">
            <div className="text-right leading-tight">
              <p className="text-sm font-medium">{userName}</p>
              {userDetail ? <p className="text-xs text-muted-foreground">{userDetail}</p> : null}
            </div>
            <Button variant="outline" size="sm" onClick={onSignOut} loading={signingOut}>
              {signingOut ? null : <LogOut aria-hidden="true" />}
              Sign out
            </Button>
          </div>
        </header>
        {banner}
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 lg:px-8">{children}</main>
      </div>
    </div>
  );
}
