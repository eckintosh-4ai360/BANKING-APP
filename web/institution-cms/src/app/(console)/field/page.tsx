'use client';

import { PageHeader, Tabs } from '@banking/ui';
import { useState } from 'react';
import { ActivityPanel } from '@/components/field/activity-panel';
import { AlertsPanel } from '@/components/field/alerts-panel';
import { AssignmentsPanel } from '@/components/field/assignments-panel';
import { OfficersPanel } from '@/components/field/officers-panel';

const TABS = [
  { id: 'officers', label: 'Field officers' },
  { id: 'assignments', label: 'Customer assignments' },
  { id: 'collections', label: 'Collections' },
  { id: 'visits', label: 'Visits' },
  { id: 'alerts', label: 'Alerts' },
];

export default function FieldPage() {
  const [tab, setTab] = useState('officers');
  return (
    <>
      <PageHeader
        title="Field operations"
        description="Officers collect from their customers offline. Every collection is numbered on the phone; what arrives late, twice, out of order or not at all raises an alert."
      />
      <Tabs tabs={TABS} active={tab} onChange={setTab} />
      {tab === 'officers' ? <OfficersPanel /> : null}
      {tab === 'assignments' ? <AssignmentsPanel /> : null}
      {tab === 'collections' ? <ActivityPanel kind="collections" /> : null}
      {tab === 'visits' ? <ActivityPanel kind="visits" /> : null}
      {tab === 'alerts' ? <AlertsPanel /> : null}
    </>
  );
}
