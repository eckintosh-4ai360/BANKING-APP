'use client';

import { MfaEnrollment, PasswordChangeForm } from '@banking/console';
import { Alert, Card, CardContent, CardDescription, CardHeader, CardTitle, DetailList, PageHeader } from '@banking/ui';
import { useState } from 'react';
import { usePlatformMe } from '@/lib/me';

export default function AccountPage() {
  const me = usePlatformMe();
  const [notice, setNotice] = useState<string>();
  return (
    <>
      <PageHeader title="Account security" />
      {notice ? <Alert tone="success" className="mb-4">{notice}</Alert> : null}
      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Your profile</CardTitle>
          </CardHeader>
          <CardContent>
            <DetailList
              columns={1}
              items={[
                { label: 'Name', value: me.fullName },
                { label: 'Username', value: <span className="font-mono">{me.username}</span> },
                { label: 'Email', value: me.email },
                { label: 'Role', value: me.role.replaceAll('_', ' ').toLowerCase() },
              ]}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Two-step verification</CardTitle>
            <CardDescription>Required for platform operators in deployed environments.</CardDescription>
          </CardHeader>
          <CardContent>
            <MfaEnrollment onEnabled={() => setNotice('Two-step verification is on. Your other sessions were signed out.')} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Change password</CardTitle>
          </CardHeader>
          <CardContent>
            <PasswordChangeForm onChanged={() => setNotice('Your password was changed.')} />
          </CardContent>
        </Card>
      </div>
    </>
  );
}
