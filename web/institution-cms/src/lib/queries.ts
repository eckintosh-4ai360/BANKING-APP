'use client';

import { bff, Permission, query, type Branch, type KycTier, type Page, type Product, type Role } from '@banking/api';
import { useQuery } from '@tanstack/react-query';
import { useCan } from './me';

/** Active branches for pickers and for showing branch names (null when the user may not list branches). */
export function useBranches() {
  const allowed = useCan(Permission.branchView);
  const result = useQuery({
    queryKey: ['branches', 'options'],
    queryFn: ({ signal }) => bff<Page<Branch>>(`/branches${query({ size: 100 })}`, { signal }),
    enabled: allowed,
    staleTime: 5 * 60_000,
  });
  const branches = result.data?.items ?? [];
  return {
    allowed,
    branches,
    activeBranches: branches.filter((branch) => branch.status === 'ACTIVE'),
    isLoading: allowed && result.isLoading,
    nameOf: (id: string | null | undefined) => {
      if (!id) {
        return '—';
      }
      const branch = branches.find((candidate) => candidate.id === id);
      return branch ? `${branch.name} (${branch.code})` : 'Other branch';
    },
  };
}

export function useKycTiers() {
  return useQuery({
    queryKey: ['kyc-tiers'],
    queryFn: ({ signal }) => bff<KycTier[]>('/kyc/tiers', { signal }),
    staleTime: 5 * 60_000,
  });
}

export interface IdentificationType {
  code: string;
  name: string;
  appliesTo: 'INDIVIDUAL' | 'BUSINESS' | 'ANY';
  formatRegex: string | null;
  formatHint: string | null;
  requiresExpiry: boolean;
  supportsElectronicVerification: boolean;
  active: boolean;
  sortOrder: number;
  version: number;
}

export function useIdentificationTypes() {
  return useQuery({
    queryKey: ['identification-types'],
    queryFn: ({ signal }) => bff<IdentificationType[]>('/kyc/identification-types', { signal }),
    staleTime: 5 * 60_000,
  });
}

export function useRoleOptions() {
  const allowed = useCan(Permission.roleView);
  return useQuery({
    queryKey: ['roles'],
    queryFn: ({ signal }) => bff<Role[]>('/roles', { signal }),
    enabled: allowed,
  });
}

/** Deposit products (for opening accounts and the products screens). */
export function useProducts() {
  const allowed = useCan(Permission.productView, Permission.productManage);
  return useQuery({
    queryKey: ['products'],
    queryFn: ({ signal }) => bff<Product[]>('/products', { signal }),
    enabled: allowed,
    staleTime: 60_000,
  });
}

/** Total count behind a filter (page size 1), for dashboard figures. */
export function useCount(path: string, params: Record<string, string>, enabled: boolean) {
  return useQuery({
    queryKey: ['count', path, params],
    queryFn: ({ signal }) => bff<Page<unknown>>(`${path}${query({ ...params, size: 1 })}`, { signal }),
    enabled,
    select: (page) => page.totalItems,
  });
}
