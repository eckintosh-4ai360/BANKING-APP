package com.company.banking.teller.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.References;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.security.Permissions;
import com.company.banking.common.tenant.TenantContext;
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
import com.company.banking.teller.dto.CashDrawerRef;
import com.company.banking.teller.dto.CashMovementRequest;
import com.company.banking.teller.dto.CashMovementResponse;
import com.company.banking.teller.dto.SupervisorDecisionRequest;
import com.company.banking.teller.entity.CashDrawer;
import com.company.banking.teller.entity.CashMovement;
import com.company.banking.teller.entity.Vault;
import com.company.banking.teller.exception.TellerErrorCode;
import com.company.banking.teller.repository.CashMovementRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cash between vaults, drawers and the bank. One person asks, another approves (four eyes) and the approval posts
 * the movement; between branches the cash sits in cash in transit until the receiving branch confirms it. Vault and
 * drawer balances can never go below zero (balance-checked ledger accounts).
 */
@Service
@RequiredArgsConstructor
public class CashMovementService {

    private static final String RESOURCE = "CASH_MOVEMENT";

    private final CashMovementRepository movements;
    private final CashPointService cashPoints;
    private final TellerSessionService sessions;
    private final LedgerAccountService ledgerAccounts;
    private final CurrencyService currencies;
    private final PostingEngine postingEngine;
    private final BusinessDateService businessDates;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public CashMovementResponse request(CashMovementRequest request) {
        AuthenticatedActor actor = CurrentActor.require();
        CashMovement.Type type = CashMovement.Type.valueOf(request.movementType());
        String currency = currencies.require(request.currency()).code();
        currencies.requireValidAmount(request.amount(), currency);
        boolean cashManager = actor.hasPermission(Permissions.CASH_MANAGE);
        CashMovement.Ends ends = switch (type) {
            case VAULT_TO_DRAWER, DRAWER_TO_VAULT -> {
                if (request.drawerId() == null) {
                    throw new BankingException(TellerErrorCode.INVALID_MOVEMENT, "Choose the drawer.");
                }
                CashDrawer drawer = cashPoints.drawerInScope(request.drawerId());
                requireCurrency(drawer.getCurrency(), currency);
                if (!cashManager) {
                    // A teller may only ask for cash to or from the drawer they are working.
                    CashDrawerRef own = sessions.drawerForCash(actor.id());
                    if (!own.drawerId().equals(drawer.getId())) {
                        throw new BankingException(CommonErrorCode.ACCESS_DENIED,
                                "Tellers can only move cash for the drawer they are working.");
                    }
                }
                Vault vault = cashPoints.vaultOf(drawer.getBranchId(), currency);
                yield type == CashMovement.Type.VAULT_TO_DRAWER
                        ? new CashMovement.Ends(drawer.getBranchId(), drawer.getBranchId(), vault.getId(), null, null,
                        drawer.getId())
                        : new CashMovement.Ends(drawer.getBranchId(), drawer.getBranchId(), null, drawer.getId(),
                        vault.getId(), null);
            }
            case BANK_TO_VAULT, VAULT_TO_BANK -> {
                requireCashManager(cashManager);
                Vault vault = vaultInScope(request.branchId(), currency);
                yield type == CashMovement.Type.BANK_TO_VAULT
                        ? new CashMovement.Ends(vault.getBranchId(), vault.getBranchId(), null, null, vault.getId(),
                        null)
                        : new CashMovement.Ends(vault.getBranchId(), vault.getBranchId(), vault.getId(), null, null,
                        null);
            }
            case VAULT_TO_VAULT -> {
                requireCashManager(cashManager);
                if (request.toBranchId() == null || request.toBranchId().equals(request.branchId())) {
                    throw new BankingException(TellerErrorCode.INVALID_MOVEMENT,
                            "Choose a different receiving branch.");
                }
                Vault from = vaultInScope(request.branchId(), currency);
                Vault to = cashPoints.vaultOf(request.toBranchId(), currency);
                yield new CashMovement.Ends(from.getBranchId(), to.getBranchId(), from.getId(), null, to.getId(), null);
            }
        };
        Instant now = clock.instant();
        CashMovement movement = movements.saveAndFlush(new CashMovement(UuidV7.next(), TenantContext.requireTenantId(),
                References.next("CM", businessDates.today()), type, currency, request.amount(), ends,
                blankToNull(request.note()), actor.id(), now));
        CashMovementResponse response = toResponse(movement);
        auditService.record(AuditEvent.builder("CASH_MOVEMENT_REQUESTED", RESOURCE)
                .resourceId(movement.getId())
                .resourceReference(movement.getReference())
                .branchId(movement.getFromBranchId())
                .after(response)
                .build());
        return response;
    }

