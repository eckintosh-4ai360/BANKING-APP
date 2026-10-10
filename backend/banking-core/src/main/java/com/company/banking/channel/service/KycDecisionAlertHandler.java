package com.company.banking.channel.service;

import com.company.banking.channel.entity.CustomerCredential;
import com.company.banking.channel.entity.CustomerNotification;
import com.company.banking.channel.repository.CustomerCredentialRepository;
import com.company.banking.channel.repository.CustomerOnboardingRepository;
import com.company.banking.common.outbox.OutboxEventHandler;
import com.company.banking.common.outbox.OutboxMessage;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.service.TenantService;
import java.nio.charset.StandardCharsets;
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
 * Tells customers who use the app what the KYC reviewer decided, in the inbox and by text: someone signing up learns
 * they can open their account, have details to correct or were not approved; an existing customer learns their
 * details were verified. The reviewer's note is never sent (what to correct is shown in the app).
 */
@Component
@RequiredArgsConstructor
class KycDecisionAlertHandler implements OutboxEventHandler {

    private static final Set<String> EVENTS = Set.of("KYC_CASE_APPROVED", "KYC_CASE_RETURNED", "KYC_CASE_REJECTED");

    private final CustomerCredentialRepository credentials;
    private final CustomerOnboardingRepository onboardings;
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
     * redelivered event finds its notification already there).
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(OutboxMessage message) {
        JsonNode event = jsonMapper.readTree(message.payloadJson());
        UUID tenantId = TenantContext.requireTenantId();
        UUID customerId = UUID.fromString(event.path("customerId").asString());
        // A decision taken before they set up mobile banking is not news to them.
        Optional<CustomerCredential> credential = credentials.findByTenantIdAndCustomerId(tenantId, customerId)
                .filter(CustomerCredential::isActive)
                .filter(found -> !found.getCreatedAt().isAfter(message.occurredAt()));
        if (credential.isEmpty()) {
            return;
        }
        boolean signingUp = onboardings.findByTenantIdAndCustomerId(tenantId, customerId)
                .filter(onboarding -> !onboarding.isCompleted()).isPresent();
        String[] notice = switch (message.eventType()) {
            case "KYC_CASE_APPROVED" -> signingUp
                    ? new String[]{"You are approved", "your details are approved. Open your account in the app."}
                    : new String[]{"Details verified", "your details are verified."};
            case "KYC_CASE_RETURNED" -> signingUp
                    ? new String[]{"Details to correct", "some of your details need correcting. Check them in the"
                    + " app and submit them again."}
                    : null;
            case "KYC_CASE_REJECTED" -> signingUp
                    ? new String[]{"Sign-up not approved", "we could not approve your sign-up. Contact us for help."}
                    : null;
            default -> null;
        };
        if (notice == null) {
            return;
        }
        String text = notice[1];
        boolean added = inbox.post(customerId, new CustomerInbox.Message(CustomerNotification.Category.ACCOUNT,
                notice[0], Character.toUpperCase(text.charAt(0)) + text.substring(1), "KYC_CASE",
                message.aggregateId(), UUID.nameUUIDFromBytes(message.id().toString().getBytes(StandardCharsets.UTF_8))));
        if (added) {
            String institution = tenantService.findById(tenantId).map(TenantSummary::displayName)
                    .orElse("Your bank");
            texts.noticeAfterCommit(credential.get().getUsername(), institution + ": " + text);
        }
    }
}
