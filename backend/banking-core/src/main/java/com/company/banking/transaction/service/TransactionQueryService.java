package com.company.banking.transaction.service;

import com.company.banking.account.service.AccountService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.transaction.dto.TransactionResponse;
import com.company.banking.transaction.entity.FinancialTransaction;
import com.company.banking.transaction.repository.FinancialTransactionRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading transactions. A transaction is visible to whoever may see one of its accounts.
 */
@Service
@RequiredArgsConstructor
public class TransactionQueryService {

    private static final int DEFAULT_RANGE_DAYS = 90;
    private static final int MAX_RANGE_DAYS = 366;

    private final FinancialTransactionRepository transactions;
    private final AccountService accountService;
    private final BusinessDateService businessDates;
    private final TransactionMapper mapper;

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID transactionId) {
        FinancialTransaction transaction = transactions.findByTenantIdAndId(TenantContext.requireTenantId(),
                        transactionId)
                .filter(this::visible)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction"));
        return mapper.toResponse(transaction, numbers(transaction));
    }

    /**
     * Transactions of an account, newest first, between two business dates (the last 90 days by default; at most
     * a year per request).
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> ofAccount(UUID accountId, LocalDate from, LocalDate to,
                                                       PageRequest page) {
        accountService.requireReadable(accountId);
        LocalDate end = to != null ? to : businessDates.today();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_RANGE_DAYS);
        if (start.isAfter(end) || start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new BankingException(CommonErrorCode.VALIDATION_FAILED,
                    "The date range must run forwards and cover at most a year.");
        }
        Page<FinancialTransaction> found = transactions.findByAccount(TenantContext.requireTenantId(), accountId,
                start, end, PageRequest.of(page.getPageNumber(), page.getPageSize(),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        Map<UUID, String> numbers = accountService.accountNumbers(found.getContent().stream()
                .flatMap(transaction -> Stream.of(transaction.getDebitAccountId(), transaction.getCreditAccountId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return PageResponse.from(found, transaction -> mapper.toResponse(transaction, numbers));
    }

    private boolean visible(FinancialTransaction transaction) {
        return Stream.of(transaction.getDebitAccountId(), transaction.getCreditAccountId())
                .filter(Objects::nonNull)
                .anyMatch(accountService::isReadable);
    }

    private Map<UUID, String> numbers(FinancialTransaction transaction) {
        return accountService.accountNumbers(Stream.of(transaction.getDebitAccountId(),
                        transaction.getCreditAccountId())
                .filter(Objects::nonNull)
                .toList());
    }
}
