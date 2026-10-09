'use client';

import { Permission } from '@banking/api';
import { Alert, PageHeader, Tabs } from '@banking/ui';
import { useState } from 'react';
import { CashPointsPanel } from '@/components/cash/cash-points-panel';
import { MovementsPanel } from '@/components/cash/movements-panel';
import { PositionsPanel } from '@/components/cash/positions-panel';
import { SessionsPanel } from '@/components/cash/sessions-panel';
import { useCan } from '@/lib/me';

export default function CashPage() {
  const canView = useCan(Permission.cashView, Permission.cashManage);
  const canSeeSessions = useCan(Permission.cashView, Permission.tellerSupervise);
  const canSeePositions = useCan(Permission.cashView, Permission.cashManage, Permission.tellerSupervise);
  const tabs = [
    ...(canView ? [{ id: 'points', label: 'Vaults & drawers' }, { id: 'movements', label: 'Cash movements' }] : []),
    ...(canSeeSessions ? [{ id: 'sessions', label: 'Teller sessions' }] : []),
    ...(canSeePositions ? [{ id: 'positions', label: 'End-of-day positions' }] : []),
  ];
  const [tab, setTab] = useState(tabs[0]?.id ?? '');

  return (
    <>
      <PageHeader
        title="Cash"
        description="Vaults and drawers hold cash as ledger accounts that can never go below zero. Every movement between them needs a second person."
      />
      {tabs.length === 0 ? <Alert tone="info">You don't have access to cash management.</Alert> : <Tabs tabs={tabs} active={tab} onChange={setTab} />}
      {tab === 'points' ? <CashPointsPanel /> : null}
      {tab === 'movements' ? <MovementsPanel /> : null}
      {tab === 'sessions' ? <SessionsPanel /> : null}
      {tab === 'positions' ? <PositionsPanel /> : null}
    </>
  );
}
