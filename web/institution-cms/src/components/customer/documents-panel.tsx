'use client';

import { ApiError, bff, BFF_HEADER, BFF_HEADER_VALUE, Permission, type CustomerDetail, type CustomerDocument } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, EmptyState, FormField, Input, Modal, Select, StatusBadge, Textarea, formatBytes, formatDateTime, humanize } from '@banking/ui';
import { useMutation } from '@tanstack/react-query';
import { Upload } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useCan } from '@/lib/me';
import { Section, useCanCapture, useCustomerRefresh } from './shared';

const DOCUMENT_TYPES = [
  'ID_FRONT',
  'ID_BACK',
  'SELFIE',
  'PROOF_OF_ADDRESS',
  'SIGNATURE',
  'BUSINESS_REGISTRATION',
  'TAX_CERTIFICATE',
  'EMPLOYMENT_LETTER',
  'OTHER',
] as const;

const MAX_BYTES = 10 * 1024 * 1024;
const ACCEPTED = ['image/jpeg', 'image/png', 'application/pdf'];

export function DocumentsPanel({ customer }: { customer: CustomerDetail }) {
  const canUpload = useCanCapture(customer) && (customer.status === 'PENDING' || customer.status === 'ACTIVE' || customer.status === 'RESTRICTED' || customer.status === 'DORMANT');
  const canView = useCan(Permission.kycView);
  const canReview = useCan(Permission.kycReview);
  const refresh = useCustomerRefresh(customer.id);
  const [uploading, setUploading] = useState(false);
  const [viewing, setViewing] = useState<CustomerDocument | null>(null);
  const [reviewing, setReviewing] = useState<CustomerDocument | null>(null);

  return (
    <Section
      title="Documents"
      action={
        canUpload ? (
          <Button size="sm" variant="outline" onClick={() => setUploading(true)}>
            <Upload aria-hidden="true" />
            Upload
          </Button>
        ) : null
      }
    >
      {customer.documents.length === 0 ? (
        <EmptyState title="No documents uploaded" description="Upload ID images, a selfie or proof of address as JPEG, PNG or PDF (max 10 MB)." />
      ) : (
        <ul className="divide-y">
          {customer.documents.map((document) => (
            <li key={document.id} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
              <div className="grid gap-0.5">
                <p className="flex items-center gap-2 font-medium">
                  {humanize(document.documentType)}
                  <StatusBadge status={document.reviewStatus} />
                </p>
                <p className="text-muted-foreground">
                  {document.fileName} · {formatBytes(document.sizeBytes)} · uploaded {formatDateTime(document.uploadedAt)} · scan {humanize(document.scanStatus)}
                </p>
                {document.reviewNote ? <p className="text-xs text-muted-foreground">Review note: {document.reviewNote}</p> : null}
              </div>
              <div className="flex gap-2">
                {canView ? (
                  <Button size="sm" variant="ghost" onClick={() => setViewing(document)}>
                    View
                  </Button>
                ) : null}
                {canReview && document.reviewStatus === 'PENDING_REVIEW' ? (
                  <Button size="sm" variant="outline" onClick={() => setReviewing(document)}>
                    Review
                  </Button>
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      )}
      <UploadDialog open={uploading} customer={customer} onClose={() => setUploading(false)} onUploaded={refresh} />
      <ViewDialog customerId={customer.id} document={viewing} onClose={() => setViewing(null)} />
      <ReviewDialog customerId={customer.id} document={reviewing} onClose={() => setReviewing(null)} onReviewed={refresh} />
    </Section>
  );
}

function UploadDialog({ open, customer, onClose, onUploaded }: { open: boolean; customer: CustomerDetail; onClose: () => void; onUploaded: () => void }) {
  const [documentType, setDocumentType] = useState<string>(customer.customerType === 'BUSINESS' ? 'BUSINESS_REGISTRATION' : 'ID_FRONT');
  const [file, setFile] = useState<File | null>(null);
  const [problem, setProblem] = useState<string>();
  const upload = useMutation({
    mutationFn: (form: FormData) => bff(`/customers/${customer.id}/documents`, { method: 'POST', body: form }),
    onSuccess: () => {
      setFile(null);
      onUploaded();
      onClose();
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setProblem(undefined);
    if (!file) {
      setProblem('Choose a file');
      return;
    }
    // Early feedback only: the backend checks the real content (magic bytes), size and malware-scan status.
    if (file.size > MAX_BYTES) {
      setProblem('The file is larger than 10 MB');
      return;
    }
    if (file.type && !ACCEPTED.includes(file.type)) {
      setProblem('Use a JPEG, PNG or PDF file');
      return;
    }
    // documentType is read as a request parameter, which Spring also takes from multipart form fields.
    const form = new FormData();
    form.set('documentType', documentType);
    form.set('file', file);
    upload.mutate(form);
  }

  return (
    <Modal open={open} onClose={onClose} busy={upload.isPending} title="Upload document" description="JPEG, PNG or PDF up to 10 MB. Files are encrypted at rest.">
      <form className="grid gap-4" onSubmit={submit} noValidate>
        {upload.isError ? <Alert tone="danger">{errorMessage(upload.error)}</Alert> : null}
        <FormField label="Document type" required>
          {(control) => (
            <Select {...control} value={documentType} onChange={(event) => setDocumentType(event.target.value)}>
              {DOCUMENT_TYPES.map((type) => (
                <option key={type} value={type}>
                  {humanize(type)}
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="File" error={problem} required>
          {(control) => <Input {...control} type="file" accept={ACCEPTED.join(',')} onChange={(event) => setFile(event.target.files?.[0] ?? null)} />}
        </FormField>
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={upload.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={upload.isPending}>
            Upload
          </Button>
        </div>
      </form>
    </Modal>
  );
}

/**
 * Fetches the document through the BFF into a blob URL. Nothing is cached by the browser (no-store) and the URL is
 * revoked when the dialog closes. Each view is audited by the backend.
 */
function ViewDialog({ customerId, document, onClose }: { customerId: string; document: CustomerDocument | null; onClose: () => void }) {
  const [url, setUrl] = useState<string>();
  const [error, setError] = useState<string>();
  const urlRef = useRef<string | undefined>(undefined);

  useEffect(() => {
    if (!document) {
      return;
    }
    const controller = new AbortController();
    setError(undefined);
    fetch(`/api/bff/customers/${customerId}/documents/${document.id}/content`, {
      headers: { [BFF_HEADER]: BFF_HEADER_VALUE },
      credentials: 'same-origin',
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          const body = (await response.json().catch(() => null)) as { code?: string; message?: string } | null;
          throw new ApiError(response.status, body);
        }
        // The blob's type comes from an allow-list, never from the response, so no HTML/SVG can render in our origin.
        const type = ACCEPTED.includes(document.contentType) ? document.contentType : 'application/octet-stream';
        const blob = new Blob([await response.arrayBuffer()], { type });
        urlRef.current = URL.createObjectURL(blob);
        setUrl(urlRef.current);
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) {
          setError(errorMessage(caught));
        }
      });
    return () => {
      controller.abort();
      if (urlRef.current) {
        URL.revokeObjectURL(urlRef.current);
        urlRef.current = undefined;
      }
      setUrl(undefined);
    };
  }, [customerId, document]);

  return (
    <Modal open={document !== null} onClose={onClose} title={document ? humanize(document.documentType) : ''} description={document?.fileName} className="max-w-4xl">
      {error ? <Alert tone="danger">{error}</Alert> : null}
      {url && document ? (
        document.contentType === 'application/pdf' ? (
          <iframe title={document.fileName} src={url} className="h-[70vh] w-full rounded border" />
        ) : (
          // eslint-disable-next-line @next/next/no-img-element -- blob URL of a private document; next/image can't optimise it
          <img src={url} alt={humanize(document.documentType)} className="max-h-[70vh] w-full rounded border object-contain" />
        )
      ) : !error ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : null}
      {url && document ? (
        <a href={url} download={document.fileName} className="text-sm text-primary underline">
          Download
        </a>
      ) : null}
    </Modal>
  );
}

function ReviewDialog({ customerId, document, onClose, onReviewed }: { customerId: string; document: CustomerDocument | null; onClose: () => void; onReviewed: () => void }) {
  const [note, setNote] = useState('');
  const review = useMutation({
    mutationFn: (decision: 'ACCEPTED' | 'REJECTED') =>
      bff(`/customers/${customerId}/documents/${document?.id}/review`, { method: 'POST', body: { decision, note: note.trim() || undefined } }),
    onSuccess: () => {
      setNote('');
      onReviewed();
      onClose();
    },
  });
  return (
    <Modal
      open={document !== null}
      onClose={onClose}
      busy={review.isPending}
      title="Review document"
      description={document ? `${humanize(document.documentType)} · ${document.fileName}` : undefined}
      footer={
        <>
          <Button variant="destructive" loading={review.isPending && review.variables === 'REJECTED'} disabled={review.isPending || note.trim() === ''} onClick={() => review.mutate('REJECTED')}>
            Reject
          </Button>
          <Button loading={review.isPending && review.variables === 'ACCEPTED'} disabled={review.isPending} onClick={() => review.mutate('ACCEPTED')}>
            Accept
          </Button>
        </>
      }
    >
      {review.isError ? <Alert tone="danger">{errorMessage(review.error)}</Alert> : null}
      <FormField label="Note" hint="Required when rejecting; shown to the officer who captured the document.">
        {(control) => <Textarea {...control} value={note} maxLength={500} onChange={(event) => setNote(event.target.value)} />}
      </FormField>
    </Modal>
  );
}
