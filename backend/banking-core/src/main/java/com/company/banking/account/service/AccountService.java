package com.company.banking.account.service;

import com.company.banking.account.dto.AccountResponse;
import com.company.banking.account.dto.AccountResponse.AccountHolderResponse;
import com.company.banking.account.dto.AccountSummary;
import com.company.banking.account.dto.ChangeAccountStatusRequest;
import com.company.banking.account.dto.CloseAccountRequest;
import com.company.banking.account.dto.OpenAccountRequest;
import com.company.banking.account.dto.OpenAccountRequest.HolderRequest;
import com.company.banking.account.dto.PostingAccount;
import com.company.banking.account.entity.Account;
import com.company.banking.account.entity.AccountHolder;
import com.company.banking.account.exception.AccountErrorCode;
import com.company.banking.account.model.AccountStatus;
import com.company.banking.account.model.HolderRole;
import com.company.banking.account.model.OwnershipType;
import com.company.banking.account.repository.AccountHolderRepository;
import com.company.banking.account.repository.AccountRepository;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.branch.dto.BranchResponse;
import com.company.banking.branch.service.BranchService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.sequence.CheckDigits;
import com.company.banking.common.sequence.SequenceService;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.service.CustomerService;
import com.company.banking.kyc.service.KycTierService;
import com.company.banking.ledger.dto.BalanceSnapshot;
import com.company.banking.ledger.dto.LedgerAccountInfo;
import com.company.banking.ledger.dto.OpenLedgerAccountCommand;
import com.company.banking.ledger.model.LedgerAccountType;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.product.dto.ProductRef;
import com.company.banking.product.dto.ProductTerms;
import com.company.banking.product.service.ProductService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer deposit accounts: opening under a product's published terms, lookup with live balances, and the
 * lifecycle (restrict, freeze, reactivate, close). Money only moves through the transaction module and the posting
 * engine; nothing here changes a balance.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    static final String RESOURCE = "ACCOUNT";
    private static final String ACCOUNT_NUMBER_SEQUENCE = "ACCOUNT";
    private static final String LEDGER_OWNER_TYPE = "ACCOUNT";
    private static final int LEDGER_NAME_MAX = 120;
    private static final int TITLE_MAX = 150;

    private final AccountRepository accountRepository;
    private final AccountHolderRepository holderRepository;
    private final AccountAccessGuard accessGuard;
    private final ProductService productService;
    private final CustomerService customerService;
    private final KycTierService kycTierService;
    private final BranchService branchService;
    private final LedgerAccountService ledgerAccounts;
    private final BusinessDateService businessDates;
    private final SequenceService sequenceService;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Opens an account. Every holder row is locked (in id order, so concurrent openings cannot deadlock) until the
     * account exists, so no holder can be closed or frozen half-way. The account starts PENDING when the product
     * requires an opening deposit and becomes ACTIVE once funded.
     */
    @Transactional
    public AccountResponse open(OpenAccountRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        ProductTerms terms = productService.termsForNewAccount(request.productId());
        OwnershipType ownership = OwnershipType.valueOf(request.ownershipType());

        Map<UUID, HolderRole> roles = new LinkedHashMap<>();
        roles.put(request.customerId(), HolderRole.PRIMARY);
        List<HolderRequest> otherHolders = request.otherHolders() == null ? List.of() : request.otherHolders();
        for (HolderRequest holder : otherHolders) {
            if (roles.putIfAbsent(holder.customerId(), HolderRole.valueOf(holder.role())) != null) {
                throw new BankingException(AccountErrorCode.INVALID_OWNERSHIP, "A customer can hold an account once.");
            }
        }
        Map<UUID, CustomerSummary> customers = new HashMap<>();
        roles.keySet().stream().sorted()
                .forEach(customerId -> customers.put(customerId, customerService.lockForNewHolding(customerId)));
        requireOwnership(ownership, roles, customers);
        requireEligible(terms, customers.values());

        CustomerSummary primary = customers.get(request.customerId());
        UUID branchId = request.branchId() != null ? request.branchId() : primary.homeBranchId();
        if (!CurrentActor.require().branchScope().permits(branchId)) {
            throw new ResourceNotFoundException("Branch");
        }
        BranchResponse branch = branchService.getForInternalUse(branchId);
        if (!branch.isActive()) {
            throw new BankingException(AccountErrorCode.BRANCH_NOT_AVAILABLE);
        }

        UUID accountId = UuidV7.next();
        String accountNumber = CheckDigits.appendLuhn(
                "1" + String.format("%08d", sequenceService.next(ACCOUNT_NUMBER_SEQUENCE)));
        String title = request.title() == null || request.title().isBlank()
                ? truncate(primary.displayName(), TITLE_MAX) : request.title().trim();
        LedgerAccountInfo ledger = ledgerAccounts.open(new OpenLedgerAccountCommand(terms.depositGlId(), null,
                branchId, terms.currency(), LedgerAccountType.CUSTOMER_DEPOSIT, true,
                truncate(accountNumber + " " + title, LEDGER_NAME_MAX), LEDGER_OWNER_TYPE, accountId));
        Instant now = clock.instant();
        Account account = accountRepository.saveAndFlush(new Account(accountId, tenantId, accountNumber,
                primary.id(), terms.productId(), terms.versionId(), ledger.id(), branchId, terms.currency(), title,
                ownership, businessDates.today(), terms.minOpeningBalance().signum() > 0, now));
        UUID actor = CurrentActor.currentActorId().orElse(null);
        roles.forEach((customerId, role) ->
                holderRepository.save(new AccountHolder(tenantId, accountId, customerId, role, now, actor)));
        holderRepository.flush();

        auditService.record(AuditEvent.builder("ACCOUNT_OPENED", RESOURCE)
                .resourceId(accountId)
                .resourceReference(accountNumber)
                .branchId(branchId)
                .after(Map.of("status", account.getStatus(), "productCode", terms.productCode(),
                        "productVersion", terms.versionNo(), "currency", terms.currency(),
                        "ownershipType", ownership, "holders", roles))
                .build());
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID accountId) {
        return toResponse(accessGuard.loadForRead(accountId));
    }

    @Transactional(readOnly = true)
    public AccountResponse getByNumber(String accountNumber) {
        Account account = accountRepository.findByTenantIdAndAccountNumber(TenantContext.requireTenantId(),
                        accountNumber)
                .filter(found -> CurrentActor.require().branchScope().permits(found.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Account"));
        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountSummary> search(AccountSearchCriteria criteria, PageRequest page) {
        UUID tenantId = TenantContext.requireTenantId();
        BranchScope scope = CurrentActor.require().branchScope();
        Specification<Account> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (!scope.allBranches()) {
                predicates.add(scope.branchIds().isEmpty()
                        ? cb.disjunction()
                        : root.get("branchId").in(scope.branchIds()));
            }
            if (criteria.accountNumber() != null && !criteria.accountNumber().isBlank()) {
                predicates.add(cb.equal(root.get("accountNumber"), criteria.accountNumber().trim()));
            }
            if (criteria.branchId() != null) {
                predicates.add(cb.equal(root.get("branchId"), criteria.branchId()));
            }
            if (criteria.productId() != null) {
                predicates.add(cb.equal(root.get("productId"), criteria.productId()));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), AccountStatus.valueOf(criteria.status())));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<Account> found = accountRepository.findAll(specification, PageRequest.of(page.getPageNumber(),
                page.getPageSize(), Sort.by("accountNumber")));
        return toSummaries(found);
    }

    /**
     * Accounts the customer holds (as primary, joint holder or signatory) that the caller may see.
     */
    @Transactional(readOnly = true)
    public List<AccountSummary> heldBy(UUID customerId) {
        CustomerSummary customer = customerService.getSummary(customerId);
        BranchScope scope = CurrentActor.require().branchScope();
        List<Account> held = accountRepository.findHeldBy(TenantContext.requireTenantId(), customer.id()).stream()
                .filter(account -> scope.permits(account.getBranchId()))
                .toList();
        return toSummaries(held);
    }

    /**
     * Restrict, freeze or (re)activate. A PENDING account can only be activated once its opening deposit is in.
     */
    @Transactional
    public AccountResponse changeStatus(UUID accountId, ChangeAccountStatusRequest request) {
        Account account = accessGuard.lockInScope(accountId);
        ConcurrentModificationException.assertVersion(request.version(), account.getVersion());
        AccountStatus previous = account.getStatus();
        AccountStatus target = AccountStatus.valueOf(request.status());
        if (previous == AccountStatus.CLOSED) {
            throw new BankingException(AccountErrorCode.ACCOUNT_CLOSED);
        }
        if (previous == AccountStatus.PENDING && target == AccountStatus.ACTIVE) {
            ProductTerms terms = productService.terms(account.getProductVersionId());
            BalanceSnapshot balance = ledgerAccounts.lockBalance(account.getLedgerAccountId());
            if (balance.ledgerBalance().compareTo(terms.minOpeningBalance()) < 0) {
                throw new BankingException(AccountErrorCode.OPENING_BALANCE_NOT_MET);
            }
        }
        String reason = request.reason().trim();
        account.changeStatus(target, reason, clock.instant());
        accountRepository.saveAndFlush(account);
        auditService.record(AuditEvent.builder("ACCOUNT_STATUS_CHANGED", RESOURCE)
                .resourceId(accountId)
                .resourceReference(account.getAccountNumber())
                .branchId(account.getBranchId())
                .before(Map.of("status", previous))
                .after(Map.of("status", target))
                .metadata("reason", reason)
                .build());
        return toResponse(account);
    }

    /**
     * Closes an empty account (zero balance, no active holds) and its ledger account. Paying out the remaining
     * balance is a withdrawal or transfer before closing, so it is posted and audited like any other.
     */
    @Transactional
    public AccountResponse close(UUID accountId, CloseAccountRequest request) {
        Account account = accessGuard.lockInScope(accountId);
        ConcurrentModificationException.assertVersion(request.version(), account.getVersion());
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BankingException(AccountErrorCode.ACCOUNT_CLOSED);
        }
        BalanceSnapshot balance = ledgerAccounts.lockBalance(account.getLedgerAccountId());
        if (balance.ledgerBalance().signum() != 0 || balance.holdAmount().signum() != 0) {
            throw new BankingException(AccountErrorCode.ACCOUNT_NOT_EMPTY);
        }
        AccountStatus previous = account.getStatus();
        String reason = request.reason().trim();
        account.close(businessDates.today(), reason, clock.instant());
        ledgerAccounts.close(account.getLedgerAccountId());
        accountRepository.saveAndFlush(account);
        auditService.record(AuditEvent.builder("ACCOUNT_CLOSED", RESOURCE)
                .resourceId(accountId)
                .resourceReference(account.getAccountNumber())
                .branchId(account.getBranchId())
                .before(Map.of("status", previous))
                .after(Map.of("status", AccountStatus.CLOSED))
                .metadata("reason", reason)
                .build());
        return toResponse(account);
    }

    // ------------------------------------------------------------------------------ for the transaction module

    /**
     * Locks the accounts of a money movement (ascending id order, so two transfers between the same accounts in
     * opposite directions cannot deadlock). Accounts outside the caller's branch scope are not found. The locks
     * serialise every movement, hold and lifecycle change of an account until the caller commits.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, PostingAccount> lockForPosting(Collection<UUID> accountIds) {
        Map<UUID, PostingAccount> locked = new LinkedHashMap<>();
        accountIds.stream().distinct().sorted()
                .forEach(accountId -> locked.put(accountId, toPostingAccount(accessGuard.lockInScope(accountId))));
        return locked;
    }

    /**
     * After money moved: records the activity and activates a PENDING account once its opening deposit is in.
     *
     * @param ledgerBalance the account balance after the posting
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPosting(UUID accountId, BigDecimal ledgerBalance) {
        UUID tenantId = TenantContext.requireTenantId();
        Account account = accountRepository.findByTenantIdAndId(tenantId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account"));
        Instant now = clock.instant();
        // The posting holds the business date's share lock, so this is the date it was posted on.
        LocalDate businessDate = businessDates.today();
        if (account.getStatus() == AccountStatus.PENDING && ledgerBalance.compareTo(
                productService.terms(account.getProductVersionId()).minOpeningBalance()) >= 0) {
            account.changeStatus(AccountStatus.ACTIVE, "Opening deposit received", now);
            account.recordActivity(now, businessDate);
            accountRepository.saveAndFlush(account);
            auditService.record(AuditEvent.builder("ACCOUNT_ACTIVATED", RESOURCE)
                    .resourceId(accountId)
                    .resourceReference(account.getAccountNumber())
                    .branchId(account.getBranchId())
                    .before(Map.of("status", AccountStatus.PENDING))
                    .after(Map.of("status", AccountStatus.ACTIVE))
                    .build());
            return;
        }
        accountRepository.touchActivity(tenantId, accountId, now, businessDate);
    }

    /**
     * An account the caller may see (404 otherwise), for listing its transactions.
     */
    @Transactional(readOnly = true)
    public PostingAccount requireReadable(UUID accountId) {
        return toPostingAccount(accessGuard.loadForRead(accountId));
    }

    /**
     * Whether the caller may see the account. Does not throw, so callers can check several accounts inside one
     * transaction without marking it for rollback.
     */
    @Transactional(readOnly = true)
    public boolean isReadable(UUID accountId) {
        BranchScope scope = CurrentActor.require().branchScope();
        return accountRepository.findByTenantIdAndId(TenantContext.requireTenantId(), accountId)
                .filter(account -> scope.permits(account.getBranchId()))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> accountNumbers(Collection<UUID> accountIds) {
        UUID tenantId = TenantContext.requireTenantId();
        Map<UUID, String> numbers = new HashMap<>();
        accountRepository.findAllById(accountIds).stream()
                .filter(account -> account.getTenantId().equals(tenantId))
                .forEach(account -> numbers.put(account.getId(), account.getAccountNumber()));
        return numbers;
    }

    private static PostingAccount toPostingAccount(Account account) {
        return new PostingAccount(account.getId(), account.getAccountNumber(), account.getTitle(),
                account.getCustomerId(), account.getLedgerAccountId(), account.getBranchId(), account.getCurrency(),
                account.getStatus().name(), account.getProductVersionId(), account.getStatus().allowsCredit(),
                account.getStatus().allowsDebit());
    }

    // ---------------------------------------------------------------------------------------------------------

    private static void requireOwnership(OwnershipType ownership, Map<UUID, HolderRole> roles,
                                         Map<UUID, CustomerSummary> customers) {
        boolean primaryIsBusiness = roles.entrySet().stream()
                .filter(entry -> entry.getValue() == HolderRole.PRIMARY)
                .allMatch(entry -> "BUSINESS".equals(customers.get(entry.getKey()).customerType()));
        List<UUID> others = roles.entrySet().stream()
                .filter(entry -> entry.getValue() != HolderRole.PRIMARY)
                .map(Map.Entry::getKey)
                .toList();
        boolean othersAreIndividuals = others.stream()
                .allMatch(id -> "INDIVIDUAL".equals(customers.get(id).customerType()));
        HolderRole expectedRole = ownership == OwnershipType.BUSINESS ? HolderRole.SIGNATORY : HolderRole.JOINT;
        boolean rolesFit = others.stream().allMatch(id -> roles.get(id) == expectedRole);
        boolean valid = switch (ownership) {
            case SINGLE -> !primaryIsBusiness && others.isEmpty();
            case JOINT_ANY, JOINT_ALL -> !primaryIsBusiness && !others.isEmpty() && rolesFit && othersAreIndividuals;
            case BUSINESS -> primaryIsBusiness && rolesFit && othersAreIndividuals;
        };
        if (!valid) {
            throw new BankingException(AccountErrorCode.INVALID_OWNERSHIP);
        }
    }

    private void requireEligible(ProductTerms terms, Iterable<CustomerSummary> holders) {
        int requiredRank = terms.requiredKycTier() == null ? 0
                : kycTierService.requireActiveRank(terms.requiredKycTier());
        for (CustomerSummary holder : holders) {
            // ACTIVE is only reachable through KYC approval, so every holder has been verified.
            if (!"ACTIVE".equals(holder.status())) {
                throw new BankingException(AccountErrorCode.CUSTOMER_NOT_ELIGIBLE);
            }
            if (requiredRank > 0 && (holder.kycTierCode() == null
                    || kycTierService.rankOf(holder.kycTierCode()) < requiredRank)) {
                throw new BankingException(AccountErrorCode.KYC_TIER_TOO_LOW);
            }
        }
    }

    private AccountResponse toResponse(Account account) {
        UUID tenantId = account.getTenantId();
        List<AccountHolder> holders = new ArrayList<>(holderRepository.findCurrent(tenantId, account.getId()));
        holders.sort(Comparator.comparing(AccountHolder::getRole));
        Map<UUID, CustomerSummary> customers = customerService.summaries(
                holders.stream().map(AccountHolder::getCustomerId).toList());
        List<AccountHolderResponse> holderResponses = holders.stream()
                .map(holder -> {
                    CustomerSummary customer = customers.get(holder.getCustomerId());
                    return new AccountHolderResponse(holder.getCustomerId(),
                            customer == null ? null : customer.customerNumber(),
                            customer == null ? null : customer.displayName(), holder.getRole().name());
                })
                .toList();
        ProductRef product = productService.refs(List.of(account.getProductId())).get(account.getProductId());
        BalanceSnapshot balance = ledgerAccounts.balance(account.getLedgerAccountId());
        return new AccountResponse(account.getId(), account.getAccountNumber(), account.getTitle(),
                account.getCustomerId(), account.getProductId(), product.code(), product.name(),
                product.productType(), account.getProductVersionId(), account.getBranchId(), account.getCurrency(),
                account.getStatus().name(), account.getStatusReason(), account.getOwnershipType().name(),
                holderResponses, balance.ledgerBalance(), balance.holdAmount(), balance.availableBalance(),
                balance.overdraftLimit(), account.getOpenedOn(), account.getActivatedAt(), account.getClosedOn(),
                account.getLastActivityAt(), account.getVersion());
    }

    private PageResponse<AccountSummary> toSummaries(Page<Account> page) {
        Map<UUID, AccountSummary> summaries = summaries(page.getContent());
        return PageResponse.from(page, account -> summaries.get(account.getId()));
    }

    private List<AccountSummary> toSummaries(List<Account> accounts) {
        Map<UUID, AccountSummary> summaries = summaries(accounts);
        return accounts.stream().map(account -> summaries.get(account.getId())).toList();
    }

    private Map<UUID, AccountSummary> summaries(List<Account> accounts) {
        Map<UUID, ProductRef> products = productService.refs(accounts.stream().map(Account::getProductId)
                .distinct().toList());
        Map<UUID, BalanceSnapshot> balances = ledgerAccounts.balances(accounts.stream()
                .map(Account::getLedgerAccountId).toList());
        Map<UUID, AccountSummary> result = new HashMap<>();
        for (Account account : accounts) {
            ProductRef product = products.get(account.getProductId());
            BalanceSnapshot balance = balances.get(account.getLedgerAccountId());
            result.put(account.getId(), new AccountSummary(account.getId(), account.getAccountNumber(),
                    account.getTitle(), account.getCustomerId(), product.code(), product.productType(),
                    account.getBranchId(), account.getCurrency(), account.getStatus().name(),
                    balance.ledgerBalance(), balance.availableBalance(), account.getOpenedOn()));
        }
        return result;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record AccountSearchCriteria(String accountNumber, UUID branchId, UUID productId, String status) {
    }
}
