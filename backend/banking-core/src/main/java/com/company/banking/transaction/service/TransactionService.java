package com.company.banking.transaction.service;

import com.company.banking.account.dto.PostingAccount;
import com.company.banking.account.service.AccountService;
import com.company.banking.approval.dto.ApprovalResponse;
import com.company.banking.approval.dto.ApprovalSubmission;
import com.company.banking.approval.model.ApprovalType;
import com.company.banking.approval.service.ApprovalHandler.ApprovedAction;
import com.company.banking.approval.service.ApprovalService;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.References;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.idempotency.IdempotencyService;
import com.company.banking.common.idempotency.IdempotencyService.Result;
import com.company.banking.common.outbox.OutboxService;
import com.company.banking.common.security.ActorType;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.BalanceSnapshot;
import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.PostingRequest;
import com.company.banking.ledger.dto.ReversalRequest;
import com.company.banking.ledger.exception.LedgerErrorCode;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.JournalSource;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.ledger.service.PostingEngine;
import com.company.banking.product.dto.ChargeTerms;
import com.company.banking.product.dto.ProductTerms;
import com.company.banking.product.model.ChargeEvent;
import com.company.banking.product.service.ChargeCalculator;
import com.company.banking.product.service.ProductService;
import com.company.banking.teller.dto.CashDrawerRef;
import com.company.banking.teller.exception.TellerErrorCode;
import com.company.banking.teller.service.TellerSessionService;
import com.company.banking.transaction.dto.CashDepositRequest;
import com.company.banking.transaction.dto.CashWithdrawalRequest;
import com.company.banking.transaction.dto.CustomerTransferCommand;
import com.company.banking.transaction.dto.FieldCollectionCommand;
import com.company.banking.transaction.dto.LoanMovementCommand;
import com.company.banking.transaction.dto.MovementResponse;
import com.company.banking.transaction.dto.MovementResponse.BalanceAfter;
import com.company.banking.transaction.dto.ReverseTransactionRequest;
import com.company.banking.transaction.dto.TransferRequest;
import com.company.banking.transaction.entity.FinancialTransaction;
import com.company.banking.transaction.exception.TransactionErrorCode;
import com.company.banking.transaction.model.TransactionChannel;
import com.company.banking.transaction.model.TransactionStatus;
import com.company.banking.transaction.model.TransactionType;
import com.company.banking.transaction.repository.FinancialTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cash deposits, cash withdrawals and transfers between accounts (CMS-initiated; until teller drawers arrive in
 * Phase 3, cash posts against the branch cash GL), and their reversal.
 *
 * <p>Every movement runs in one database transaction: the idempotency key is claimed, the accounts are locked, the
 * product rules and charges are applied, the posting engine writes a balanced journal and the transaction record,
 * audit entry and outbox event are written with it. Amounts are exactly what the caller asked for; charges are
 * computed here from the product terms, never taken from the client.
 *
 * <p>Maker-checker: a withdrawal or transfer at or above the institution's threshold is not posted but submitted for
 * approval; when a checker approves, it is validated again against the state at that moment and posted with the
 * maker as initiator and the checker as approver. Reversals always need approval.
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
    private final TellerSessionService tellerSessions;
    private final BusinessDateService businessDates;
    private final IdempotencyService idempotency;
    private final ApprovalService approvalService;
    private final OutboxService outbox;
    private final TransactionMapper mapper;
    private final AuditService auditService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * What an approved withdrawal or transfer runs with: the original request, the maker's drawer (the cash is paid
     * from it) and the maker's idempotency key (kept on the transaction for tracing).
     */
    public record PendingWithdrawal(CashWithdrawalRequest request, UUID drawerId, String idempotencyKey) {
    }

    public record PendingTransfer(TransferRequest request, String idempotencyKey) {
    }

    public record PendingReversal(UUID transactionId, String reason) {
    }

    @Transactional
    public Result<MovementResponse> deposit(String idempotencyKey, CashDepositRequest request) {
        return idempotent(TransactionType.CASH_DEPOSIT, idempotencyKey, request, () -> {
            PostingAccount account = accountService.lockForPosting(List.of(request.accountId()))
                    .get(request.accountId());
            if (!account.creditAllowed()) {
                throw new BankingException(TransactionErrorCode.ACCOUNT_NOT_CREDITABLE);
            }
            BigDecimal amount = validAmount(request.amount(), account.currency());
            CashDrawerRef drawer = tellerSessions.drawerForCash(CurrentActor.require().id());
            requireDrawerCurrency(drawer, account);
            ProductTerms terms = productService.terms(account.productVersionId());
            Charge charge = charge(terms, ChargeEvent.CASH_DEPOSIT, amount);
            requireBelowMaximum(account, terms, amount.subtract(charge.amount()));

            String narration = narration(request.narration(), "Cash deposit");
            List<PostingLine> lines = new ArrayList<>();
            lines.add(PostingLine.toLedgerAccount(drawer.ledgerAccountId(), EntryDirection.DEBIT, amount, narration));
            lines.add(PostingLine.toLedgerAccount(account.ledgerAccountId(), EntryDirection.CREDIT, amount,
                    narration));
            addCharge(lines, account, terms, charge);
            return post(new Movement(TransactionType.CASH_DEPOSIT, null, account, drawer.branchId(), drawer.drawerId(),
                    amount, charge.amount(), account.currency(), narration, request.externalReference(),
                    idempotencyKey, TransactionChannel.BRANCH), lines, null);
        });
    }

    /**
     * Posts cash a field officer took for a customer: Dr the officer's cash with collectors, Cr the account. The
     * account must accept money and stay within its product's maximum balance; no per-movement charge applies (susu
     * products charge a cycle commission instead). Runs inside the field operations module's transaction, whose
     * collection record (unique client reference) makes it happen once, so there is no request key here.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovementResponse postFieldCollection(FieldCollectionCommand command) {
        PostingAccount account = accountService.lockForPosting(List.of(command.accountId())).get(command.accountId());
        if (!account.creditAllowed()) {
            throw new BankingException(TransactionErrorCode.ACCOUNT_NOT_CREDITABLE);
        }
        if (!account.currency().equals(command.currency())) {
            throw new BankingException(TransactionErrorCode.CURRENCY_MISMATCH);
        }
        BigDecimal amount = validAmount(command.amount(), account.currency());
        requireBelowMaximum(account, productService.terms(account.productVersionId()), amount);
        String narration = narration(command.narration(), "Field collection");
        List<PostingLine> lines = List.of(
                PostingLine.toLedgerAccount(command.collectorLedgerAccountId(), EntryDirection.DEBIT, amount,
                        narration),
                PostingLine.toLedgerAccount(account.ledgerAccountId(), EntryDirection.CREDIT, amount, narration));
        return post(new Movement(TransactionType.FIELD_COLLECTION, null, account, command.branchId(), null, amount,
                BigDecimal.ZERO, account.currency(), narration, command.externalReference(), null,
                TransactionChannel.FIELD), lines, null);
    }

    /**
     * Posts a loan disbursement or repayment. The loan module builds the loan's side of the journal; the customer's
     * side is added here with the same checks as any movement: a disbursement credits the borrower's account (which
     * must accept money, within its maximum balance) and takes the processing fee from it; a repayment debits the
     * borrower's account (which must pay out and keep its minimum balance) or takes cash into the caller's open till.
     * Runs inside the loan module's transaction, which handles idempotency.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovementResponse postLoanMovement(LoanMovementCommand command) {
        BigDecimal amount = validAmount(command.amount(), command.currency());
        String narration = narration(command.narration(),
                command.type() == TransactionType.LOAN_DISBURSEMENT ? "Loan disbursement" : "Loan repayment");
        List<PostingLine> lines = new ArrayList<>(command.loanLines());
        if (command.type() == TransactionType.LOAN_DISBURSEMENT) {
            PostingAccount account = accountService.lockForPosting(List.of(command.accountId()))
                    .get(command.accountId());
            requireCustomerSide(account, command.currency(), account.creditAllowed(),
                    TransactionErrorCode.ACCOUNT_NOT_CREDITABLE);
            BigDecimal fee = command.fee() == null ? BigDecimal.ZERO : command.fee();
            requireBelowMaximum(account, productService.terms(account.productVersionId()), amount.subtract(fee));
            lines.add(PostingLine.toLedgerAccount(account.ledgerAccountId(), EntryDirection.CREDIT, amount, narration));
            if (fee.signum() > 0) {
                lines.add(PostingLine.toLedgerAccount(account.ledgerAccountId(), EntryDirection.DEBIT, fee,
                        "Loan processing fee"));
                lines.add(PostingLine.toGl(command.feeGlId(), account.branchId(), command.currency(),
                        EntryDirection.CREDIT, fee, "Loan processing fee"));
            }
            return post(new Movement(TransactionType.LOAN_DISBURSEMENT, null, account, command.branchId(), null,
                    amount, fee, command.currency(), narration, command.externalReference(), command.idempotencyKey(),
                    channelOfActor()), lines, null);
        }
        if (command.type() != TransactionType.LOAN_REPAYMENT) {
            throw new IllegalArgumentException("Not a loan movement: " + command.type());
        }
        if (command.accountId() == null) {
            CashDrawerRef drawer = tellerSessions.drawerForCash(CurrentActor.require().id());
            if (!drawer.currency().equals(command.currency())) {
                throw new BankingException(TransactionErrorCode.CURRENCY_MISMATCH);
            }
            lines.add(PostingLine.toLedgerAccount(drawer.ledgerAccountId(), EntryDirection.DEBIT, amount, narration));
            return post(new Movement(TransactionType.LOAN_REPAYMENT, null, null, drawer.branchId(), drawer.drawerId(),
                    amount, BigDecimal.ZERO, command.currency(), narration, command.externalReference(),
                    command.idempotencyKey(), TransactionChannel.BRANCH), lines, null);
        }
        PostingAccount account = accountService.lockForPosting(List.of(command.accountId())).get(command.accountId());
        requireCustomerSide(account, command.currency(), account.debitAllowed(),
                TransactionErrorCode.ACCOUNT_NOT_DEBITABLE);
        requireMinimumBalanceKept(account, productService.terms(account.productVersionId()), amount);
        requireFunds(account, amount);
        lines.add(PostingLine.toLedgerAccount(account.ledgerAccountId(), EntryDirection.DEBIT, amount, narration));
        return post(new Movement(TransactionType.LOAN_REPAYMENT, account, null, command.branchId(), null, amount,
                BigDecimal.ZERO, command.currency(), narration, command.externalReference(), command.idempotencyKey(),
                channelOfActor()), lines, null);
    }

    /**
     * A movement a customer makes in the app is a mobile one; staff work at a branch.
     */
    private static TransactionChannel channelOfActor() {
        return CurrentActor.current().map(actor -> actor.type() == ActorType.CUSTOMER).orElse(false)
                ? TransactionChannel.MOBILE : TransactionChannel.BRANCH;
    }

    private static void requireCustomerSide(PostingAccount account, String currency, boolean allowed,
                                            TransactionErrorCode refusal) {
        if (!allowed) {
            throw new BankingException(refusal);
        }
        if (!account.currency().equals(currency)) {
            throw new BankingException(TransactionErrorCode.CURRENCY_MISMATCH);
        }
    }

    @Transactional
    public Result<MovementResponse> withdraw(String idempotencyKey, CashWithdrawalRequest request) {
        return idempotent(TransactionType.CASH_WITHDRAWAL, idempotencyKey, request,
                () -> withdrawal(request, idempotencyKey, null, null));
    }

    @Transactional
    public Result<MovementResponse> transfer(String idempotencyKey, TransferRequest request) {
        return idempotent(TransactionType.TRANSFER, idempotencyKey, request,
                () -> transferBetween(request, idempotencyKey, null));
    }

    /**
     * A transfer by the signed-in customer from the app (channel {@code MOBILE}): the same product rules, charges
     * and checks as at a branch, but never a maker-checker request, since the channel's own limits apply instead.
     * {@code preconditions} runs once, on the first attempt only (a retry with the same key replays the result), inside
     * the transaction and after the accounts are locked, with the transfer's currency: the PIN and channel limits.
     */
    @Transactional
    public Result<MovementResponse> customerTransfer(String idempotencyKey, CustomerTransferCommand command,
                                                     Consumer<String> preconditions) {
        return idempotent(TransactionType.TRANSFER, idempotencyKey, command, () -> {
            if (command.fromAccountId().equals(command.toAccountId())) {
                throw new BankingException(TransactionErrorCode.SAME_ACCOUNT_TRANSFER);
            }
            Map<UUID, PostingAccount> locked = accountService.lockForCustomerTransfer(command.fromAccountId(),
                    command.toAccountId());
            PostingAccount from = locked.get(command.fromAccountId());
            PostingAccount to = locked.get(command.toAccountId());
            if (!from.debitAllowed()) {
                throw new BankingException(TransactionErrorCode.ACCOUNT_NOT_DEBITABLE);
            }
            if (!to.creditAllowed()) {
                throw new BankingException(TransactionErrorCode.ACCOUNT_NOT_CREDITABLE);
            }
            if (!from.currency().equals(to.currency())) {
                throw new BankingException(TransactionErrorCode.CURRENCY_MISMATCH);
            }
            BigDecimal amount = validAmount(command.amount(), from.currency());
            preconditions.accept(from.currency());
            ProductTerms fromTerms = productService.terms(from.productVersionId());
            Charge charge = charge(fromTerms, ChargeEvent.TRANSFER_OUT, amount);
            requireWithinLimits(from, fromTerms, amount);
            requireMinimumBalanceKept(from, fromTerms, amount.add(charge.amount()));
            requireBelowMaximum(to, productService.terms(to.productVersionId()), amount);
            requireFunds(from, amount.add(charge.amount()));

            String narration = narration(command.narration(), "Transfer");
            List<PostingLine> lines = new ArrayList<>();
            lines.add(PostingLine.toLedgerAccount(from.ledgerAccountId(), EntryDirection.DEBIT, amount,
                    lineNarration(narration + " to " + to.accountNumber())));
            lines.add(PostingLine.toLedgerAccount(to.ledgerAccountId(), EntryDirection.CREDIT, amount,
                    lineNarration(narration + " from " + from.accountNumber())));
            addCharge(lines, from, fromTerms, charge);
            return post(new Movement(TransactionType.TRANSFER, from, to, from.branchId(), null, amount,
                    charge.amount(), from.currency(), narration, null, idempotencyKey, TransactionChannel.MOBILE),
                    lines, null);
        });
    }

    /**
     * What a customer has sent to others from the app today, in a currency (moves between their own accounts, given
     * as {@code ownAccounts}, are left out).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal mobileTransfersToday(UUID customerId, String currency, Collection<UUID> ownAccounts) {
        return transactions.sumMobileTransfers(TenantContext.requireTenantId(), customerId, businessDates.today(),
                currency, ownAccounts.isEmpty() ? List.of(new UUID(0, 0)) : ownAccounts);
    }

    /**
     * Asks for a reversal; it runs when a checker approves it.
     */
    @Transactional
    public ApprovalResponse requestReversal(UUID transactionId, ReverseTransactionRequest request) {
        FinancialTransaction transaction = loadVisible(transactionId);
        if (transaction.getStatus() != TransactionStatus.POSTED) {
            throw new BankingException(TransactionErrorCode.TRANSACTION_ALREADY_REVERSED);
        }
        requireReversible(transaction);
        return approvalService.submit(new ApprovalSubmission(ApprovalType.TRANSACTION_REVERSAL,
                transaction.getBranchId(), transaction.getAmount(), transaction.getCurrency(), RESOURCE,
                transaction.getId(), "Reverse " + transaction.getTransactionType() + " " + transaction.getReference()
                + ": " + request.reason().trim(), new PendingReversal(transaction.getId(), request.reason().trim())));
    }

    // ------------------------------------------------------------------------------------- approved execution

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID executeApprovedWithdrawal(ApprovedAction action) {
        PendingWithdrawal pending = jsonMapper.readValue(action.payloadJson(), PendingWithdrawal.class);
        return withdrawal(pending.request(), pending.idempotencyKey(), action, pending.drawerId()).transaction().id();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID executeApprovedTransfer(ApprovedAction action) {
        PendingTransfer pending = jsonMapper.readValue(action.payloadJson(), PendingTransfer.class);
        return transferBetween(pending.request(), pending.idempotencyKey(), action).transaction().id();
    }

    /**
     * Posts the mirror of the transaction's journal (charges included), attributed to the maker and approved by the
     * checker. Fails, leaving the request pending, if the money is no longer there (e.g. a reversed deposit was
     * already spent).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID executeApprovedReversal(ApprovedAction action) {
        PendingReversal pending = jsonMapper.readValue(action.payloadJson(), PendingReversal.class);
        FinancialTransaction transaction = transactions.findByTenantIdAndId(TenantContext.requireTenantId(),
                        pending.transactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction"));
        Map<UUID, PostingAccount> accounts = accountService.lockForPosting(Stream.of(
                        transaction.getDebitAccountId(), transaction.getCreditAccountId())
                .filter(Objects::nonNull)
                .toList());
        if (transaction.getStatus() != TransactionStatus.POSTED) {
            throw new BankingException(TransactionErrorCode.TRANSACTION_ALREADY_REVERSED);
        }
        requireReversible(transaction);
        if (transaction.getCashDrawerId() != null) {
            // Reversing cash changes what the drawer should hold, so someone must be working it to hand cash over.
            tellerSessions.requireOpenDrawer(transaction.getCashDrawerId());
        }
        PostedJournal reversal = postingEngine.reverseApproved(new ReversalRequest(transaction.getJournalEntryId(),
                pending.reason(), transaction.getId(), null), action.requestedBy());
        transaction.markReversed(reversal.id(), clock.instant(), action.approvedBy(), pending.reason());
        transactions.saveAndFlush(transaction);
        for (PostingAccount account : accounts.values()) {
            accountService.recordPosting(account.id(), reversal.balanceOf(account.ledgerAccountId()).ledgerBalance());
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("transactionType", transaction.getTransactionType());
        details.put("reversalJournalEntryId", reversal.id());
        details.put("requestedBy", action.requestedBy());
        details.put("approvalRequestId", action.requestId());
        auditService.record(AuditEvent.builder("TRANSACTION_REVERSED", RESOURCE)
                .resourceId(transaction.getId())
                .resourceReference(transaction.getReference())
                .branchId(transaction.getBranchId())
                .before(Map.of("status", TransactionStatus.POSTED))
                .after(Map.of("status", TransactionStatus.REVERSED))
                .metadata("details", details)
                .metadata("reason", pending.reason())
                .build());
        outbox.publish("FINANCIAL_TRANSACTION", transaction.getId(), "TRANSACTION_REVERSED", Map.of(
                "reference", transaction.getReference(), "transactionType", transaction.getTransactionType().name(),
                "reversalJournalEntryId", reversal.id().toString()));
        return transaction.getId();
    }

    // ---------------------------------------------------------------------------------------------------------

    /**
     * What is being moved; {@code debit} and {@code credit} are the customer accounts on each side (null for the
     * cash side).
     */
    private record Movement(TransactionType type, PostingAccount debit, PostingAccount credit, UUID branchId,
                            UUID drawerId,
                            BigDecimal amount, BigDecimal fee, String currency, String narration,
                            String externalReference, String idempotencyKey, TransactionChannel channel) {
    }

    private record Charge(BigDecimal amount, String name) {

        static Charge none() {
            return new Charge(BigDecimal.ZERO, null);
        }
    }

    /**
     * @param approval the approved request when a checker is executing it; null for the maker's own request
     */
    /**
     * @param approval      the approved request when a checker is executing it; null for the teller's own request
     * @param approvedDrawer the maker's drawer recorded with the request (approved execution only)
     */
    private MovementResponse withdrawal(CashWithdrawalRequest request, String idempotencyKey,
                                        ApprovedAction approval, UUID approvedDrawer) {
        PostingAccount account = accountService.lockForPosting(List.of(request.accountId())).get(request.accountId());
        if (!account.debitAllowed()) {
            throw new BankingException(TransactionErrorCode.ACCOUNT_NOT_DEBITABLE);
        }
        BigDecimal amount = validAmount(request.amount(), account.currency());
        CashDrawerRef drawer = approval == null ? tellerSessions.drawerForCash(CurrentActor.require().id())
                : tellerSessions.drawerForApprovedCash(approvedDrawer, approval.requestedBy());
        requireDrawerCurrency(drawer, account);
        ProductTerms terms = productService.terms(account.productVersionId());
        Charge charge = charge(terms, ChargeEvent.CASH_WITHDRAWAL, amount);
        requireWithinLimits(account, terms, amount);
        requireMinimumBalanceKept(account, terms, amount.add(charge.amount()));
        requireFunds(account, amount.add(charge.amount()));
        requireDrawerCash(drawer, amount);
        if (approval == null
                && approvalService.requiresApproval(ApprovalType.CASH_WITHDRAWAL, account.currency(), amount)) {
            return MovementResponse.pending(approvalService.submit(new ApprovalSubmission(
                    ApprovalType.CASH_WITHDRAWAL, drawer.branchId(), amount, account.currency(), "ACCOUNT", null,
                    "Cash withdrawal of " + account.currency() + " " + currencies.present(amount, account.currency())
                            .toPlainString() + " from " + account.accountNumber() + " at drawer " + drawer.code(),
                    new PendingWithdrawal(request, drawer.drawerId(), idempotencyKey))));
        }

        String narration = narration(request.narration(), "Cash withdrawal");
        List<PostingLine> lines = new ArrayList<>();
        lines.add(PostingLine.toLedgerAccount(account.ledgerAccountId(), EntryDirection.DEBIT, amount, narration));
        lines.add(PostingLine.toLedgerAccount(drawer.ledgerAccountId(), EntryDirection.CREDIT, amount, narration));
        addCharge(lines, account, terms, charge);
        return post(new Movement(TransactionType.CASH_WITHDRAWAL, account, null, drawer.branchId(), drawer.drawerId(),
                amount, charge.amount(), account.currency(), narration, request.externalReference(), idempotencyKey,
                TransactionChannel.BRANCH), lines, approval);
    }

    private MovementResponse transferBetween(TransferRequest request, String idempotencyKey,
                                             ApprovedAction approval) {
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
        requireFunds(from, amount.add(charge.amount()));
        if (approval == null && approvalService.requiresApproval(ApprovalType.TRANSFER, from.currency(), amount)) {
            return MovementResponse.pending(approvalService.submit(new ApprovalSubmission(ApprovalType.TRANSFER,
                    from.branchId(), amount, from.currency(), "ACCOUNT", null,
                    "Transfer of " + from.currency() + " " + currencies.present(amount, from.currency())
                            .toPlainString() + " from " + from.accountNumber() + " to " + to.accountNumber(),
                    new PendingTransfer(request, idempotencyKey))));
        }

        String narration = narration(request.narration(), "Transfer");
        List<PostingLine> lines = new ArrayList<>();
        lines.add(PostingLine.toLedgerAccount(from.ledgerAccountId(), EntryDirection.DEBIT, amount,
                lineNarration(narration + " to " + to.accountNumber())));
        lines.add(PostingLine.toLedgerAccount(to.ledgerAccountId(), EntryDirection.CREDIT, amount,
                lineNarration(narration + " from " + from.accountNumber())));
        addCharge(lines, from, fromTerms, charge);
        return post(new Movement(TransactionType.TRANSFER, from, to, from.branchId(), null, amount, charge.amount(),
                from.currency(), narration, request.externalReference(), idempotencyKey, TransactionChannel.BRANCH),
                lines, approval);
    }

    private Result<MovementResponse> idempotent(TransactionType type, String key, Object request,
                                                Supplier<MovementResponse> action) {
        IdempotencyService.requireValidKey(key);
        AuthenticatedActor actor = CurrentActor.require();
        String scope = actor.type() + ":" + (actor.id() == null ? "SYSTEM" : actor.id()) + ":" + type;
        return idempotency.execute(new IdempotencyService.Request(scope, key, idempotency.hash(request), RESOURCE),
                MovementResponse.class,
                response -> response.isPosted() ? response.transaction().id() : response.approval().id(), action);
    }

    /**
     * @param approval when present, the journal is posted by the maker and approved by the current actor (checker)
     */
    private MovementResponse post(Movement movement, List<PostingLine> lines, ApprovedAction approval) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID transactionId = UuidV7.next();
        LocalDate businessDate = businessDates.today();
        String reference = References.next("TXN", businessDate);
        PostingRequest posting = new PostingRequest(JournalSource.TRANSACTION, reference, transactionId,
                movement.branchId(), null, movement.narration(), null, lines);
        PostedJournal journal = approval == null ? postingEngine.post(posting)
                : postingEngine.postApproved(posting, approval.requestedBy());
        UUID initiatedBy = approval == null ? CurrentActor.currentActorId().orElse(null) : approval.requestedBy();
        FinancialTransaction transaction = transactions.saveAndFlush(new FinancialTransaction(transactionId,
                tenantId, reference, movement.type(), movement.channel(), movement.currency(),
                movement.amount(), movement.fee(), idOf(movement.debit()), idOf(movement.credit()),
                movement.branchId(), movement.drawerId(), journal.id(), journal.businessDate(), journal.businessDate(),
                movement.narration(), blankToNull(movement.externalReference()), movement.idempotencyKey(),
                initiatedBy, approval == null ? null : approval.approvedBy(),
                approval == null ? null : approval.requestId(), clock.instant()));

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
        details.put("approvalRequestId", approval == null ? null : approval.requestId());
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
        return MovementResponse.posted(mapper.toResponse(transaction, numbers), balances);
    }

    /**
     * A loan's schedule records what each of its movements settled, so reversing one here would leave the loan
     * wrong: loan movements are corrected through the loan module.
     */
    private static void requireReversible(FinancialTransaction transaction) {
        if (transaction.getTransactionType() == TransactionType.LOAN_DISBURSEMENT
                || transaction.getTransactionType() == TransactionType.LOAN_REPAYMENT) {
            throw new BankingException(TransactionErrorCode.REVERSAL_NOT_SUPPORTED);
        }
    }

    private FinancialTransaction loadVisible(UUID transactionId) {
        return transactions.findByTenantIdAndId(TenantContext.requireTenantId(), transactionId)
                .filter(transaction -> Stream.of(transaction.getDebitAccountId(), transaction.getCreditAccountId())
                        .filter(Objects::nonNull)
                        .anyMatch(accountService::isReadable))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction"));
    }

    private BigDecimal validAmount(BigDecimal amount, String currency) {
        currencies.requireValidAmount(amount, currency);
        return amount;
    }

    private static void requireDrawerCurrency(CashDrawerRef drawer, PostingAccount account) {
        if (!drawer.currency().equals(account.currency())) {
            throw new BankingException(TellerErrorCode.CURRENCY_MISMATCH,
                    "Drawer " + drawer.code() + " holds " + drawer.currency() + "; the account is in "
                            + account.currency() + ".");
        }
    }

    /**
     * The drawer must hold the cash to pay out (the database would refuse too: drawers never go below zero).
     */
    private void requireDrawerCash(CashDrawerRef drawer, BigDecimal amount) {
        BigDecimal held = ledgerAccounts.balance(drawer.ledgerAccountId()).availableBalance();
        if (held.compareTo(amount) < 0) {
            throw new BankingException(TellerErrorCode.CASH_INSUFFICIENT,
                    "Drawer " + drawer.code() + " holds only " + held.toPlainString() + ". Ask for cash from the vault.");
        }
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
     * The product's minimum operating balance must stay in the account.
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

    /**
     * Refuses up front what the posting engine would refuse anyway, so a withdrawal that cannot be paid is never
     * sent for approval. The engine and the database check again when posting.
     */
    private void requireFunds(PostingAccount account, BigDecimal totalDebit) {
        BalanceSnapshot balance = ledgerAccounts.balance(account.ledgerAccountId());
        if (balance.availableBalance().add(balance.overdraftLimit()).compareTo(totalDebit) < 0) {
            throw new BankingException(LedgerErrorCode.INSUFFICIENT_FUNDS);
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
