package com.company.banking.manualjournal.service;

import com.company.banking.approval.dto.ApprovalResponse;
import com.company.banking.approval.dto.ApprovalSubmission;
import com.company.banking.approval.model.ApprovalType;
import com.company.banking.approval.service.ApprovalHandler;
import com.company.banking.approval.service.ApprovalService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.References;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.ledger.dto.GlAccountRef;
import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.PostingRequest;
import com.company.banking.ledger.exception.LedgerErrorCode;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.JournalSource;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.ledger.service.ChartOfAccountService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.ledger.service.PostingEngine;
import com.company.banking.manualjournal.dto.ManualJournalRequest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Manual journals: an accountant prepares one, a second person approves it, and only then the posting engine posts
 * it (as MANUAL, prepared by the maker and approved by the checker). The engine allows only GL accounts open to
 * manual posting and never customer sub-ledger accounts, so customer balances only change through transactions.
 */
@Service
@RequiredArgsConstructor
public class ManualJournalService implements ApprovalHandler {

    private final ApprovalService approvalService;
    private final ChartOfAccountService chartOfAccounts;
    private final CurrencyService currencies;
    private final PostingEngine postingEngine;
    private final BusinessDateService businessDates;
    private final JsonMapper jsonMapper;

    /**
     * Checks the journal now, so a checker never sees one that cannot post, and submits it for approval.
     */
    @Transactional
    public ApprovalResponse submit(ManualJournalRequest request) {
        BranchScope scope = CurrentActor.require().branchScope();
        Map<String, BigDecimal> net = new HashMap<>();
        BigDecimal debits = BigDecimal.ZERO;
        for (ManualJournalRequest.Line line : request.lines()) {
            UUID lineBranch = line.branchId() != null ? line.branchId() : request.branchId();
            if (!scope.permits(lineBranch)) {
                throw new ResourceNotFoundException("Branch");
            }
            GlAccountRef gl = chartOfAccounts.requirePostable(line.chartOfAccountId());
            if (!gl.manualPostingAllowed()) {
                throw new BankingException(LedgerErrorCode.GL_ACCOUNT_NOT_MANUAL,
                        "GL account " + gl.code() + " does not accept manual journals.");
            }
            currencies.requireValidAmount(line.amount(), line.currency());
            boolean debit = EntryDirection.valueOf(line.direction()) == EntryDirection.DEBIT;
            net.merge(line.currency(), debit ? line.amount() : line.amount().negate(), BigDecimal::add);
            if (debit) {
                debits = debits.add(line.amount());
            }
        }
        if (net.values().stream().anyMatch(total -> total.signum() != 0)) {
            throw new BankingException(LedgerErrorCode.UNBALANCED_JOURNAL);
        }
        if (request.valueDate() != null && request.valueDate().isAfter(businessDates.today())) {
            throw new BankingException(LedgerErrorCode.INVALID_POSTING, "The value date cannot be in the future.");
        }
        boolean singleCurrency = net.size() == 1;
        String currency = singleCurrency ? net.keySet().iterator().next() : null;
        return approvalService.submit(new ApprovalSubmission(ApprovalType.MANUAL_JOURNAL, request.branchId(),
                singleCurrency ? debits : null, currency, "JOURNAL", null,
                "Manual journal: " + request.description().trim(), request));
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.MANUAL_JOURNAL;
    }

    /**
     * Posts the approved journal; the engine re-validates everything against the ledger as it is now.
     */
    @Override
    public UUID execute(ApprovedAction action) {
        ManualJournalRequest request = jsonMapper.readValue(action.payloadJson(), ManualJournalRequest.class);
        PostedJournal journal = postingEngine.postApproved(new PostingRequest(JournalSource.MANUAL,
                References.next("MJ", businessDates.today()), null, request.branchId(), request.valueDate(),
                request.description(), null, request.lines().stream()
                .map(line -> PostingLine.toGl(line.chartOfAccountId(),
                        line.branchId() != null ? line.branchId() : request.branchId(), line.currency(),
                        EntryDirection.valueOf(line.direction()), line.amount(), line.narration()))
                .toList()), action.requestedBy());
        return journal.id();
    }
}
