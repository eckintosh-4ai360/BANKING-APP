'use client';

import { AuditLogView } from '@banking/console';
import { PageHeader } from '@banking/ui';

export default function AuditPage() {
  return (
    <>
      <PageHeader title="Audit log" description="Every sensitive action in your institution, who did it and from where. Entries can't be changed or deleted." />
      <AuditLogView endpoint="/audit-logs" />
    </>
  );
}