    /**
     * Approves and posts: the cash leaves its source (into cash in transit between branches).
     */
    @Transactional
    public CashMovementResponse approve(UUID movementId, SupervisorDecisionRequest decision) {
        AuthenticatedActor approver = CurrentActor.require();
        CashMovement movement = lockInScope(movementId, approver, true);
        ConcurrentModificationException.assertVersion(decision.version(), movement.getVersion());
        requireStatus(movement, CashMovement.Status.REQUESTED);
        if (approver.isSameUserAs(movement.getRequestedBy())) {
            throw new BankingException(CommonErrorCode.FOUR_EYES_VIOLATION);
        }
        String narration = humanType(movement) + " " + movement.getReference();
        List<PostingLine> lines = switch (movement.getMovementType()) {
            case VAULT_TO_DRAWER -> {
                UUID drawerLedger = sessions.requireOpenDrawer(movement.getToDrawerId()).ledgerAccountId();
                UUID vaultLedger = cashPoints.vault(movement.getFromVaultId()).getLedgerAccountId();
                requireCash(vaultLedger, movement.getAmount(), "The vault");
                yield List.of(line(drawerLedger, EntryDirection.DEBIT, movement, narration),
                        line(vaultLedger, EntryDirection.CREDIT, movement, narration));
            }
            case DRAWER_TO_VAULT -> {
                UUID drawerLedger = sessions.requireOpenDrawer(movement.getFromDrawerId()).ledgerAccountId();
                UUID vaultLedger = cashPoints.vault(movement.getToVaultId()).getLedgerAccountId();
                requireCash(drawerLedger, movement.getAmount(), "The drawer");
                yield List.of(line(vaultLedger, EntryDirection.DEBIT, movement, narration),
                        line(drawerLedger, EntryDirection.CREDIT, movement, narration));
            }
            case BANK_TO_VAULT -> List.of(
                    line(cashPoints.vault(movement.getToVaultId()).getLedgerAccountId(), EntryDirection.DEBIT,
                            movement, narration),
                    PostingLine.toSystemAccount(SystemAccount.BANK_BALANCES, movement.getFromBranchId(),
                            movement.getCurrency(), EntryDirection.CREDIT, movement.getAmount(), narration));
            case VAULT_TO_BANK -> {
                UUID vaultLedger = cashPoints.vault(movement.getFromVaultId()).getLedgerAccountId();
                requireCash(vaultLedger, movement.getAmount(), "The vault");
                yield List.of(PostingLine.toSystemAccount(SystemAccount.BANK_BALANCES, movement.getFromBranchId(),
                                movement.getCurrency(), EntryDirection.DEBIT, movement.getAmount(), narration),
                        line(vaultLedger, EntryDirection.CREDIT, movement, narration));
            }
            case VAULT_TO_VAULT -> {
                UUID vaultLedger = cashPoints.vault(movement.getFromVaultId()).getLedgerAccountId();
                requireCash(vaultLedger, movement.getAmount(), "The vault");
                yield List.of(PostingLine.toSystemAccount(SystemAccount.CASH_IN_TRANSIT, movement.getFromBranchId(),
                                movement.getCurrency(), EntryDirection.DEBIT, movement.getAmount(), narration),
                        line(vaultLedger, EntryDirection.CREDIT, movement, narration));
            }
        };
        PostedJournal journal = post(movement, narration, lines);
        movement.approve(approver.id(), clock.instant(), journal.id());
        movements.saveAndFlush(movement);
        audit("CASH_MOVEMENT_APPROVED", movement, decision.note());
        return toResponse(movement);
    }

