package com.company.banking.channel.service;

import com.company.banking.account.dto.AccountStatement;
import com.company.banking.account.dto.AccountSummary;
import com.company.banking.account.dto.TransferDestination;
import com.company.banking.account.service.AccountService;
import com.company.banking.account.service.AccountStatementService;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.channel.dto.BankingDtos;
import com.company.banking.channel.entity.Beneficiary;
import com.company.banking.channel.entity.ChannelSettings;
import com.company.banking.channel.entity.CustomerCredential;
import com.company.banking.channel.entity.CustomerDevice;
import com.company.banking.channel.exception.ChannelErrorCode;
import com.company.banking.channel.repository.BeneficiaryRepository;
import com.company.banking.channel.repository.CustomerCredentialRepository;
import com.company.banking.channel.repository.CustomerDeviceRepository;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.idempotency.IdempotencyService.Result;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.tenant.service.TenantService;
import com.company.banking.transaction.dto.CustomerTransferCommand;
import com.company.banking.transaction.dto.MovementResponse;
import com.company.banking.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in customer's accounts, statements, transfers and beneficiaries.
 *
 * <p>A transfer is confirmed with the transaction PIN. Transfers to other people are limited by the institution's
 * settings (per transfer and per day, in its base currency) and more tightly while the destination is new (a
 * beneficiary still cooling down, or an account number typed in) or the device was trusted recently; transfers
 * between the customer's own accounts are limited only by their products. Retried with the same idempotency key, a
 * transfer moves money once; limits and the PIN are checked only on the first attempt.
 */
@Service
@RequiredArgsConstructor
public class CustomerBankingService {

    private static final String RESOURCE = "BENEFICIARY";

    private final AccountService accountService;
    private final AccountStatementService statementService;
    private final TransactionService transactionService;
    private final CustomerCredentialRepository credentials;
    private final CustomerDeviceRepository devices;
    private final BeneficiaryRepository beneficiaries;
    private final ChannelSettingsService settingsService;
    private final PinService pinService;
    private final OtpService otp;
    private final CurrencyService currencies;
    private final TenantService tenantService;
    private final AuditService auditService;
    private final Clock clock;

    // ---------------------------------------------------------------------------------------------- accounts

    @Transactional(readOnly = true)
    public BankingDtos.Accounts accounts() {
        CustomerAuthService.requireCustomer();
        List<BankingDtos.Account> accounts = accountService.heldByCurrentCustomer().stream()
                .map(CustomerBankingService::toResponse).toList();
        Map<String, BigDecimal> totals = new TreeMap<>();
        accounts.stream().filter(account -> !"CLOSED".equals(account.status()))
                .forEach(account -> totals.merge(account.currency(), account.availableBalance(), BigDecimal::add));
        List<BankingDtos.Total> summed = new ArrayList<>();
        totals.forEach((currency, available) -> summed.add(new BankingDtos.Total(currency,
                currencies.present(available, currency))));
        return new BankingDtos.Accounts(accounts, summed);
    }

    /**
     * The statement of one of the customer's own accounts.
     */
    @Transactional(readOnly = true)
    public AccountStatement statement(UUID accountId, LocalDate from, LocalDate to) {
        CustomerAuthService.requireCustomer();
        return statementService.statement(accountId, from, to);
    }

    @Transactional
    public AccountStatementService.StatementFile statementFile(UUID accountId, LocalDate from, LocalDate to,
                                                               AccountStatementService.Format format) {
        CustomerAuthService.requireCustomer();
        return statementService.export(accountId, from, to, format);
    }

    /**
     * Who an account number of the institution belongs to (partly hidden), to check before paying it.
     */
    @Transactional(readOnly = true)
    public BankingDtos.Destination destination(String accountNumber) {
        CustomerAuthService.requireCustomer();
        TransferDestination destination = payable(accountNumber);
        return new BankingDtos.Destination(destination.accountNumber(), maskName(destination.title()),
                destination.currency());
    }

    // --------------------------------------------------------------------------------------------- transfers

