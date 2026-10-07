'use client';

import type { InputHTMLAttributes, LabelHTMLAttributes, ReactNode, Ref, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';
import { useId } from 'react';
import { cn } from '../lib/cn';

const controlClass =
  'flex w-full rounded-md border border-input bg-card px-3 text-sm shadow-xs transition-colors ' +
  'placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-ring ' +
  'disabled:cursor-not-allowed disabled:opacity-60 aria-invalid:border-destructive';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  ref?: Ref<HTMLInputElement>;
}

export function Input({ className, type, ...props }: InputProps) {
  return <input type={type ?? 'text'} className={cn(controlClass, 'h-9 py-1', className)} {...props} />;
}

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  ref?: Ref<HTMLTextAreaElement>;
}

export function Textarea({ className, ...props }: TextareaProps) {
  return <textarea className={cn(controlClass, 'min-h-20 py-2', className)} {...props} />;
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  ref?: Ref<HTMLSelectElement>;
}

export function Select({ className, children, ...props }: SelectProps) {
  return (
    <select className={cn(controlClass, 'h-9 py-1 pr-8', className)} {...props}>
      {children}
    </select>
  );
}

export function Label({ className, ...props }: LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={cn('text-sm font-medium leading-none', className)} {...props} />;
}

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: ReactNode;
  ref?: Ref<HTMLInputElement>;
}

export function Checkbox({ label, className, id, ...props }: CheckboxProps) {
  const generated = useId();
  const inputId = id ?? generated;
  return (
    <div className={cn('flex items-center gap-2', className)}>
      <input id={inputId} type="checkbox" className="size-4 rounded border-input accent-primary" {...props} />
      <Label htmlFor={inputId} className="font-normal">
        {label}
      </Label>
    </div>
  );
}

export interface FormFieldProps {
  label: ReactNode;
  /** Validation message; also marks the control invalid for assistive technology. */
  error?: string | undefined;
  hint?: ReactNode;
  required?: boolean;
  className?: string;
  /** Receives the ids to wire the control to its label, hint and error. */
  children: (control: { id: string; 'aria-invalid': boolean; 'aria-describedby': string | undefined }) => ReactNode;
}

/** Label + control + hint + error, with the accessibility attributes wired up. */
export function FormField({ label, error, hint, required, className, children }: FormFieldProps) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined;
  return (
    <div className={cn('grid gap-1.5', className)}>
      <Label htmlFor={id}>
        {label}
        {required ? (
          <span className="ml-0.5 text-destructive" aria-hidden="true">
            *
          </span>
        ) : null}
      </Label>
      {children({ id, 'aria-invalid': Boolean(error), 'aria-describedby': describedBy })}
      {hint && !error ? (
        <p id={hintId} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} role="alert" className="text-xs text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