    /**
     * The receiving branch confirms cash in transit: it moves into the receiving vault.
     */
    @Transactional
    public CashMovementResponse receive(UUID movementId, SupervisorDecisionRequest decision) {
        AuthenticatedActor receiver = CurrentActor.require();
        CashMovement movement = movements.lockByTenantIdAndId(TenantContext.requireTenantId(), movementId)
                .filter(found -> receiver.branchScope().permits(found.getToBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cash movement"));
        ConcurrentModificationException.assertVersion(decision.version(), movement.getVersion());
        requireStatus(movement, CashMovement.Status.IN_TRANSIT);
        String narration = "Received " + movement.getReference();
        PostedJournal journal = post(movement, narration, List.of(
                line(cashPoints.vault(movement.getToVaultId()).getLedgerAccountId(), EntryDirection.DEBIT, movement,
                        narration),
                PostingLine.toSystemAccount(SystemAccount.CASH_IN_TRANSIT, movement.getFromBranchId(),
                        movement.getCurrency(), EntryDirection.CREDIT, movement.getAmount(), narration)));
        movement.receive(receiver.id(), clock.instant(), journal.id());
        movements.saveAndFlush(movement);
        audit("CASH_MOVEMENT_RECEIVED", movement, decision.note());
        return toResponse(movement);
    }

    @Transactional
    public CashMovementResponse reject(UUID movementId, SupervisorDecisionRequest decision) {
        AuthenticatedActor approver = CurrentActor.require();
        CashMovement movement = lockInScope(movementId, approver, true);
        ConcurrentModificationException.assertVersion(decision.version(), movement.getVersion());
        requireStatus(movement, CashMovement.Status.REQUESTED);
        movement.reject(approver.id(), clock.instant(), decision.note().trim());
        movements.saveAndFlush(movement);
        audit("CASH_MOVEMENT_REJECTED", movement, decision.note());
        return toResponse(movement);
    }

    /**
     * The requester withdraws a request that nobody has acted on.
     */
    @Transactional
    public CashMovementResponse cancel(UUID movementId, SupervisorDecisionRequest decision) {
        AuthenticatedActor actor = CurrentActor.require();
        CashMovement movement = lockInScope(movementId, actor, true);
        if (!actor.isSameUserAs(movement.getRequestedBy())) {
            throw new BankingException(CommonErrorCode.ACCESS_DENIED, "Only the requester can cancel it.");
        }
        ConcurrentModificationException.assertVersion(decision.version(), movement.getVersion());
        requireStatus(movement, CashMovement.Status.REQUESTED);
        movement.cancel(decision.note().trim());
        movements.saveAndFlush(movement);
        audit("CASH_MOVEMENT_CANCELLED", movement, decision.note());
        return toResponse(movement);
    }

    @Transactional(readOnly = true)
    public PageResponse<CashMovementResponse> search(String status, PageRequest page) {
        UUID tenantId = TenantContext.requireTenantId();
        BranchScope scope = CurrentActor.require().branchScope();
        Specification<CashMovement> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (!scope.allBranches()) {
                predicates.add(scope.branchIds().isEmpty() ? cb.disjunction()
                        : cb.or(root.get("fromBranchId").in(scope.branchIds()),
                        root.get("toBranchId").in(scope.branchIds())));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), CashMovement.Status.valueOf(status)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(movements.findAll(specification, PageRequest.of(page.getPageNumber(),
                page.getPageSize(), Sort.by(Sort.Order.desc("requestedAt")))), this::toResponse);
    }

    // ---------------------------------------------------------------------------------------------------------

    private CashMovement lockInScope(UUID movementId, AuthenticatedActor actor, boolean sourceBranch) {
        return movements.lockByTenantIdAndId(TenantContext.requireTenantId(), movementId)
                .filter(found -> actor.branchScope().permits(sourceBranch ? found.getFromBranchId()
                        : found.getToBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cash movement"));
    }

    private Vault vaultInScope(UUID branchId, String currency) {
        if (branchId == null) {
            throw new BankingException(TellerErrorCode.INVALID_MOVEMENT, "Choose the branch.");
        }
        if (!CurrentActor.require().branchScope().permits(branchId)) {
            throw new ResourceNotFoundException("Branch");
        }
        return cashPoints.vaultOf(branchId, currency);
    }

    private void requireCash(UUID ledgerAccountId, BigDecimal amount, String holder) {
        BigDecimal available = ledgerAccounts.balance(ledgerAccountId).availableBalance();
        if (available.compareTo(amount) < 0) {
            throw new BankingException(TellerErrorCode.CASH_INSUFFICIENT, holder + " holds only " + available + ".");
        }
    }

    private PostedJournal post(CashMovement movement, String narration, List<PostingLine> lines) {
        return postingEngine.post(new PostingRequest(JournalSource.TRANSACTION, movement.getReference(), null,
                movement.getFromBranchId(), null, narration, null, lines));
    }

    private static PostingLine line(UUID ledgerAccountId, EntryDirection direction, CashMovement movement,
                                    String narration) {
        return PostingLine.toLedgerAccount(ledgerAccountId, direction, movement.getAmount(), narration);
    }

    private static void requireCurrency(String held, String requested) {
        if (!held.equals(requested)) {
            throw new BankingException(TellerErrorCode.CURRENCY_MISMATCH);
        }
    }

    private static void requireCashManager(boolean cashManager) {
        if (!cashManager) {
            throw new BankingException(CommonErrorCode.ACCESS_DENIED, "Moving vault cash needs cash.manage.");
        }
    }

    private static void requireStatus(CashMovement movement, CashMovement.Status expected) {
        if (movement.getStatus() != expected) {
            throw new BankingException(TellerErrorCode.MOVEMENT_NOT_PENDING);
        }
    }

    private static String humanType(CashMovement movement) {
        return switch (movement.getMovementType()) {
            case VAULT_TO_DRAWER -> "Vault to drawer";
            case DRAWER_TO_VAULT -> "Drawer to vault";
            case VAULT_TO_VAULT -> "Vault to vault";
            case BANK_TO_VAULT -> "Bank to vault";
            case VAULT_TO_BANK -> "Vault to bank";
        };
    }

    private void audit(String action, CashMovement movement, String note) {
        auditService.record(AuditEvent.builder(action, RESOURCE)
                .resourceId(movement.getId())
                .resourceReference(movement.getReference())
                .branchId(movement.getFromBranchId())
                .metadata("status", movement.getStatus())
                .metadata("note", blankToNull(note))
                .build());
    }

    private CashMovementResponse toResponse(CashMovement movement) {
        return new CashMovementResponse(movement.getId(), movement.getReference(), movement.getMovementType().name(),
                movement.getStatus().name(), movement.getCurrency(),
                currencies.present(movement.getAmount(), movement.getCurrency()), movement.getFromBranchId(),
                movement.getToBranchId(), movement.getFromVaultId(), movement.getFromDrawerId(),
                movement.getToVaultId(), movement.getToDrawerId(), movement.getNote(), movement.getRequestedBy(),
                movement.getRequestedAt(), movement.getApprovedBy(), movement.getApprovedAt(),
                movement.getReceivedBy(), movement.getReceivedAt(), movement.getRejectionReason(),
                movement.getVersion());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
