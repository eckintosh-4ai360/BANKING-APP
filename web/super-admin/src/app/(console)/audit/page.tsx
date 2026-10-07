'use client';

import { AuditLogView } from '@banking/console';
import { PageHeader } from '@banking/ui';

export default function PlatformAuditPage() {
  return (
    <>
      <PageHeader title="Platform audit" description="Actions taken by platform operators, including onboarding, licensing and status changes." />
      <AuditLogView endpoint="/platform/audit-logs" />
    </>
  );
}
