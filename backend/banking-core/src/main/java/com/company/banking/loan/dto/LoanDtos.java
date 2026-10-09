package com.company.banking.loan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class LoanDtos {

    private static final String METHODS = "^(FLAT|DECLINING_BALANCE_EQUAL_INSTALLMENT|DECLINING_BALANCE_EQUAL_PRINCIPAL)$";
    private static final String COMPONENT = "(PENALTY|FEE|INTEREST|PRINCIPAL)";

    private LoanDtos() {
    }

    // ------------------------------------------------------------------------------------------------- products

    /**
     * The terms of a loan product version. GL accounts default to the institution's system accounts.
     *
     * @param annualRate          percent a year, e.g. 30 for 30%
     * @param processingFeeRate   percent of the principal taken at disbursement
     * @param processingFeeFlat   added to the rate-based fee
     * @param penaltyRate         percent a year charged on overdue installments after {@code penaltyGraceDays}
     * @param collateralCoverage  verified forced-sale value needed, as a percent of the approved amount
     * @param secondApprovalAbove amounts above this need a second approver
     * @param allocationOrder     how a repayment settles an installment, e.g. {@code PENALTY,FEE,INTEREST,PRINCIPAL}
     */
    public record Terms(
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal minAmount,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal maxAmount,
            @NotNull @Min(1) @Max(520) Integer minInstallments,
            @NotNull @Min(1) @Max(520) Integer maxInstallments,
            @NotBlank @Pattern(regexp = METHODS) String interestMethod,
            @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 6) BigDecimal annualRate,
            @NotBlank @Pattern(regexp = "^(ACTUAL_365F|ACTUAL_360|THIRTY_360)$") String dayCount,
            @NotBlank @Pattern(regexp = "^(DAILY|WEEKLY|BIWEEKLY|MONTHLY|QUARTERLY)$") String repaymentFrequency,
            @Min(0) @Max(519) Integer principalGrace,
            @Min(0) @Max(519) Integer interestGrace,
            @Pattern(regexp = "^(HALF_EVEN|HALF_UP)$") String roundingMode,
            @Pattern(regexp = "^" + COMPONENT + "(," + COMPONENT + "){3}$") String allocationOrder,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 6) BigDecimal processingFeeRate,
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal processingFeeFlat,
            @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 6) BigDecimal penaltyRate,
            @Min(0) @Max(365) Integer penaltyGraceDays,
            @Min(0) @Max(10) Integer requiredGuarantors,
            @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 4) BigDecimal collateralCoverage,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal secondApprovalAbove,
            @Size(max = 20) String requiredKycTier,
            UUID principalGlId,
            UUID interestReceivableGlId,
            UUID interestIncomeGlId,
            UUID feeIncomeGlId,
            UUID penaltyReceivableGlId,
            UUID penaltyIncomeGlId) {
    }

    public record NewProduct(
            @NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{1,29}$") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @NotNull @Valid Terms terms) {
    }

    public record UpdateProduct(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @NotBlank @Pattern(regexp = "^(ACTIVE|INACTIVE)$") String status,
            @NotNull Long version) {
    }

    public record ProductVersion(UUID id, int versionNo, String status, String currency, BigDecimal minAmount,
                                 BigDecimal maxAmount, int minInstallments, int maxInstallments,
                                 String interestMethod, BigDecimal annualRate, String dayCount,
                                 String repaymentFrequency, int principalGrace, int interestGrace,
                                 String roundingMode, String allocationOrder, BigDecimal processingFeeRate,
                                 BigDecimal processingFeeFlat, BigDecimal penaltyRate, int penaltyGraceDays,
                                 int requiredGuarantors, BigDecimal collateralCoverage,
                                 BigDecimal secondApprovalAbove, String requiredKycTier, UUID principalGlId,
                                 UUID interestReceivableGlId, UUID interestIncomeGlId, UUID feeIncomeGlId,
                                 UUID penaltyReceivableGlId, UUID penaltyIncomeGlId, Instant createdAt,
                                 Instant publishedAt) {
    }

    /**
     * @param currentVersion the published version (new applications use it); null until one is published
     */
    public record Product(UUID id, String code, String name, String description, String status,
                          ProductVersion currentVersion, List<ProductVersion> versions, Long version) {
    }

    // --------------------------------------------------------------------------------------------- applications

    public record NewApplication(
            @NotNull UUID customerId,
            @NotNull UUID productId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal requestedAmount,
            @NotNull @Min(1) @Max(520) Integer requestedInstallments,
            @NotBlank @Size(max = 300) String purpose,
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal monthlyIncome,
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal monthlyExpenses,
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal existingDebt,
            @NotNull UUID disbursementAccountId) {
    }

    public record EditApplication(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal requestedAmount,
            @NotNull @Min(1) @Max(520) Integer requestedInstallments,
            @NotBlank @Size(max = 300) String purpose,
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal monthlyIncome,
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal monthlyExpenses,
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal existingDebt,
            @NotNull UUID disbursementAccountId,
            @NotNull Long version) {
    }

    public record Assess(
            @NotBlank @Pattern(regexp = "^(LOW|MEDIUM|HIGH)$") String riskRating,
            @NotBlank @Size(max = 1000) String note,
            @NotNull Long version) {
    }

    /**
     * A workflow step without terms (submit, recommend, second approval, withdraw).
     */
    public record Step(@Size(max = 1000) String note, @NotNull Long version) {
    }

    public record Reject(@NotBlank @Size(max = 1000) String note, @NotNull Long version) {
    }

    /**
     * Approves the loan on these terms (at most what was requested).
     *
     * @param firstDueDate null for one repayment period after disbursement
     */
    public record Approve(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal approvedAmount,
            @NotNull @Min(1) @Max(520) Integer approvedInstallments,
            LocalDate firstDueDate,
            @Size(max = 1000) String note,
            @NotNull Long version) {
    }

    public record NewGuarantor(
            UUID customerId,
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 30) String phone,
            @NotBlank @Size(max = 60) String relationship,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal guaranteedAmount) {
    }

    public record NewCollateral(
            @NotBlank @Pattern(regexp = "^(LAND|BUILDING|VEHICLE|EQUIPMENT|INVENTORY|SAVINGS|HOUSEHOLD|OTHER)$")
            String category,
            @NotBlank @Size(max = 300) String description,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal estimatedValue,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal forcedSaleValue,
            @NotNull LocalDate valuationDate) {
    }

    /**
     * @param secondApprovalRequired the amount is above the product's second-approval threshold
     * @param loanId                 the loan once disbursed
     */
    public record Application(UUID id, String applicationNumber, UUID customerId, String customerName,
                              UUID branchId, UUID productId, String productCode, String productName,
                              UUID productVersionId, String currency, BigDecimal requestedAmount,
                              int requestedInstallments, String purpose, BigDecimal monthlyIncome,
                              BigDecimal monthlyExpenses, BigDecimal existingDebt, UUID disbursementAccountId,
                              String status, String riskRating, String assessmentNote, BigDecimal approvedAmount,
                              Integer approvedInstallments, LocalDate firstDueDate, UUID loanOfficerId,
                              String loanOfficerName, boolean secondApprovalRequired, UUID loanId, Instant createdAt,
                              Instant updatedAt, Long version) {
    }

    public record WorkflowStep(String type, UUID actorId, String actorName, Instant occurredAt, String note) {
    }

    public record Guarantor(UUID id, UUID customerId, String fullName, String phone, String relationship,
                            BigDecimal guaranteedAmount, UUID verifiedBy, Instant verifiedAt) {
    }

    public record Collateral(UUID id, String category, String description, BigDecimal estimatedValue,
                             BigDecimal forcedSaleValue, LocalDate valuationDate, String status, UUID verifiedBy,
                             Instant verifiedAt, Instant releasedAt) {
    }

    /**
     * Where the application stands against the product's security requirements.
     *
     * @param collateralNeeded verified forced-sale value the product needs for the amount applied for or approved
     */
    public record Security(int guarantorsRequired, int guarantorsVerified, BigDecimal collateralNeeded,
                           BigDecimal collateralVerified) {
    }

    public record ApplicationDetail(Application application, List<WorkflowStep> steps, List<Guarantor> guarantors,
                                    List<Collateral> collateral, Security security) {
    }

    // ------------------------------------------------------------------------------------------------ schedules

    public record ScheduleLine(int number, LocalDate fromDate, LocalDate dueDate, BigDecimal principal,
                               BigDecimal interest, BigDecimal total, BigDecimal outstandingAfter) {
    }

    /**
     * What a loan on these terms would look like if disbursed today. Computed by the server from the product.
     *
     * @param netDisbursed what reaches the borrower's account (principal less the processing fee)
     */
    public record SchedulePreview(BigDecimal principal, BigDecimal processingFee, BigDecimal netDisbursed,
                                  BigDecimal totalInterest, BigDecimal totalRepayable, BigDecimal installmentAmount,
                                  LocalDate disbursementDate, LocalDate maturityDate, List<ScheduleLine> lines) {
    }

    // ----------------------------------------------------------------------------------------------------- loans

    /**
     * @param firstDueDate overrides the approved first due date (which may have passed since approval)
     */
    public record Disburse(LocalDate firstDueDate, @Size(max = 200) String narration) {
    }

    /**
     * @param source ACCOUNT debits the borrower's repayment account; CASH takes cash into the caller's till
     */
    public record Repay(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal amount,
            @NotBlank @Pattern(regexp = "^(ACCOUNT|CASH)$") String source,
            @Size(max = 200) String narration,
            @Size(max = 100) String externalReference) {
    }

    /**
     * @param status UPCOMING, DUE (today), OVERDUE, PAID, or PARTLY_PAID (not yet due, something paid)
     */
    public record Installment(int number, LocalDate fromDate, LocalDate dueDate, BigDecimal principalDue,
                              BigDecimal interestDue, BigDecimal penaltyDue, BigDecimal principalPaid,
                              BigDecimal interestPaid, BigDecimal penaltyPaid, BigDecimal interestWaived,
                              BigDecimal outstanding, LocalDate paidOn, String status) {
    }

    /**
     * Balances come from the loan's ledger accounts; arrears and the next installment from its schedule.
     *
     * @param interestReceivable interest earned and not yet paid
     * @param arrears            what is overdue
     */
    public record Loan(UUID id, String loanNumber, UUID applicationId, UUID customerId, String customerName,
                       UUID branchId, UUID productVersionId, String productCode, String productName,
                       UUID repaymentAccountId, String currency, BigDecimal principal, String interestMethod,
                       BigDecimal annualRate, String dayCount, String repaymentFrequency, int installments,
                       BigDecimal processingFee, LocalDate disbursementDate, LocalDate firstDueDate,
                       LocalDate maturityDate, String status, int daysPastDue, String delinquencyBand,
                       boolean nonAccrual, BigDecimal principalOutstanding, BigDecimal interestReceivable,
                       BigDecimal penaltyReceivable, BigDecimal arrears, LocalDate nextDueDate,
                       BigDecimal nextDueAmount, LocalDate closedOn, Long version) {
    }

    public record Repayment(UUID id, UUID transactionId, String source, BigDecimal amount, BigDecimal penalty,
                            BigDecimal fee, BigDecimal interest, BigDecimal principal, LocalDate businessDate,
                            UUID receivedBy, Instant createdAt) {
    }

    /**
     * What settles the loan today: everything due, the interest earned so far and all principal; the interest not
     * yet earned is waived.
     */
    public record Payoff(LocalDate asOf, BigDecimal principal, BigDecimal interest, BigDecimal penalty,
                         BigDecimal total, BigDecimal interestWaived) {
    }

    public record LoanDetail(Loan loan, List<Installment> schedule, List<Repayment> repayments, Payoff payoff) {
    }

    /**
     * @param settled the repayment paid the loan off and closed it
     */
    public record RepaymentReceipt(Repayment repayment, Loan loan, boolean settled) {
    }
}
