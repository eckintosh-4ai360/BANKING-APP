package com.company.banking.loan.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.loan.schedule.RepaymentAllocator.Allocation;
import com.company.banking.loan.schedule.RepaymentAllocator.Component;
import com.company.banking.loan.schedule.RepaymentAllocator.Owed;
import com.company.banking.loan.schedule.RepaymentAllocator.Result;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepaymentAllocatorTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 5, 10);
    private static final List<Component> PENALTY_FIRST = RepaymentAllocator.parseOrder(
            "PENALTY,FEE,INTEREST,PRINCIPAL");

    /** Two installments overdue (one with a penalty), one running, one in the future. */
    private static final List<Owed> OWED = List.of(
            owed(1, LocalDate.of(2027, 3, 31), "100.00", "30.00", "5.00"),
            owed(2, LocalDate.of(2027, 4, 30), "100.00", "25.00", "0.00"),
            owed(3, LocalDate.of(2027, 5, 31), "100.00", "20.00", "0.00"),
            owed(4, LocalDate.of(2027, 6, 30), "100.00", "15.00", "0.00"));

    private static Owed owed(int number, LocalDate due, String principal, String interest, String penalty) {
        return new Owed(number, due, new BigDecimal(principal), new BigDecimal(interest), new BigDecimal(penalty));
    }

    private static Allocation of(Result result, int number) {
        return result.allocations().get(number - 1);
    }

    @Test
    void settlesTheOldestInstallmentFirstInTheProductsOrder() {
        Result result = RepaymentAllocator.allocate(OWED, TODAY, new BigDecimal("150.00"), PENALTY_FIRST);

        assertThat(of(result, 1).penalty()).isEqualByComparingTo("5.00");
        assertThat(of(result, 1).interest()).isEqualByComparingTo("30.00");
        assertThat(of(result, 1).principal()).isEqualByComparingTo("100.00");
        assertThat(of(result, 2).interest()).isEqualByComparingTo("15.00");
        assertThat(of(result, 2).principal()).isEqualByComparingTo("0.00");
        assertThat(result.total()).isEqualByComparingTo("150.00");
        assertThat(result.unallocated()).isEqualByComparingTo("0.00");
    }

    @Test
    void followsAnInterestBeforePenaltyOrder() {
        Result result = RepaymentAllocator.allocate(OWED, TODAY, new BigDecimal("32.00"),
                RepaymentAllocator.parseOrder("INTEREST,PRINCIPAL,PENALTY,FEE"));

        assertThat(of(result, 1).interest()).isEqualByComparingTo("30.00");
        assertThat(of(result, 1).principal()).isEqualByComparingTo("2.00");
        assertThat(of(result, 1).penalty()).isEqualByComparingTo("0.00");
    }

    @Test
    void prepaysFuturePrincipalButNeverInterestNotYetDue() {
        // Overdue: 135 + 125 = 260. The remaining 140 pays installment 3's principal and 40 of installment 4's.
        Result result = RepaymentAllocator.allocate(OWED, TODAY, new BigDecimal("400.00"), PENALTY_FIRST);

        assertThat(of(result, 3).principal()).isEqualByComparingTo("100.00");
        assertThat(of(result, 3).interest()).isEqualByComparingTo("0.00");
        assertThat(of(result, 4).principal()).isEqualByComparingTo("40.00");
        assertThat(of(result, 4).interest()).isEqualByComparingTo("0.00");
        assertThat(result.principal()).isEqualByComparingTo("340.00");
        assertThat(result.interest()).isEqualByComparingTo("55.00");
        assertThat(result.penalty()).isEqualByComparingTo("5.00");
    }

    @Test
    void reportsWhatCouldNotBePlaced() {
        Result result = RepaymentAllocator.allocate(OWED, TODAY, new BigDecimal("1000.00"), PENALTY_FIRST);

        // Everything that can be paid now: penalty 5 + interest 55 + all principal 400.
        assertThat(result.total()).isEqualByComparingTo("460.00");
        assertThat(result.unallocated()).isEqualByComparingTo("540.00");
    }

    @Test
    void settlementCollectsEarnedInterestAndWaivesTheRest() {
        // Earned by today: installments 1 and 2 in full (30 + 25, nothing paid yet) plus 10 days of installment 3.
        Result result = RepaymentAllocator.settle(OWED, List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO), TODAY, new BigDecimal("61.45"));

        assertThat(of(result, 3).interest()).isEqualByComparingTo("6.45");
        assertThat(of(result, 3).interestWaived()).isEqualByComparingTo("13.55");
        assertThat(of(result, 4).interest()).isEqualByComparingTo("0.00");
        assertThat(of(result, 4).interestWaived()).isEqualByComparingTo("15.00");
        assertThat(result.principal()).isEqualByComparingTo("400.00");
        assertThat(result.interest()).isEqualByComparingTo("61.45");
        assertThat(result.penalty()).isEqualByComparingTo("5.00");
        assertThat(result.total()).isEqualByComparingTo("466.45");
        assertThat(result.interestWaived()).isEqualByComparingTo("28.55");
    }

    @Test
    void settlementCountsInterestAlreadyPaid() {
        // Installment 1 had 10 of its 40 interest paid earlier: it still owes 30, and earned includes the 10.
        Result result = RepaymentAllocator.settle(OWED, List.of(new BigDecimal("10.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO), TODAY, new BigDecimal("71.45"));

        assertThat(of(result, 1).interest()).isEqualByComparingTo("30.00");
        assertThat(of(result, 3).interest()).isEqualByComparingTo("6.45");
        assertThat(result.interest()).isEqualByComparingTo("61.45");
    }

    @Test
    void anAllocationOrderNamesEachComponentOnce() {
        assertThatThrownBy(() -> RepaymentAllocator.parseOrder("PENALTY,INTEREST,PRINCIPAL"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RepaymentAllocator.parseOrder("PENALTY,INTEREST,INTEREST,PRINCIPAL"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RepaymentAllocator.allocate(OWED, TODAY, BigDecimal.ZERO, PENALTY_FIRST))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
