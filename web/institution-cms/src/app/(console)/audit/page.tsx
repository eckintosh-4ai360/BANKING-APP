'use client';

import { AuditLogView, AuditSealsView } from '@banking/console';
import { PageHeader, Tabs } from '@banking/ui';
import { useState } from 'react';

const TABS = [
  { id: 'log', label: 'Entries' },
  { id: 'seals', label: 'Integrity seals' },
];

export default function AuditPage() {
  const [tab, setTab] = useState('log');
  return (
    <>
      <PageHeader
        title="Audit log"
        description="Every sensitive action in your institution, who did it and from where. Entries can't be changed or deleted, and every hour is sealed so that tampering would be found."
      />
      <Tabs tabs={TABS} active={tab} onChange={setTab} />
      {tab === 'log' ? <AuditLogView endpoint="/audit-logs" /> : <AuditSealsView endpoint="/audit-seals" />}
    </>
  );
}
