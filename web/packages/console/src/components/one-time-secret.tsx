'use client';

import type { IssuedCredential } from '@banking/api';
import { Alert, Button, DetailList, Modal } from '@banking/ui';
import { Check, Copy } from 'lucide-react';
import { useState } from 'react';

/**
 * Shows a temporary password exactly once. It is never stored by the app: closing the dialog discards it, and the
 * backend keeps only its hash, so a lost password must be reset rather than looked up.
 */
export function OneTimeCredentialDialog({
  credential,
  title,
  onClose,
}: {
  credential: IssuedCredential | null;
  title: string;
  onClose: () => void;
}) {
  const [copied, setCopied] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);

  async function copy() {
    if (!credential) {
      return;
    }
    try {
      await navigator.clipboard.writeText(credential.temporaryPassword);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  function close() {
    setCopied(false);
    setAcknowledged(false);
    onClose();
  }

  return (
    <Modal
      open={credential !== null}
      onClose={close}
      busy={!acknowledged}
      title={title}
      description="Give these sign-in details to the user through a secure channel. They must choose a new password at first sign-in."
      footer={
        <Button onClick={close} disabled={!acknowledged}>
          Done
        </Button>
      }
    >
      {credential ? (
        <div className="grid gap-4">
          <Alert tone="warning" title="This password will not be shown again">
            Once you close this dialog it can't be recovered, only reset.
          </Alert>
          <DetailList
            columns={1}
            items={[
              { label: 'Username', value: <code className="font-mono">{credential.username}</code> },
              {
                label: 'Temporary password',
                value: (
                  <span className="flex items-center gap-2">
                    <code className="rounded bg-muted px-2 py-1 font-mono text-base select-all">{credential.temporaryPassword}</code>
                    <Button variant="outline" size="sm" onClick={copy} aria-label="Copy password">
                      {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
                      {copied ? 'Copied' : 'Copy'}
                    </Button>
                  </span>
                ),
              },
            ]}
          />
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="size-4 accent-primary" checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} />
            I have passed these details on securely
          </label>
        </div>
      ) : null}
    </Modal>
  );
}
