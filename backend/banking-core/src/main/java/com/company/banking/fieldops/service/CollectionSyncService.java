package com.company.banking.fieldops.service;

import com.company.banking.account.service.AccountService;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.fieldops.dto.FieldResponses;
import com.company.banking.fieldops.dto.SyncRequest;
import com.company.banking.fieldops.dto.SyncRequest.CollectionItem;
import com.company.banking.fieldops.dto.SyncRequest.VisitItem;
import com.company.banking.fieldops.dto.SyncResponse;
import com.company.banking.fieldops.dto.SyncResponse.CollectionResult;
import com.company.banking.fieldops.dto.SyncResponse.VisitResult;
import com.company.banking.fieldops.entity.Collection;
import com.company.banking.fieldops.entity.CustomerVisit;
import com.company.banking.fieldops.entity.FieldAlert;
import com.company.banking.fieldops.entity.FieldDevice;
import com.company.banking.fieldops.entity.FieldOfficer;
import com.company.banking.fieldops.exception.FieldErrorCode;
import com.company.banking.fieldops.repository.CollectionRepository;
import com.company.banking.fieldops.repository.CustomerVisitRepository;
import com.company.banking.fieldops.repository.FieldOfficerRepository;
import com.company.banking.fieldops.repository.SequenceGapRepository.Gap;
import com.company.banking.susu.service.SusuPlanService;
import com.company.banking.transaction.dto.FieldCollectionCommand;
import com.company.banking.transaction.dto.MovementResponse;
import com.company.banking.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Takes in what a field device recorded offline. Every collection is handled in its own transaction, under the
 * device's row lock:
 * <ol>
 *   <li>a client reference already received is answered from what was stored (the same collection) or is a
 *   conflict (a different one; nothing happens and a supervisor is alerted), as is a used sequence number;</li>
 *   <li>otherwise it is posted (Dr the officer's cash with collectors, Cr the account) or, when a rule refuses it,
 *   recorded as rejected: the cash is still with the officer and the sequence number is accounted for.</li>
 * </ol>
 * Sending a batch again therefore posts nothing twice. Afterwards the device's sequence gaps, late collections and
 * offline cash above the officer's limit raise alerts.
 */
@Service
public class CollectionSyncService {

    static final String RESOURCE = "FIELD_COLLECTION";
    /** Phone clocks drift: a collection may be dated this far ahead of the server. */
    private static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(5);
    /** Search bound when none is given (a rejected collection may be dated in the future). */
    private static final Instant NO_END = Instant.parse("3000-01-01T00:00:00Z");

    private final FieldOfficerService officerService;
    private final FieldOfficerRepository officers;
    private final FieldDeviceService deviceService;
    private final CustomerAssignmentService assignmentService;
    private final FieldAlertService alertService;
    private final FieldScope fieldScope;
    private final CollectionRepository collections;
    private final CustomerVisitRepository visits;
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final SusuPlanService susuPlans;
    private final AuditService auditService;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public CollectionSyncService(FieldOfficerService officerService, FieldOfficerRepository officers,
                                 FieldDeviceService deviceService, CustomerAssignmentService assignmentService,
                                 FieldAlertService alertService, FieldScope fieldScope,
                                 CollectionRepository collections, CustomerVisitRepository visits,
                                 TransactionService transactionService, AccountService accountService,
                                 SusuPlanService susuPlans, AuditService auditService,
                                 PlatformTransactionManager transactionManager, Clock clock) {
        this.officerService = officerService;
        this.officers = officers;
        this.deviceService = deviceService;
        this.assignmentService = assignmentService;
        this.alertService = alertService;
        this.fieldScope = fieldScope;
        this.collections = collections;
        this.visits = visits;
        this.transactionService = transactionService;
        this.accountService = accountService;
        this.susuPlans = susuPlans;
        this.auditService = auditService;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public SyncResponse sync(SyncRequest request) {
        FieldOfficer officer = inTransaction(officerService::requireCurrentOfficer);
        UUID deviceId = request.deviceId();
        inTransaction(() -> deviceService.lockOwnLiveDevice(deviceId, officer.getStaffId()));
        Instant receivedAt = clock.instant();

        List<CollectionResult> collectionResults = new ArrayList<>();
        List<Collection> postedNow = new ArrayList<>();
        request.collectionsOrEmpty().stream()
                .sorted(Comparator.comparing(CollectionItem::sequenceNo))
                .forEach(item -> collectionResults.add(syncCollection(officer, deviceId, item, receivedAt,
                        postedNow)));
        List<VisitResult> visitResults = request.visitsOrEmpty().stream()
                .map(item -> syncVisit(officer, deviceId, item, receivedAt))
                .toList();

        List<Gap> gaps = inTransaction(() -> finish(officer, deviceId, receivedAt, postedNow));
        long highest = inTransaction(() -> collections.highestSequenceNo(deviceId));
        return new SyncResponse(collectionResults, visitResults, highest,
                gaps.stream().map(gap -> new SyncResponse.Range(gap.from(), gap.to())).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<FieldResponses.CollectionView> search(UUID officerId, UUID customerId, String status,
                                                             Instant from, Instant to, PageRequest page) {
        Set<UUID> visible = fieldScope.officers(officerId);
        if (visible.isEmpty()) {
            return PageResponse.of(List.of(), page.getPageNumber(), page.getPageSize(), 0);
        }
        return PageResponse.from(collections.search(TenantContext.requireTenantId(), visible, customerId,
                        status == null ? null : Collection.Status.valueOf(status),
                        from == null ? Instant.EPOCH : from, to == null ? NO_END : to, page),
                CollectionSyncService::toView);
    }

    @Transactional(readOnly = true)
    public PageResponse<FieldResponses.Visit> visits(UUID officerId, UUID customerId, PageRequest page) {
        Set<UUID> visible = fieldScope.officers(officerId);
        if (visible.isEmpty()) {
            return PageResponse.of(List.of(), page.getPageNumber(), page.getPageSize(), 0);
        }
        return PageResponse.from(visits.search(TenantContext.requireTenantId(), visible, customerId, page),
                visit -> new FieldResponses.Visit(visit.getId(), visit.getClientReference(), visit.getOfficerId(),
                        visit.getCustomerId(), visit.getPurpose(), visit.getOutcome(), visit.getNotes(),
                        visit.getVisitedAt(), visit.getReceivedAt(), visit.getLatitude(), visit.getLongitude()));
    }

    // ---------------------------------------------------------------------------------------------- collections

    private CollectionResult syncCollection(FieldOfficer officer, UUID deviceId, CollectionItem item,
                                            Instant receivedAt, List<Collection> postedNow) {
        String hash = contentHash(deviceId, item);
        try {
            return inTransaction(() -> process(officer, deviceId, item, hash, receivedAt, postedNow));
        } catch (DataIntegrityViolationException race) {
            // The same reference or number was stored in parallel: answer as for any resubmission.
            return inTransaction(() -> answerKnown(officer, deviceId, item, hash));
        } catch (BankingException rejection) {
            if (rejection.getErrorCode() == FieldErrorCode.DEVICE_NOT_REGISTERED) {
                throw rejection; // revoked while syncing: nothing more is taken from it
            }
            return inTransaction(() -> reject(officer, deviceId, item, hash, receivedAt, rejection));
        }
    }

    private CollectionResult process(FieldOfficer officer, UUID deviceId, CollectionItem item, String hash,
                                     Instant receivedAt, List<Collection> postedNow) {
        deviceService.lockOwnLiveDevice(deviceId, officer.getStaffId());
        CollectionResult known = answerKnown(officer, deviceId, item, hash);
        if (known != null) {
            return known;
        }
        FieldOfficer current = officers.lockShared(officer.getTenantId(), officer.getStaffId()).orElseThrow();
        if (!current.isActive()) {
            throw new BankingException(FieldErrorCode.OFFICER_SUSPENDED);
        }
        if (item.collectedAt().isAfter(receivedAt.plus(CLOCK_TOLERANCE))) {
            throw new BankingException(FieldErrorCode.INVALID_COLLECTION_TIME);
        }
        if (!assignmentService.lockIsAssigned(item.customerId(), officer.getStaffId())) {
            throw new BankingException(FieldErrorCode.CUSTOMER_NOT_ASSIGNED);
        }
        boolean held = accountService.heldBy(item.customerId()).stream()
                .anyMatch(account -> account.id().equals(item.accountId()));
        if (!held) {
            throw new BankingException(FieldErrorCode.ACCOUNT_NOT_FOR_CUSTOMER);
        }
        MovementResponse posted = transactionService.postFieldCollection(new FieldCollectionCommand(
                item.accountId(), item.amount(), item.currency(), current.getCollectorLedgerAccountId(),
                current.getBranchId(), "Field collection", item.clientReference().toString()));
        Collection collection = collections.saveAndFlush(base(officer, deviceId, item, hash, receivedAt)
                .status(Collection.Status.POSTED)
                .financialTransactionId(posted.transaction().id())
                .transactionReference(posted.transaction().reference())
                .businessDate(posted.transaction().businessDate())
                .build());
        if (item.susuPlanId() != null) {
            // Throws (and so rolls back the posting) when the plan is not owed this.
            susuPlans.applyCollection(item.susuPlanId(), item.customerId(), item.accountId(), item.amount(),
                    collection.getId(), collection.getFinancialTransactionId(), item.collectedAt());
        }
        postedNow.add(collection);
        BigDecimal balance = posted.balances().stream()
                .filter(after -> after.accountId().equals(item.accountId()))
                .map(MovementResponse.BalanceAfter::ledgerBalance)
                .findFirst().orElse(null);
        return new CollectionResult(item.clientReference(), "POSTED", null, null, null, collection.getId(),
                collection.getTransactionReference(), collection.getBusinessDate(), balance);
    }

    /**
     * Records a collection a rule refused. The cash stays with the officer until it is returned or collected again
     * correctly; the record accounts for the device's sequence number.
     */
    private CollectionResult reject(FieldOfficer officer, UUID deviceId, CollectionItem item, String hash,
                                    Instant receivedAt, BankingException rejection) {
        deviceService.lockOwnLiveDevice(deviceId, officer.getStaffId());
        CollectionResult known = answerKnown(officer, deviceId, item, hash);
        if (known != null) {
            return known;
        }
        String code = rejection.getErrorCode().code();
        String reason = truncate(rejection.getMessage(), 300);
        Collection collection = collections.saveAndFlush(base(officer, deviceId, item, hash, receivedAt)
                .status(Collection.Status.REJECTED)
                .rejectionCode(code)
                .rejectionReason(reason)
                .build());
        auditService.record(AuditEvent.builder("FIELD_COLLECTION_REJECTED", RESOURCE)
                .resourceId(collection.getId())
                .resourceReference(item.clientReference().toString())
                .branchId(officer.getBranchId())
                .metadata("code", code)
                .metadata("officerId", officer.getStaffId().toString())
                .build());
        return new CollectionResult(item.clientReference(), "REJECTED", null, code, reason, collection.getId(), null,
                null, null);
    }

    /**
     * The answer for a reference or number already received: a duplicate repeats the first outcome; anything else
     * under a used reference or number is a conflict and alerts a supervisor. Null when neither was received.
     */
    private CollectionResult answerKnown(FieldOfficer officer, UUID deviceId, CollectionItem item, String hash) {
        Collection byReference = collections.findByTenantIdAndClientReference(officer.getTenantId(),
                item.clientReference()).orElse(null);
        if (byReference != null) {
            if (byReference.getContentHash().equals(hash)) {
                return new CollectionResult(item.clientReference(), "DUPLICATE", byReference.getStatus().name(),
                        byReference.getRejectionCode(), byReference.getRejectionReason(), byReference.getId(),
                        byReference.getTransactionReference(), byReference.getBusinessDate(), null);
            }
            return conflict(officer, deviceId, item, "Reference " + item.clientReference()
                    + " was already used for a different collection");
        }
        Collection bySequence = collections.findByDeviceIdAndDeviceSequenceNo(deviceId, item.sequenceNo())
                .orElse(null);
        if (bySequence != null) {
            return conflict(officer, deviceId, item, "Number " + item.sequenceNo()
                    + " of the device was already used for collection " + bySequence.getClientReference());
        }
        return null;
    }

    private CollectionResult conflict(FieldOfficer officer, UUID deviceId, CollectionItem item, String detail) {
        alertService.raise(officer, deviceId, FieldAlert.Type.CONFLICT, detail, item.clientReference());
        return new CollectionResult(item.clientReference(), "CONFLICT", null, "COLLECTION_CONFLICT", detail, null,
                null, null, null);
    }

    /**
     * After the items: the device's highest number and sync time, alerts for collections that waited too long or
     * offline cash above the limit, and the device's gaps.
     */
    private List<Gap> finish(FieldOfficer officer, UUID deviceId, Instant receivedAt, List<Collection> postedNow) {
        FieldDevice device = deviceService.lockOwnLiveDevice(deviceId, officer.getStaffId());
        device.recordSync(collections.highestSequenceNo(deviceId), receivedAt);
        Instant lateBefore = receivedAt.minus(officer.getMaxOfflineHours(), ChronoUnit.HOURS);
        long late = postedNow.stream().filter(collection -> collection.getCollectedAt().isBefore(lateBefore)).count();
        if (late > 0) {
            alertService.raise(officer, deviceId, FieldAlert.Type.LATE_SYNC, late + (late == 1
                    ? " collection reached" : " collections reached") + " the server more than "
                    + officer.getMaxOfflineHours() + " hours after it was taken", null);
        }
        BigDecimal offline = postedNow.stream().map(Collection::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (offline.compareTo(officer.getMaxOfflineAmount()) > 0) {
            alertService.raise(officer, deviceId, FieldAlert.Type.OFFLINE_LIMIT, "The device held "
                    + offline.toPlainString() + " " + officer.getCurrency() + " in collections, above the limit of "
                    + officer.getMaxOfflineAmount().toPlainString(), null);
        }
        return alertService.refreshGaps(officer, deviceId);
    }

    // ---------------------------------------------------------------------------------------------------- visits

    private VisitResult syncVisit(FieldOfficer officer, UUID deviceId, VisitItem item, Instant receivedAt) {
        String hash = visitHash(item);
        try {
            return inTransaction(() -> {
                deviceService.lockOwnLiveDevice(deviceId, officer.getStaffId());
                VisitResult known = knownVisit(officer, deviceId, item, hash);
                if (known != null) {
                    return known;
                }
                if (!assignmentService.lockIsAssigned(item.customerId(), officer.getStaffId())) {
                    return new VisitResult(item.clientReference(), "REJECTED",
                            FieldErrorCode.CUSTOMER_NOT_ASSIGNED.code(),
                            FieldErrorCode.CUSTOMER_NOT_ASSIGNED.defaultMessage());
                }
                visits.saveAndFlush(CustomerVisit.builder()
                        .id(UuidV7.next())
                        .tenantId(officer.getTenantId())
                        .clientReference(item.clientReference())
                        .deviceId(deviceId)
                        .officerId(officer.getStaffId())
                        .customerId(item.customerId())
                        .purpose(item.purpose())
                        .outcome(item.outcome())
                        .notes(blankToNull(item.notes()))
                        .visitedAt(item.visitedAt())
                        .receivedAt(receivedAt)
                        .latitude(item.latitude())
                        .longitude(item.longitude())
                        .contentHash(hash)
                        .build());
                return new VisitResult(item.clientReference(), "RECORDED", null, null);
            });
        } catch (DataIntegrityViolationException race) {
            return inTransaction(() -> knownVisit(officer, deviceId, item, hash));
        }
    }

    private VisitResult knownVisit(FieldOfficer officer, UUID deviceId, VisitItem item, String hash) {
        CustomerVisit existing = visits.findByTenantIdAndClientReference(officer.getTenantId(),
                item.clientReference()).orElse(null);
        if (existing == null) {
            return null;
        }
        if (existing.getContentHash().equals(hash)) {
            return new VisitResult(item.clientReference(), "DUPLICATE", null, null);
        }
        String detail = "Reference " + item.clientReference() + " was already used for a different visit";
        alertService.raise(officer, deviceId, FieldAlert.Type.CONFLICT, detail, item.clientReference());
        return new VisitResult(item.clientReference(), "CONFLICT", "VISIT_CONFLICT", detail);
    }

    // --------------------------------------------------------------------------------------------------- helpers

    private Collection.CollectionBuilder base(FieldOfficer officer, UUID deviceId, CollectionItem item, String hash,
                                              Instant receivedAt) {
        return Collection.builder()
                .id(UuidV7.next())
                .tenantId(officer.getTenantId())
                .clientReference(item.clientReference())
                .deviceId(deviceId)
                .deviceSequenceNo(item.sequenceNo())
                .officerId(officer.getStaffId())
                .customerId(item.customerId())
                .targetType(item.susuPlanId() == null ? Collection.TargetType.SAVINGS_ACCOUNT
                        : Collection.TargetType.SUSU_PLAN)
                .accountId(item.accountId())
                .susuPlanId(item.susuPlanId())
                .amount(item.amount())
                .currency(item.currency())
                .collectedAt(item.collectedAt())
                .receivedAt(receivedAt)
                .latitude(item.latitude())
                .longitude(item.longitude())
                .note(blankToNull(item.note()))
                .contentHash(hash);
    }

    /**
     * What makes two submissions the same collection: device, number, customer, account, plan, amount, currency and
     * time taken (notes and location may be filled in differently on a resend).
     */
    static String contentHash(UUID deviceId, CollectionItem item) {
        return sha256(String.join("|", deviceId.toString(), item.sequenceNo().toString(),
                item.customerId().toString(), item.accountId().toString(), Objects.toString(item.susuPlanId(), ""),
                item.amount().stripTrailingZeros().toPlainString(), item.currency(),
                Long.toString(ChronoUnit.MICROS.between(Instant.EPOCH, item.collectedAt()))));
    }

    private static String visitHash(VisitItem item) {
        return sha256(String.join("|", item.customerId().toString(), item.purpose(), item.outcome(),
                Long.toString(ChronoUnit.MICROS.between(Instant.EPOCH, item.visitedAt()))));
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    static FieldResponses.CollectionView toView(Collection collection) {
        return new FieldResponses.CollectionView(collection.getId(), collection.getClientReference(),
                collection.getDeviceId(), collection.getDeviceSequenceNo(), collection.getOfficerId(),
                collection.getCustomerId(), collection.getTargetType().name(), collection.getAccountId(),
                collection.getSusuPlanId(), collection.getAmount(), collection.getCurrency(),
                collection.getCollectedAt(), collection.getReceivedAt(), collection.getStatus().name(),
                collection.getRejectionCode(), collection.getRejectionReason(),
                collection.getFinancialTransactionId(), collection.getBusinessDate(), collection.getLatitude(),
                collection.getLongitude(), collection.getNote());
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
