package com.company.banking.customer.service;

import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.security.ActorType;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.security.Permissions;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.entity.Customer;
import com.company.banking.customer.entity.CustomerStatus;
import com.company.banking.customer.exception.CustomerErrorCode;
import com.company.banking.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Central access rules for customer data:
 * <ul>
 *   <li>customers outside the caller's branch scope do not exist for the caller (404); a customer signed in to the
 *       app reaches only their own record;</li>
 *   <li>a customer changes their own record only while signing up (still being onboarded), never once
 *       established;</li>
 *   <li>changes need {@code customer.edit}, or {@code customer.create} while the customer is still being onboarded
 *       (field and loan officers capture data for new customers but can't alter established ones);</li>
 *   <li>nothing changes while KYC is under review, so the reviewer decides on a stable record;</li>
 *   <li>every change locks the customer row, so concurrent edits of one customer serialise.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CustomerAccessGuard {

    private final CustomerRepository customerRepository;

    public Customer loadForRead(UUID customerId) {
        return inScope(customerRepository.findByTenantIdAndId(TenantContext.requireTenantId(), customerId));
    }

    /**
     * For data capture (profile, contacts, documents, identifications, opening and submitting KYC).
     */
    public Customer lockForCapture(UUID customerId) {
        AuthenticatedActor actor = CurrentActor.require();
        Customer customer = lockInScope(customerId);
        boolean onboarding = customer.getStatus() == CustomerStatus.PENDING;
        if (actor.type() == ActorType.CUSTOMER) {
            if (!onboarding) {
                throw new BankingException(CommonErrorCode.ACCESS_DENIED,
                        "Your details can be changed at a branch once you are a customer.");
            }
        } else if (actor.type() != ActorType.SYSTEM && !actor.hasPermission(Permissions.CUSTOMER_EDIT)
                && !(onboarding && actor.hasPermission(Permissions.CUSTOMER_CREATE))) {
            throw new BankingException(CommonErrorCode.ACCESS_DENIED,
                    "Changing an onboarded customer requires the customer.edit permission.");
        }
        if (customer.isKycUnderReview()) {
            throw new BankingException(CustomerErrorCode.KYC_UNDER_REVIEW);
        }
        return customer;
    }

    /**
     * For decisions taken by other roles (status changes, KYC review outcomes): scope and row lock only; the
     * endpoint's permission has already been checked.
     */
    public Customer lockInScope(UUID customerId) {
        return inScope(customerRepository.lockByTenantIdAndId(TenantContext.requireTenantId(), customerId));
    }

    public static void assertIdentityEditable(Customer customer) {
        if (customer.isKycUnderReview()) {
            throw new BankingException(CustomerErrorCode.KYC_UNDER_REVIEW);
        }
        if (customer.isIdentityDataLocked()) {
            throw new BankingException(CustomerErrorCode.KYC_DATA_LOCKED);
        }
    }

    private static Customer inScope(Optional<Customer> customer) {
        AuthenticatedActor actor = CurrentActor.require();
        BranchScope scope = actor.branchScope();
        return customer.filter(found -> actor.type() == ActorType.CUSTOMER
                        ? found.getId().equals(actor.id())
                        : scope.permits(found.getHomeBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Customer"));
    }
}
