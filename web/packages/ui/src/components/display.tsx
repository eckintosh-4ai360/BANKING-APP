import { cva, type VariantProps } from 'class-variance-authority';
import { CircleAlert, CircleCheck, Info, TriangleAlert } from 'lucide-react';
import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '../lib/cn';
import { humanize } from '../lib/format';

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('rounded-lg border bg-card text-card-foreground shadow-xs', className)} {...props} />;
}

export function CardHeader({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex flex-col gap-1 border-b px-5 py-4', className)} {...props} />;
}

export function CardTitle({ className, ...props }: HTMLAttributes<HTMLHeadingElement>) {
  return <h2 className={cn('text-base font-semibold', className)} {...props} />;
}

export function CardDescription({ className, ...props }: HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn('text-sm text-muted-foreground', className)} {...props} />;
}

export function CardContent({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('px-5 py-4', className)} {...props} />;
}

export function CardFooter({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex items-center justify-end gap-2 border-t px-5 py-3', className)} {...props} />;
}

export const badgeVariants = cva('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap', {
  variants: {
    tone: {
      neutral: 'bg-muted text-muted-foreground',
      info: 'bg-info-soft text-primary',
      success: 'bg-success-soft text-success',
      warning: 'bg-warning-soft text-warning',
      danger: 'bg-danger-soft text-destructive',
    },
  },
  defaultVariants: { tone: 'neutral' },
});

export type BadgeTone = NonNullable<VariantProps<typeof badgeVariants>['tone']>;

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, tone, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ tone }), className)} {...props} />;
}

const STATUS_TONES: Record<string, BadgeTone> = {
  ACTIVE: 'success',
  VERIFIED: 'success',
  APPROVED: 'success',
  ACCEPTED: 'success',
  PASS: 'success',
  SUCCESS: 'success',
  LOW: 'success',
  CLEAN: 'success',
  PENDING: 'info',
  OPEN: 'info',
  IN_PROGRESS: 'info',
  ONBOARDING: 'info',
  PENDING_REVIEW: 'warning',
  RETURNED: 'warning',
  RESTRICTED: 'warning',
  DORMANT: 'warning',
  SUSPENDED: 'warning',
  MEDIUM: 'warning',
  INCONCLUSIVE: 'warning',
  INACTIVE: 'neutral',
  NOT_STARTED: 'neutral',
  UNASSESSED: 'neutral',
  CANCELLED: 'neutral',
  UNVERIFIED: 'neutral',
  SKIPPED: 'neutral',
  FROZEN: 'danger',
  CLOSED: 'danger',
  REJECTED: 'danger',
  TERMINATED: 'danger',
  EXPIRED: 'danger',
  FAIL: 'danger',
  FAILED: 'danger',
  FAILURE: 'danger',
  DENIED: 'danger',
  ERROR: 'danger',
  HIGH: 'danger',
  INFECTED: 'danger',
};

export function statusTone(status: string | null | undefined): BadgeTone {
  return (status && STATUS_TONES[status]) || 'neutral';
}

/** A badge whose colour follows the meaning of a backend status code. */
export function StatusBadge({ status, className }: { status: string | null | undefined; className?: string }) {
  return (
    <Badge tone={statusTone(status)} className={className}>
      {humanize(status)}
    </Badge>
  );
}

const alertVariants = cva('flex gap-3 rounded-md border px-4 py-3 text-sm [&>svg]:mt-0.5 [&>svg]:size-4 [&>svg]:shrink-0', {
  variants: {
    tone: {
      info: 'border-primary/30 bg-info-soft',
      success: 'border-success/30 bg-success-soft',
      warning: 'border-warning/30 bg-warning-soft',
      danger: 'border-destructive/30 bg-danger-soft text-destructive',
    },
  },
  defaultVariants: { tone: 'info' },
});

const ALERT_ICONS = { info: Info, success: CircleCheck, warning: TriangleAlert, danger: CircleAlert } as const;

export interface AlertProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'>, VariantProps<typeof alertVariants> {
  title?: ReactNode;
}

export function Alert({ className, tone, title, children, ...props }: AlertProps) {
  const Icon = ALERT_ICONS[tone ?? 'info'];
  return (
    <div role={tone === 'danger' ? 'alert' : 'status'} className={cn(alertVariants({ tone }), className)} {...props}>
      <Icon aria-hidden="true" />
      <div className="grid gap-1">
        {title ? <p className="font-medium">{title}</p> : null}
        {children ? <div>{children}</div> : null}
      </div>
    </div>
  );
}

export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div aria-hidden="true" className={cn('animate-pulse rounded-md bg-muted', className)} {...props} />;
}

export function EmptyState({ title, description, action }: { title: ReactNode; description?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-6 py-12 text-center">
      <p className="font-medium">{title}</p>
      {description ? <p className="max-w-md text-sm text-muted-foreground">{description}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
}

export function PageHeader({ title, description, actions }: { title: ReactNode; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div className="grid gap-1">
        <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}

export interface DetailItem {
  label: ReactNode;
  value: ReactNode;
}

/** Label/value pairs for detail pages. Empty values render as an em dash. */
export function DetailList({ items, columns = 2 }: { items: DetailItem[]; columns?: 1 | 2 | 3 }) {
  return (
    <dl
      className={cn(
        'grid gap-x-6 gap-y-4',
        columns === 1 && 'grid-cols-1',
        columns === 2 && 'grid-cols-1 sm:grid-cols-2',
        columns === 3 && 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3',
      )}
    >
      {items.map((item, index) => (
        <div key={index} className="grid gap-0.5">
          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{item.label}</dt>
          <dd className="text-sm break-words">{item.value === null || item.value === undefined || item.value === '' ? '—' : item.value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function StatCard({ label, value, hint }: { label: ReactNode; value: ReactNode; hint?: ReactNode }) {
  return (
    <Card>
      <CardContent className="grid gap-1">
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="text-2xl font-semibold tabular-nums">{value}</p>
        {hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
      </CardContent>
    </Card>
  );
}