    @Transactional
    public BankingDtos.TransferReceipt transfer(String idempotencyKey, BankingDtos.Transfer request) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        String deviceKey = CustomerAuthService.requireDeviceKey();
        if ((request.beneficiaryId() == null) == (request.toAccountNumber() == null
                || request.toAccountNumber().isBlank())) {
            throw new BankingException(CommonErrorCode.VALIDATION_FAILED,
                    "Pay either a saved beneficiary or an account number.");
        }
        Optional<Beneficiary> beneficiary = Optional.ofNullable(request.beneficiaryId())
                .map(id -> beneficiaries.findByTenantIdAndId(actor.tenantId(), id)
                        .filter(found -> found.getCustomerId().equals(actor.id()) && found.isActive())
                        .orElseThrow(() -> new ResourceNotFoundException("Beneficiary")));
        UUID toAccountId = beneficiary.map(Beneficiary::getAccountId)
                .orElseGet(() -> payable(request.toAccountNumber()).accountId());
        boolean ownAccount = accountService.heldByCurrentCustomer().stream()
                .anyMatch(account -> account.id().equals(toAccountId));
        CustomerTransferCommand command = new CustomerTransferCommand(request.fromAccountId(), toAccountId,
                request.amount(), request.narration());

        Result<MovementResponse> result = transactionService.customerTransfer(idempotencyKey, command, currency -> {
            pinService.requireForPayment(actor.id(), request.pin());
            // Serialises the customer's transfers, so two at once cannot both pass the daily limit.
            credentials.lockByCustomerId(actor.tenantId(), actor.id())
                    .orElseThrow(() -> new BankingException(CommonErrorCode.UNAUTHENTICATED));
            if (!ownAccount) {
                requireWithinLimits(actor, deviceKey, beneficiary, request.amount(), currency);
            }
        });
        MovementResponse movement = result.response();
        if (!result.replayed() && !ownAccount) {
            CustomerCredential credential = credentials.findByTenantIdAndCustomerId(actor.tenantId(), actor.id())
                    .orElseThrow();
            otp.noticeAfterCommit(credential.getUsername(), institution(actor) + ": you sent "
                    + movement.transaction().currency() + " " + movement.transaction().amount().toPlainString()
                    + " from the app (ref " + movement.transaction().reference() + ").");
        }
        BigDecimal availableAfter = movement.balances().stream()
                .filter(balance -> balance.accountId().equals(request.fromAccountId()))
                .map(MovementResponse.BalanceAfter::availableBalance)
                .findFirst().orElse(null);
        TransferDestination to = accountService.destinationById(toAccountId).orElseThrow();
        return new BankingDtos.TransferReceipt(movement.transaction().id(), movement.transaction().reference(),
                movement.transaction().amount(), movement.transaction().feeAmount(),
                movement.transaction().currency(), movement.transaction().debitAccountNumber(), to.accountNumber(),
                ownAccount ? to.title() : maskName(to.title()), availableAfter,
                movement.transaction().businessDate(), movement.transaction().createdAt());
    }

    /**
     * The institution's limits for a transfer to someone else, checked under the customer's lock.
     */
    private void requireWithinLimits(AuthenticatedActor actor, String deviceKey, Optional<Beneficiary> beneficiary,
                                     BigDecimal amount, String currency) {
        String base = tenantService.getCurrent().baseCurrency();
        if (!base.equals(currency)) {
            throw new BankingException(ChannelErrorCode.TRANSFER_LIMIT, "Transfers to others from the app are in "
                    + base + " only.");
        }
        ChannelSettings settings = settingsService.current();
        Instant now = clock.instant();
        BigDecimal cap = settings.getMaxTransferAmount();
        String why = "the most you can send in one transfer";
        boolean newDestination = beneficiary.map(found -> found.isCoolingDown(now)).orElse(true);
        if (newDestination && settings.getCooldownMaxAmount().compareTo(cap) < 0) {
            cap = settings.getCooldownMaxAmount();
            why = "the most you can send to a new beneficiary or an account you typed in";
        }
        Optional<CustomerDevice> device = devices.findTrusted(actor.tenantId(), actor.id(), deviceKey);
        boolean newDevice = device.map(found -> found.getBoundAt().plus(Duration.ofHours(
                settings.getNewDeviceCooldownHours())).isAfter(now)).orElse(true);
        if (newDevice && settings.getNewDeviceMaxAmount().compareTo(cap) < 0) {
            cap = settings.getNewDeviceMaxAmount();
            why = "the most you can send from a device added in the last " + settings.getNewDeviceCooldownHours()
                    + " hours";
        }
        if (beneficiary.isPresent() && beneficiary.get().getTransferLimit() != null
                && beneficiary.get().getTransferLimit().compareTo(cap) < 0) {
            cap = beneficiary.get().getTransferLimit();
            why = "the limit you set for this beneficiary";
        }
        if (amount.compareTo(cap) > 0) {
            throw new BankingException(ChannelErrorCode.TRANSFER_LIMIT, base + " "
                    + currencies.present(cap, base).toPlainString() + " is " + why + ".");
        }
        BigDecimal sentToday = transactionService.mobileTransfersToday(actor.id(), base,
                accountService.heldByCurrentCustomer().stream().map(AccountSummary::id).toList());
        if (sentToday.add(amount).compareTo(settings.getDailyTransferLimit()) > 0) {
            throw new BankingException(ChannelErrorCode.TRANSFER_LIMIT, "This would take you over the daily limit of "
                    + base + " " + currencies.present(settings.getDailyTransferLimit(), base).toPlainString()
                    + " for transfers from the app.");
        }
    }

    // ------------------------------------------------------------------------------------------ beneficiaries

    @Transactional(readOnly = true)
    public List<BankingDtos.Beneficiary> beneficiaries() {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        return beneficiaries.findActive(actor.tenantId(), actor.id()).stream().map(this::toResponse).toList();
    }

    /**
     * Saves an account of the institution as a beneficiary, confirmed with the PIN. Transfers to it are capped by the
     * cooldown limit for the institution's cooldown period; the customer is told by text.
     */
    @Transactional
    public BankingDtos.Beneficiary addBeneficiary(BankingDtos.NewBeneficiary request) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        if (!"INTERNAL".equals(request.type())) {
            throw new BankingException(ChannelErrorCode.BENEFICIARY_TYPE_NOT_AVAILABLE);
        }
        TransferDestination destination = payable(request.accountNumber());
        if (accountService.heldByCurrentCustomer().stream().anyMatch(own -> own.id().equals(destination.accountId()))) {
            throw new BankingException(ChannelErrorCode.OWN_ACCOUNT);
        }
        if (beneficiaries.findActiveForAccount(actor.tenantId(), actor.id(), destination.accountId()).isPresent()) {
            throw new BankingException(ChannelErrorCode.BENEFICIARY_EXISTS);
        }
        validLimit(request.transferLimit(), destination.currency());
        pinService.requireForPayment(actor.id(), request.pin());
        Instant now = clock.instant();
        ChannelSettings settings = settingsService.current();
        Beneficiary saved = beneficiaries.saveAndFlush(new Beneficiary(UuidV7.next(), actor.tenantId(), actor.id(),
                Beneficiary.Type.INTERNAL, request.nickname().trim(), destination.accountId(),
                maskName(destination.title()), request.transferLimit(), now,
                now.plus(Duration.ofHours(settings.getBeneficiaryCooldownHours()))));
        if (request.favourite()) {
            saved.edit(saved.getNickname(), true, saved.getTransferLimit());
        }
        auditService.record(AuditEvent.builder("BENEFICIARY_ADDED", RESOURCE)
                .resourceId(saved.getId())
                .metadata("type", saved.getBeneficiaryType().name())
                .build());
        credentials.findByTenantIdAndCustomerId(actor.tenantId(), actor.id()).ifPresent(credential ->
                otp.noticeAfterCommit(credential.getUsername(), institution(actor) + ": a new beneficiary ("
                        + saved.getNickname() + ") was added to your mobile banking. If this was not you, contact"
                        + " us at once."));
        return toResponse(saved);
    }

    @Transactional
    public BankingDtos.Beneficiary editBeneficiary(UUID beneficiaryId, BankingDtos.BeneficiaryEdit request) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        Beneficiary beneficiary = owned(actor, beneficiaryId);
        ConcurrentModificationException.assertVersion(request.version(), beneficiary.getVersion());
        BigDecimal current = beneficiary.getTransferLimit();
        BigDecimal requested = request.transferLimit();
        boolean raised = current != null && (requested == null || requested.compareTo(current) > 0);
        if (raised) {
            if (request.pin() == null) {
                throw new BankingException(ChannelErrorCode.WRONG_PIN, "Raising or removing the limit needs your"
                        + " transaction PIN.");
            }
            pinService.requireForPayment(actor.id(), request.pin());
        }
        if (requested != null) {
            validLimit(requested, tenantService.getCurrent().baseCurrency());
        }
        beneficiary.edit(request.nickname().trim(), request.favourite(), requested);
        beneficiaries.saveAndFlush(beneficiary);
        auditService.record(AuditEvent.builder("BENEFICIARY_EDITED", RESOURCE)
                .resourceId(beneficiaryId)
                .metadata("limitRaised", raised)
                .build());
        return toResponse(beneficiary);
    }

    @Transactional
    public void removeBeneficiary(UUID beneficiaryId) {
        AuthenticatedActor actor = CustomerAuthService.requireCustomer();
        Beneficiary beneficiary = owned(actor, beneficiaryId);
        beneficiary.remove(clock.instant());
        beneficiaries.saveAndFlush(beneficiary);
        auditService.record(AuditEvent.builder("BENEFICIARY_REMOVED", RESOURCE).resourceId(beneficiaryId).build());
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private TransferDestination payable(String accountNumber) {
        return accountService.destinationByNumber(accountNumber == null ? "" : accountNumber.trim())
                .filter(destination -> "ACTIVE".equals(destination.status()) || "DORMANT".equals(destination.status()))
                .orElseThrow(() -> new BankingException(ChannelErrorCode.DESTINATION_NOT_FOUND));
    }

    private Beneficiary owned(AuthenticatedActor actor, UUID beneficiaryId) {
        return beneficiaries.findByTenantIdAndId(actor.tenantId(), beneficiaryId)
                .filter(found -> found.getCustomerId().equals(actor.id()) && found.isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary"));
    }

    private void validLimit(BigDecimal limit, String currency) {
        if (limit != null) {
            currencies.requireValidAmount(limit, currency);
        }
    }

    private String institution(AuthenticatedActor actor) {
        return tenantService.findById(actor.tenantId()).map(tenant -> tenant.displayName()).orElse("Your bank");
    }

    private BankingDtos.Beneficiary toResponse(Beneficiary beneficiary) {
        Instant now = clock.instant();
        TransferDestination destination = accountService.destinationById(beneficiary.getAccountId()).orElse(null);
        String currency = destination == null ? tenantService.getCurrent().baseCurrency() : destination.currency();
        return new BankingDtos.Beneficiary(beneficiary.getId(), beneficiary.getBeneficiaryType().name(),
                beneficiary.getNickname(), destination == null ? null : destination.accountNumber(),
                beneficiary.getDisplayName(), beneficiary.isFavourite(),
                beneficiary.getTransferLimit() == null ? null
                        : currencies.present(beneficiary.getTransferLimit(), currency),
                beneficiary.isCoolingDown(now) ? beneficiary.getCooldownUntil() : null, beneficiary.getCreatedAt(),
                beneficiary.getVersion());
    }

    private static BankingDtos.Account toResponse(AccountSummary account) {
        return new BankingDtos.Account(account.id(), account.accountNumber(), account.title(), account.productCode(),
                account.productType(), account.currency(), account.status(), account.ledgerBalance(),
                account.availableBalance(), account.openedOn());
    }

    /**
     * A name partly hidden for someone who is not its owner: the first word, then initials ("Akosua M.").
     */
    static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] words = name.trim().split("\\s+");
        StringBuilder masked = new StringBuilder(words[0]);
        for (int index = 1; index < words.length; index++) {
            masked.append(' ').append(words[index].substring(0, 1).toUpperCase(Locale.ROOT)).append('.');
        }
        return masked.toString();
    }
}
