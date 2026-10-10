package com.company.banking.channel.service;

import com.company.banking.account.service.AccountService;
import com.company.banking.channel.entity.CustomerCredential;
import com.company.banking.channel.entity.CustomerNotification;
import com.company.banking.channel.repository.CustomerCredentialRepository;
import com.company.banking.common.outbox.OutboxEventHandler;
import com.company.banking.common.outbox.OutboxMessage;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.service.TenantService;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Alerts for money in and out: every posted or reversed transaction notifies the customers holding its accounts who
 * use the app (in the inbox, pushed to their devices, and texted when they chose text alerts). Runs from the outbox
 * after the posting committed, so it never holds up or undoes it; a redelivered event adds nothing.
 */
@Component
@RequiredArgsConstructor
class TransactionAlertHandler implements OutboxEventHandler {

    private static final Set<String> EVENTS = Set.of("TRANSACTION_POSTED", "TRANSACTION_REVERSED");

    private final AccountService accountService;
    private final CustomerCredentialRepository credentials;
    private final CustomerInbox inbox;
    private final OtpService texts;
    private final TenantService tenantService;
    private final JsonMapper jsonMapper;

    @Override
    public boolean supports(String eventType) {
        return EVENTS.contains(eventType);
    }

    /**
     * In its own transaction, so a failure here is retried on its own instead of undoing the relay's batch (a
     * redelivered event finds its notifications already there).
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OutboxMessage message) {
        JsonNode event = jsonMapper.readTree(message.payloadJson());
        boolean reversed = "TRANSACTION_REVERSED".equals(message.eventType());
        Map<UUID, Boolean> sides = new LinkedHashMap<>();
        uuid(event, "debitAccountId").ifPresent(account -> sides.put(account, true));
        uuid(event, "creditAccountId").ifPresent(account -> sides.put(account, false));
        if (sides.isEmpty()) {
            return;
        }
        String institution = tenantService.findById(TenantContext.requireTenantId())
                .map(TenantSummary::displayName).orElse("Your bank");
        Map<UUID, String> numbers = accountService.accountNumbers(sides.keySet());
        sides.forEach((accountId, debit) -> {
            String account = "account ending " + last4(numbers.get(accountId));
            String text = text(event, reversed, debit, account);
            String title = title(event.path("transactionType").asString(), reversed, debit);
            for (UUID customerId : accountService.holderIdsOf(accountId)) {
                // Money that moved before they set up mobile banking is not alerted.
                Optional<CustomerCredential> credential = credentials.findByTenantIdAndCustomerId(
                                TenantContext.requireTenantId(), customerId)
                        .filter(CustomerCredential::isActive)
                        .filter(found -> !found.getCreatedAt().isAfter(message.occurredAt()));
                if (credential.isEmpty()) {
                    continue;
                }
                boolean added = inbox.post(customerId, new CustomerInbox.Message(
                        CustomerNotification.Category.TRANSACTION, title, text, "FINANCIAL_TRANSACTION",
                        message.aggregateId(), sourceKey(message.id(), accountId)));
                if (added && credential.get().isSmsAlerts()) {
                    texts.noticeAfterCommit(credential.get().getUsername(), institution + ": " + text);
                }
            }
        });
    }

    private static String text(JsonNode event, boolean reversed, boolean debit, String account) {
        String money = event.path("currency").asString() + " " + event.path("amount").asString();
        String reference = event.path("reference").asString();
        if (reversed) {
            return "Transaction " + reference + " of " + money + " on your " + account + " was reversed.";
        }
        return money + (debit ? " was taken from your " : " was paid into your ") + account + ". Ref " + reference
                + ".";
    }

    private static String title(String type, boolean reversed, boolean debit) {
        if (reversed) {
            return "Transaction reversed";
        }
        return switch (type) {
            case "CASH_DEPOSIT" -> "Deposit received";
            case "CASH_WITHDRAWAL" -> "Cash withdrawn";
            case "FIELD_COLLECTION" -> "Collection received";
            case "LOAN_DISBURSEMENT" -> "Loan paid out";
            case "LOAN_REPAYMENT" -> "Loan repayment";
            default -> debit ? "Money sent" : "Money received";
        };
    }

    /**
     * One notification per event and account (a customer holding both accounts of a transfer gets both sides).
     */
    private static UUID sourceKey(UUID eventId, UUID accountId) {
        return UUID.nameUUIDFromBytes((eventId + ":" + accountId).getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<UUID> uuid(JsonNode event, String field) {
        JsonNode value = event.path(field);
        if (value.isMissingNode() || value.isNull() || value.asString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(value.asString()));
    }

    private static String last4(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }
        return accountNumber.substring(accountNumber.length() - 4);
    }
}
