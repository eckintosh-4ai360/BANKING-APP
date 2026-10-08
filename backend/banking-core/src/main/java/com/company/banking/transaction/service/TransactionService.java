package com.company.banking.transaction.service;

import com.company.banking.account.dto.PostingAccount;
import com.company.banking.account.service.AccountService;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.branch.service.BranchService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.References;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.idempotency.IdempotencyService;
import com.company.banking.common.idempotency.IdempotencyService.Result;
import com.company.banking.common.outbox.OutboxService;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.BalanceSnapshot;
import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.PostingRequest;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.JournalSource;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.ledger.service.PostingEngine;
import com.company.banking.product.dto.ChargeTerms;
import com.company.banking.product.dto.ProductTerms;
import com.company.banking.product.model.ChargeEvent;
import com.company.banking.product.service.ChargeCalculator;
import com.company.banking.product.service.ProductService;
import com.company.banking.transaction.dto.CashDepositRequest;
import com.company.banking.transaction.dto.CashWithdrawalRequest;
import com.company.banking.transaction.dto.PostedTransactionResponse;
import com.company.banking.transaction.dto.PostedTransactionResponse.BalanceAfter;
import com.company.banking.transaction.dto.TransferRequest;
import com.company.banking.transaction.entity.FinancialTransaction;
import com.company.banking.transaction.exception.TransactionErrorCode;
import com.company.banking.transaction.model.TransactionChannel;
import com.company.banking.transaction.model.TransactionStatus;
import com.company.banking.transaction.model.TransactionType;
import com.company.banking.transaction.repository.FinancialTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cash deposits, cash withdrawals and transfers between accounts (CMS-initiated; until teller drawers arrive in
 * Phase 3, cash posts against the branch cash GL).
 *
 * <p>Every movement runs in one database transaction: the idempotency key is claimed, the accounts are locked, the
 * product rules and charges are applied, the posting engine writes a balanced journal and the transaction record,
 * audit entry and outbox event are written with it. Amounts are exactly what the caller asked for; charges are
 * computed here from the product terms, never taken from the client.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    static final String RESOURCE = "FINANCIAL_TRANSACTION";
    private static final List<TransactionType> OUTGOING = List.of(TransactionType.CASH_WITHDRAWAL,
            TransactionType.TRANSFER);

    private final FinancialTransactionRepository transactions;
    private final AccountService accountService;
    private final ProductService productService;
    private final PostingEngine postingEngine;
    private final LedgerAccountService ledgerAccounts;
    private final CurrencyService currencies;
    private final BranchService branchService;
    private final BusinessDateService businessDates;
    private final IdempotencyService idempotency;
    private final OutboxService outbox;
    private final TransactionMapper mapper;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public Result<PostedTransactionResponse> deposit(String idempotencyKey, CashDepositRequest request) {
        return idempotent(TransactionType.CASH_DEPOSIT, idempotencyKey, request, () -> {
            PostingAccount account = accountService.lockForPosting(List.of(request.accountId()))
                    .get(request.accountId());
            if (!account.creditAllowed()) {
                throw new BankingException(TransactionErrorCode.ACCOUNT_NOT_CREDITABLE);
            }
            BigDecimal amount = validAmount(request.amount(), account.currency());
            UUID cashBranch = cashBranch(request.branchId(), account);
            ProductTerms terms = productService.terms(account.productVersionId());
            Charge charge = charge(terms, ChargeEvent.CASH_DEPOSIT, amount);
            requireBelowMaximum(account, terms, amount.subtract(charge.amount()));

            String narration = narration(request.narration(), "Cash deposit");
            List<PostingLine> lines = new ArrayList<>();
            lines.add(PostingLine.toSystemAccount(SystemAccount.CASH_AT_BRANCH, cashBranch, account.currency(),
                    EntryDirection.DEBIT, amount, narration));
            lines.add(PostingLine.toLedgerAccount(account.ledgerAccountId(), EntryDirection.CREDIT, amount,
                    narration));
            addCharge(lines, account, terms, charge);
            return post(new Movement(TransactionType.CASH_DEPOSIT, null, account, cashBranch, amount, charge.amount(),
                    account.currency(), narration, request.externalReference(), idempotencyKey), lines);
        });
    }

    @Transactional
    public Result<PostedTransactionResponse> withdraw(String idempotencyKey, CashWithdrawalRequest request) {
        return idempotent(TransactionType.CASH_WITHDRAWAL, idempotencyKey, request, () -> {
            PostingAccount account = accountService.lockForPosting(List.of(request.accountId()))
                    .get(request.accountId());
            if (!account.debitAllowed()) {
                throw new BankingException(TransactionErrorCode.ACCOUNT_NOT_DEBITABLE);
            }
            BigDecimal amount = validAmount(request.amount(), account.currency());
            UUID cashBranch = cashBranch(request.branchId(), account);
            ProductTerms terms = productService.terms(account.productVersionId());
            Charge charge = charge(terms, ChargeEvent.CASH_WITHDRAWAL, amount);
            requireWithinLimits(account, terms, amount);
            requireMinimumBalanceKept(account, terms, amount.add(charge.amount()));

            String narration = narration(request.narration(), "Cash withdrawal");
            List<PostingLine> lines = new ArrayList<>();
            lines.add(PostingLine.toLedgerAccount(account.ledgerAccountId(), EntryDirection.DEBIT, amount,
                    narration));
            lines.add(PostingLine.toSystemAccount(SystemAccount.CASH_AT_BRANCH, cashBranch, account.currency(),
                    EntryDirection.CREDIT, amount, narration));
            addCharge(lines, account, terms, charge);
            return post(new Movement(TransactionType.CASH_WITHDRAWAL, account, null, cashBranch, amount,
                    charge.amount(), account.currency(), narration, request.externalReference(), idempotencyKey),
                    lines);
        });
    }

    @Transactional
    public Result<PostedTransactionResponse> transfer(String idempotencyKey, TransferRequest request) {
        return idempotent(TransactionType.TRANSFER, idempotencyKey, request, () -> {
            if (request.fromAccountId().equals(request.toAccountId())) {
                throw new BankingException(TransactionErrorCode.SAME_ACCOUNT_TRANSFER);
            }
            Map<UUID, PostingAccount> locked = accountService.lockForPosting(
                    List.of(request.fromAccountId(), request.toAccountId()));
            PostingAccount from = locked.get(request.fromAccountId());
            PostingAccount to = locked.get(request.toAccountId());
            if (!from.debitAllowed()) {
                throw new BankingException(TransactionErrorCode.ACCOUNT_NOT_DEBITABLE);
            }
            if (!to.creditAllowed()) {
                throw new BankingException(TransactionErrorCode.ACCOUNT_NOT_CREDITABLE);
            }
            if (!from.currency().equals(to.currency())) {
                throw new BankingException(TransactionErrorCode.CURRENCY_MISMATCH);
            }
            BigDecimal amount = validAmount(request.amount(), from.currency());
            ProductTerms fromTerms = productService.terms(from.productVersionId());
            Charge charge = charge(fromTerms, ChargeEvent.TRANSFER_OUT, amount);
            requireWithinLimits(from, fromTerms, amount);
            requireMinimumBalanceKept(from, fromTerms, amount.add(charge.amount()));
            requireBelowMaximum(to, productService.terms(to.productVersionId()), amount);

            String narration = narration(request.narration(), "Transfer");
            List<PostingLine> lines = new ArrayList<>();
            lines.add(PostingLine.toLedgerAccount(from.ledgerAccountId(), EntryDirection.DEBIT, amount,
                    lineNarration(narration + " to " + to.accountNumber())));
            lines.add(PostingLine.toLedgerAccount(to.ledgerAccountId(), EntryDirection.CREDIT, amount,
                    lineNarration(narration + " from " + from.accountNumber())));
            addCharge(lines, from, fromTerms, charge);
            return post(new Movement(TransactionType.TRANSFER, from, to, from.branchId(), amount, charge.amount(),
                    from.currency(), narration, request.externalReference(), idempotencyKey), lines);
        });
    }

    // ---------------------------------------------------------------------------------------------------------

    /**
     * What is being moved; {@code debit} and {@code credit} are the customer accounts on each side (null for the
     * cash side).
     */
    private record Movement(TransactionType type, PostingAccount debit, PostingAccount credit, UUID branchId,
                            BigDecimal amount, BigDecimal fee, String currency, String narration,
                            String externalReference, String idempotencyKey) {
    }

    private record Charge(BigDecimal amount, String name) {

        static Charge none() {
            return new Charge(BigDecimal.ZERO, null);
        }
    }

    private Result<PostedTransactionResponse> idempotent(TransactionType type, String key, Object request,
                                                         Supplier<PostedTransactionResponse> action) {
        IdempotencyService.requireValidKey(key);
        AuthenticatedActor actor = CurrentActor.require();
        String scope = actor.type() + ":" + (actor.id() == null ? "SYSTEM" : actor.id()) + ":" + type;
        return idempotency.execute(new IdempotencyService.Request(scope, key, idempotency.hash(request), RESOURCE),
                PostedTransactionResponse.class, response -> response.transaction().id(), action);
    }

    private PostedTransactionResponse post(Movement movement, List<PostingLine> lines) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID transactionId = UuidV7.next();
        LocalDate businessDate = businessDates.today();
        String reference = References.next("TXN", businessDate);
        PostedJournal journal = postingEngine.post(new PostingRequest(JournalSource.TRANSACTION, reference,
                transactionId, movement.branchId(), null, movement.narration(), null, lines));
        UUID actorId = CurrentActor.currentActorId().orElse(null);
        FinancialTransaction transaction = transactions.saveAndFlush(new FinancialTransaction(transactionId,
                tenantId, reference, movement.type(), TransactionChannel.BRANCH, movement.currency(),
                movement.amount(), movement.fee(), idOf(movement.debit()), idOf(movement.credit()),
                movement.branchId(), journal.id(), journal.businessDate(), journal.businessDate(),
                movement.narration(), blankToNull(movement.externalReference()), movement.idempotencyKey(), actorId,
                clock.instant()));

        List<BalanceAfter> balances = new ArrayList<>();
        Map<UUID, String> numbers = new LinkedHashMap<>();
        for (PostingAccount account : new PostingAccount[]{movement.debit(), movement.credit()}) {
            if (account == null) {
                continue;
            }
            BalanceSnapshot after = journal.balanceOf(account.ledgerAccountId());
            accountService.recordPosting(account.id(), after.ledgerBalance());
            balances.add(new BalanceAfter(account.id(), account.accountNumber(), after.ledgerBalance(),
                    after.availableBalance()));
            numbers.put(account.id(), account.accountNumber());
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("transactionType", movement.type());
        details.put("amount", currencies.present(movement.amount(), movement.currency()));
        details.put("feeAmount", currencies.present(movement.fee(), movement.currency()));
        details.put("currency", movement.currency());
        details.put("debitAccountId", idOf(movement.debit()));
        details.put("creditAccountId", idOf(movement.credit()));
        details.put("journalEntryId", journal.id());
        auditService.record(AuditEvent.builder("TRANSACTION_POSTED", RESOURCE)
                .resourceId(transactionId)
                .resourceReference(reference)
                .branchId(movement.branchId())
                .after(details)
                .build());
        Map<String, Object> event = new LinkedHashMap<>(details);
        event.put("reference", reference);
        event.put("businessDate", journal.businessDate().toString());
        outbox.publish("FINANCIAL_TRANSACTION", transactionId, "TRANSACTION_POSTED", event);
        return new PostedTransactionResponse(mapper.toResponse(transaction, numbers), List.copyOf(balances));
    }

    private BigDecimal validAmount(BigDecimal amount, String currency) {
        currencies.requireValidAmount(amount, currency);
        return amount;
    }

    /**
     * The branch whose cash moves: in the caller's scope and active.
     */
    private UUID cashBranch(UUID requested, PostingAccount account) {
        UUID branchId = requested != null ? requested : account.branchId();
        if (!CurrentActor.require().branchScope().permits(branchId)) {
            throw new ResourceNotFoundException("Branch");
        }
        if (!branchService.getForInternalUse(branchId).isActive()) {
            throw new BankingException(TransactionErrorCode.CASH_BRANCH_NOT_AVAILABLE);
        }
        return branchId;
    }

    private Charge charge(ProductTerms terms, ChargeEvent event, BigDecimal amount) {
        Optional<ChargeTerms> configured = terms.chargeFor(event.name());
        if (configured.isEmpty()) {
            return Charge.none();
        }
        int minorUnits = currencies.require(terms.currency()).minorUnits();
        return new Charge(ChargeCalculator.calculate(configured.get(), amount, minorUnits), configured.get().name());
    }

    /**
     * The charge is debited from the account and credited to the product's fee income GL in the account's branch.
     */
    private static void addCharge(List<PostingLine> lines, PostingAccount account, ProductTerms terms, Charge charge) {
        if (charge.amount().signum() == 0) {
            return;
        }
        lines.add(PostingLine.toLedgerAccount(account.ledgerAccountId(), EntryDirection.DEBIT, charge.amount(),
                charge.name()));
        lines.add(PostingLine.toGl(terms.feeIncomeGlId(), account.branchId(), account.currency(),
                EntryDirection.CREDIT, charge.amount(), charge.name()));
    }

    private void requireWithinLimits(PostingAccount account, ProductTerms terms, BigDecimal amount) {
        if (terms.maxWithdrawalAmount() != null && amount.compareTo(terms.maxWithdrawalAmount()) > 0) {
            throw new BankingException(TransactionErrorCode.WITHDRAWAL_LIMIT_EXCEEDED);
        }
        if (terms.dailyWithdrawalLimit() != null) {
            BigDecimal alreadyToday = transactions.sumDebits(TenantContext.requireTenantId(), account.id(),
                    businessDates.today(), TransactionStatus.POSTED, OUTGOING);
            if (alreadyToday.add(amount).compareTo(terms.dailyWithdrawalLimit()) > 0) {
                throw new BankingException(TransactionErrorCode.DAILY_LIMIT_EXCEEDED);
            }
        }
    }

    /**
     * The product's minimum operating balance must stay in the account. Funds (holds, overdraft) are checked by the
     * posting engine and the database.
     */
    private void requireMinimumBalanceKept(PostingAccount account, ProductTerms terms, BigDecimal totalDebit) {
        if (terms.minOperatingBalance().signum() == 0) {
            return;
        }
        BigDecimal balance = ledgerAccounts.balance(account.ledgerAccountId()).ledgerBalance();
        if (balance.subtract(totalDebit).compareTo(terms.minOperatingBalance()) < 0) {
            throw new BankingException(TransactionErrorCode.MINIMUM_BALANCE_REQUIRED);
        }
    }

    private void requireBelowMaximum(PostingAccount account, ProductTerms terms, BigDecimal netCredit) {
        if (terms.maxBalance() == null) {
            return;
        }
        BigDecimal balance = ledgerAccounts.balance(account.ledgerAccountId()).ledgerBalance();
        if (balance.add(netCredit).compareTo(terms.maxBalance()) > 0) {
            throw new BankingException(TransactionErrorCode.MAXIMUM_BALANCE_EXCEEDED);
        }
    }

    private static UUID idOf(PostingAccount account) {
        return account == null ? null : account.id();
    }

    private static String narration(String requested, String fallback) {
        return requested == null || requested.isBlank() ? fallback : requested.trim();
    }

    /**
     * Ledger lines keep at most 200 characters of narration.
     */
    private static String lineNarration(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
