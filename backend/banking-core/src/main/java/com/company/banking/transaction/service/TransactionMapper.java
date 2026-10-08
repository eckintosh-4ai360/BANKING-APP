package com.company.banking.transaction.service;

import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.transaction.dto.TransactionResponse;
import com.company.banking.transaction.entity.FinancialTransaction;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TransactionMapper {

    private final CurrencyService currencies;

    /**
     * @param accountNumbers numbers of the accounts involved, by account id
     */
    TransactionResponse toResponse(FinancialTransaction transaction, Map<UUID, String> accountNumbers) {
        String currency = transaction.getCurrency();
        return new TransactionResponse(transaction.getId(), transaction.getReference(),
                transaction.getTransactionType().name(), transaction.getStatus().name(),
                transaction.getChannel().name(), currency, currencies.present(transaction.getAmount(), currency),
                currencies.present(transaction.getFeeAmount(), currency), transaction.getDebitAccountId(),
                accountNumbers.get(transaction.getDebitAccountId()), transaction.getCreditAccountId(),
                accountNumbers.get(transaction.getCreditAccountId()), transaction.getBranchId(),
                transaction.getJournalEntryId(), transaction.getBusinessDate(), transaction.getValueDate(),
                transaction.getNarration(), transaction.getExternalReference(), transaction.getInitiatedBy(),
                transaction.getCreatedAt(), transaction.getReversedAt(), transaction.getReversalReason());
    }
}
