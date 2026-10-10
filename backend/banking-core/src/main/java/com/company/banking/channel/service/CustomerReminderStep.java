package com.company.banking.channel.service;

import com.company.banking.channel.entity.CustomerCredential;
import com.company.banking.channel.entity.CustomerNotification;
import com.company.banking.channel.repository.CustomerCredentialRepository;
import com.company.banking.common.eod.EndOfDayContext;
import com.company.banking.common.eod.EndOfDayStep;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.loan.dto.LoanDtos;
import com.company.banking.loan.service.LoanService;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.service.TenantService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * End-of-day ({@code CUSTOMER_REMINDERS}, after the loan portfolio step): customers who use the app are reminded of
 * loan installments falling due on the next business date, in the inbox and by text when they chose text alerts.
 * One reminder per installment and due date, so a resumed run sends nothing twice.
 */
@Component
@RequiredArgsConstructor
class CustomerReminderStep implements EndOfDayStep {

    static final String STEP = "CUSTOMER_REMINDERS";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final LoanService loanService;
    private final CustomerCredentialRepository credentials;
    private final CustomerInbox inbox;
    private final OtpService texts;
    private final TenantService tenantService;

    @Override
    public String code() {
        return STEP;
    }

    @Override
    public int order() {
        return 360;
    }

    @Override
    public Map<String, Object> run(EndOfDayContext context) {
        LocalDate due = context.nextBusinessDate();
        int sent = context.inTransaction(() -> {
            UUID tenantId = TenantContext.requireTenantId();
            String institution = tenantService.findById(tenantId).map(TenantSummary::displayName)
                    .orElse("Your bank");
            int count = 0;
            for (LoanDtos.DueInstallment installment : loanService.installmentsDueOn(due)) {
                Optional<CustomerCredential> credential = credentials.findByTenantIdAndCustomerId(tenantId,
                        installment.customerId()).filter(CustomerCredential::isActive);
                if (credential.isEmpty()) {
                    continue;
                }
                String text = installment.currency() + " " + installment.amount().toPlainString() + " is due on "
                        + DAY.format(installment.dueDate()) + " for loan " + installment.loanNumber() + ".";
                boolean added = inbox.post(installment.customerId(), new CustomerInbox.Message(
                        CustomerNotification.Category.LOAN, "Repayment due", text, "LOAN", installment.loanId(),
                        UUID.nameUUIDFromBytes(("reminder:" + installment.loanId() + ":" + installment.dueDate())
                                .getBytes(StandardCharsets.UTF_8))));
                if (added) {
                    count++;
                    if (credential.get().isSmsAlerts()) {
                        texts.noticeAfterCommit(credential.get().getUsername(), institution + ": " + text);
                    }
                }
            }
            return count;
        });
        context.checkpoint(STEP, "sent");
        return Map.of("reminders", sent, "dueOn", due.toString());
    }
}
