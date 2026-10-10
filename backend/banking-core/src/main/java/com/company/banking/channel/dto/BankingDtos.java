package com.company.banking.channel.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The signed-in customer's banking in the app: accounts, transfers and beneficiaries. Amounts are decimal strings
 * computed by the server.
 */
public final class BankingDtos {

    private static final String PIN = "^[0-9]{4,6}$";

    private BankingDtos() {
    }

    public record Account(UUID id, String accountNumber, String title, String productName, String productType,
                          String currency, String status, BigDecimal ledgerBalance, BigDecimal availableBalance,
                          LocalDate openedOn) {
    }

    /**
     * @param totals what is available across the accounts, per currency
     */
    public record Accounts(List<Account> accounts, List<Total> totals) {
    }

    public record Total(String currency, BigDecimal available) {
    }

    /**
     * Who an account number belongs to, partly hidden, so the customer can check before paying or saving it.
     */
    public record Destination(String accountNumber, String name, String currency) {
    }

    /**
     * A transfer to one of the customer's own accounts, a saved beneficiary or an account number of the institution,
     * confirmed with the transaction PIN. Retried with the same {@code Idempotency-Key}, it moves the money once.
     */
    public record Transfer(
            @NotNull UUID fromAccountId,
            UUID beneficiaryId,
            @Size(max = 34) String toAccountNumber,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @Size(max = 140) String narration,
            @NotBlank @Pattern(regexp = PIN) String pin) {

        @Override
        public String toString() {
            return "Transfer[from=" + fromAccountId + ", amount=" + amount + ", pin=***]";
        }
    }

    /**
     * @param availableAfter what the paying account has available afterwards
     */
    public record TransferReceipt(UUID transactionId, String reference, BigDecimal amount, BigDecimal fee,
                                  String currency, String fromAccountNumber, String toAccountNumber, String toName,
                                  BigDecimal availableAfter, LocalDate businessDate, Instant postedAt) {
    }

    /**
     * Repays one of the customer's loans from its repayment account, confirmed with the transaction PIN.
     */
    public record LoanRepayment(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
            @NotBlank @Pattern(regexp = PIN) String pin) {

        @Override
        public String toString() {
            return "LoanRepayment[amount=" + amount + ", pin=***]";
        }
    }

    public record NewBeneficiary(
            @NotBlank @Pattern(regexp = "^(INTERNAL|BANK|MOBILE_MONEY)$") String type,
            @NotBlank @Size(max = 34) String accountNumber,
            @NotBlank @Size(max = 60) String nickname,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal transferLimit,
            boolean favourite,
            @NotBlank @Pattern(regexp = PIN) String pin) {

        @Override
        public String toString() {
            return "NewBeneficiary[type=" + type + ", nickname=" + nickname + ", pin=***]";
        }
    }

    /**
     * Raising or removing the limit needs the PIN; renaming and favouriting do not.
     */
    public record BeneficiaryEdit(
            @NotBlank @Size(max = 60) String nickname,
            boolean favourite,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal transferLimit,
            @Pattern(regexp = PIN) String pin,
            @NotNull Long version) {

        @Override
        public String toString() {
            return "BeneficiaryEdit[nickname=" + nickname + ", pin=***]";
        }
    }

    /**
     * @param coolingDownUntil until then transfers to it are capped by the institution's cooldown limit (null once
     *                         over)
     */
    public record Beneficiary(UUID id, String type, String nickname, String accountNumber, String name,
                              boolean favourite, BigDecimal transferLimit, Instant coolingDownUntil,
                              Instant createdAt, Long version) {
    }
}
