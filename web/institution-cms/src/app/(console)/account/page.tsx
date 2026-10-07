'use client';

import { MfaEnrollment, PasswordChangeForm } from '@banking/console';
import { Alert, Card, CardContent, CardDescription, CardHeader, CardTitle, DetailList, PageHeader } from '@banking/ui';
import { useState } from 'react';
import { useMe } from '@/lib/me';

export default function AccountPage() {
  const me = useMe();
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
                { label: 'Name', value: `${me.firstName} ${me.lastName}` },
                { label: 'Username', value: <span className="font-mono">{me.username}</span> },
                { label: 'Institution', value: `${me.institutionName} (${me.tenantCode})` },
                { label: 'Email', value: me.email },
                { label: 'Roles', value: me.roles.map((role) => role.name).join(', ') || '—' },
              ]}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Two-step verification</CardTitle>
            <CardDescription>Adds a code from your phone to every sign-in.</CardDescription>
          </CardHeader>
          <CardContent>
            <MfaEnrollment onEnabled={() => setNotice('Two-step verification is on. Your other sessions were signed out.')} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Change password</CardTitle>
            <CardDescription>Changing your password signs out your other sessions.</CardDescription>
          </CardHeader>
          <CardContent>
            <PasswordChangeForm onChanged={() => setNotice('Your password was changed.')} />
          </CardContent>
        </Card>
      </div>
    </>
  );
}
