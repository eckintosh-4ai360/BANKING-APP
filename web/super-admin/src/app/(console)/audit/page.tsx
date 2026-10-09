'use client';

import { AuditLogView, AuditSealsView } from '@banking/console';
import { PageHeader, Tabs } from '@banking/ui';
import { useState } from 'react';

const TABS = [
  { id: 'log', label: 'Entries' },
  { id: 'seals', label: 'Integrity seals' },
];

export default function PlatformAuditPage() {
  const [tab, setTab] = useState('log');
  return (
    <>
      <PageHeader
        title="Platform audit"
        description="Actions taken by platform operators, including onboarding, licensing and status changes. The platform trail is sealed separately from each institution's."
      />
      <Tabs tabs={TABS} active={tab} onChange={setTab} />
      {tab === 'log' ? <AuditLogView endpoint="/platform/audit-logs" /> : <AuditSealsView endpoint="/platform/audit-seals" />}
    </>
  );
}
