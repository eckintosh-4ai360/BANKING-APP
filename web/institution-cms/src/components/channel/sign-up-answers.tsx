'use client';

import { ApiError, bff, type CustomerSignUp } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Badge, Card, CardContent, CardHeader, CardTitle, DetailList, formatDateTime, humanize } from '@banking/ui';
import { useQuery } from '@tanstack/react-query';

/**
 * What a customer who signed up in the app answered about their account (for the reviewer's risk rating). Shows
 * nothing for customers onboarded at a branch.
 */
export function SignUpAnswers({ customerId }: { customerId: string }) {
  const signUp = useQuery({
    queryKey: ['customer-sign-up', customerId],
    queryFn: async ({ signal }) => {
      try {
        return await bff<CustomerSignUp>(`/customers/${customerId}/sign-up`, { signal });
      } catch (error) {
        if (error instanceof ApiError && (error.status === 404 || error.status === 403)) {
          return null;
        }
        throw error;
      }
    },
  });
  if (signUp.isPending || signUp.data === null) {
    return null;
  }
  if (signUp.isError) {
    return <Alert tone="danger">{errorMessage(signUp.error)}</Alert>;
  }
  const answers = signUp.data;
  return (
    <Card className="mt-6">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          Signed up in the app
          {answers.politicallyExposed ? <Badge tone="warning">Declared politically exposed</Badge> : null}
        </CardTitle>
        <p className="text-sm text-muted-foreground">The customer entered these details themselves. A declared politically exposed person can only be approved as high risk.</p>
      </CardHeader>
      <CardContent>
        <DetailList
          columns={3}
          items={[
            { label: 'Source of funds', value: answers.sourceOfFunds ? humanize(answers.sourceOfFunds) : null },
            { label: 'Account purpose', value: answers.accountPurpose ? humanize(answers.accountPurpose) : null },
            { label: 'Expected each month', value: answers.expectedMonthlyTurnover ? turnover(answers.expectedMonthlyTurnover) : null },
            { label: 'Politically exposed', value: answers.politicallyExposed === null ? null : answers.politicallyExposed ? 'Yes' : 'No' },
            { label: 'Started', value: formatDateTime(answers.startedAt) },
            { label: 'Submitted', value: formatDateTime(answers.submittedAt) },
          ]}
        />
      </CardContent>
    </Card>
  );
}

function turnover(band: string) {
  if (band === 'ABOVE_100000') {
    return 'Above 100,000';
  }
  const limit = band.replace('UP_TO_', '');
  return `Up to ${Number(limit).toLocaleString('en-GB')}`;
}
